package ticketing.reservation.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ticketing.reservation.service.ReservationService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final ReservationService reservationService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObservationRegistry obs;

    // Kafka 토픽 수신
    @KafkaListener(topics = "order.events", groupId = "reservation-service")
    public void consume(ConsumerRecord<String, String> record) {
        Headers headers = record.headers();

        // ✅ trace context 복원 (Kafka → OTel Context)
        Context parentCtx = W3CTraceContextPropagator.getInstance()
                .extract(Context.current(), headers, new TextMapGetter<Headers>() {
                    @Override
                    public Iterable<String> keys(Headers carrier) {
                        return () -> StreamSupport.stream(carrier.spliterator(), false)
                                .map(Header::key)
                                .iterator();
                    }

                    @Override
                    public String get(Headers carrier, String key) {
                        if (carrier == null) return null;
                        Header header = carrier.lastHeader(key);
                        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
                    }
                });

        // ✅ parentCtx를 활성화 → Observation과 연결
        try (io.opentelemetry.context.Scope scope = parentCtx.makeCurrent()) {
            Observation obsSpan = Observation.start("reservation.consume", this.obs)
                    .contextualName("consume " + record.topic());

            try (Observation.Scope ignored = obsSpan.openScope()) {
                String message = record.value();
                BaseEvent base = objectMapper.readValue(message, BaseEvent.class);

                switch (base.eventType()) {
                    case "PAYMENT_SUCCESS" -> {
                        PaymentSuccessEvent ev = objectMapper.readValue(message, PaymentSuccessEvent.class);
                        log.info("[CONSUME] Payment success orderId={} → mark SOLD", ev.orderId());
                        for (Long seatId : ev.seatIds()) {
                            reservationService.markSeatSold(ev.eventId(), seatId, ev.traceId());
                        }
                    }
                    case "PAYMENT_FAILED" -> {
                        PaymentFailedEvent ev = objectMapper.readValue(message, PaymentFailedEvent.class);
                        log.info("[CONSUME] Payment failed orderId={} → release seats", ev.orderId());
                        for (Long seatId : ev.seatIds()) {
                            reservationService.releaseHold(ev.eventId(), seatId, ev.traceId());
                        }
                    }
                    default -> log.warn("[CONSUME] Unknown eventType={}", base.eventType());
                }

                obsSpan.stop();
            } catch (Exception e) {
                obsSpan.error(e);
                obsSpan.stop();
                log.error("[CONSUME] Failed to parse/handle message={}", record, e);
                kafkaTemplate.send("order.events.DLQ", record.value());
            }
        } catch (Exception e) {
            log.error("[CONSUME] Unexpected error processing record={}", record, e);
            kafkaTemplate.send("order.events.DLQ", record.value());
        }
    }

    // 공통 Event DTO
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BaseEvent(String eventType) {}

    // 결제 성공 이벤트 DTO
    record PaymentSuccessEvent(Long orderId, Long eventId, List<Long> seatIds, String eventType, String traceId) {}

    // 결제 실패 이벤트 DTO
    record PaymentFailedEvent(Long orderId, Long eventId, List<Long> seatIds, String eventType, String traceId) {}
}
