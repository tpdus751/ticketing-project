package ticketing.order.worker;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ticketing.order.entity.OutboxEvent;
import ticketing.order.repository.OutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxWorker {

    private final OutboxEventRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObservationRegistry obs; // ✅ Jaeger 연결을 위한 Observation Registry

    // 5초마다 실행
    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepo.findByStatus("PENDING");

        for (OutboxEvent event : pending) {
            Observation.createNotStarted("order.outbox.publish", obs)
                    .lowCardinalityKeyValue("event.type", event.getEventType())
                    .observe(() -> {
                        try {
                            // DB에 저장된 traceId를 traceparent로 사용
                            Map<String, String> headersMap = new HashMap<>();
                            if (event.getTraceId() != null) {
                                headersMap.put("traceparent", event.getTraceId());
                            } else {
                                // 현재 trace context 가져오기
                                Context context = Context.current().with(Span.current());
                                W3CTraceContextPropagator.getInstance().inject(context, headersMap, Map::put);
                            }

                            // kafka record 생성
                            ProducerRecord<String, String> record = new ProducerRecord<>("order.events", event.getPayload());

                            // traceparent 헤더 추가
                            headersMap.forEach((k, v) -> record.headers().add(k, v.getBytes(StandardCharsets.UTF_8)));

                            // kafka에 발행
                            kafkaTemplate.send(record);
                            log.info("[OUTBOX] Published eventId={} type={} trace={}", event.getId(), event.getEventType(), headersMap.get("traceparent"));

                            // 상태 갱신
                            event.setStatus("SENT");
                            outboxRepo.save(event);
                        } catch(Exception e){
                            log.error("[OUTBOX] Failed to publish eventId={} error={}", event.getId(), e.getMessage());
                            event.setStatus("FAILED");
                            outboxRepo.save(event);
                        }
                    });
        }
    }

}
