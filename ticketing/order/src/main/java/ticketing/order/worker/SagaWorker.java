package ticketing.order.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import ticketing.common.TraceIdFilter;
import ticketing.order.entity.Order;
import ticketing.order.entity.OutboxEvent;
import ticketing.order.metrics.OrderMetrics;
import ticketing.order.repository.OrderRepository;
import ticketing.order.repository.OutboxEventRepository;

import java.net.http.HttpRequest;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaWorker {

    private final OrderRepository orderRepo;
    private final OutboxEventRepository outboxRepo;
    private final RestTemplate restTemplate;
    private final ObservationRegistry obs; // ✅ Jaeger 연결을 위한 Observation Registry
    private final OrderMetrics metrics; // ✅ 메트릭 주입

    // 5초마다 실행
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processCreatedOrders() {
        // 1. 상태가 CREATED인 주문들 조회
        List<Order> createdOrders = orderRepo.findByStatus("CREATED");

        for (Order order : createdOrders) {
            // DB에서 traceId 가져오기 (없으면 새로 생성)
            String traceId = order.getTraceId();

            // ✅ Jaeger에 span 기록
            Observation.createNotStarted("order.saga", obs)
                    .lowCardinalityKeyValue("order.id", String.valueOf(order.getId()))
                    .lowCardinalityKeyValue("trace.id", traceId)
                    .observe(() -> {
                        try {
                            log.info("[SAGA] Processing orderId={} traceId={}", order.getId(), traceId);

                            PaymentRequest reqBody = new PaymentRequest(order.getId());
                            HttpHeaders headers = new HttpHeaders();
                            headers.set(TraceIdFilter.HEADER, traceId);

                            HttpEntity<PaymentRequest> entity = new HttpEntity<>(reqBody, headers);

                            // 2. Payment 모듈 호출
                            ResponseEntity<PaymentResponse> resp = restTemplate.postForEntity(
                                    "http://payment:8083/ticketing/api/payments/authorize",
                                    entity,
                                    PaymentResponse.class
                            );

                            PaymentResponse paymentResp = resp.getBody();

                            // 3. 성공 여부에 따라 상태 변경 + 이벤트 발행
                            if (resp != null && "success".equals(paymentResp.status())) {
                                order.setStatus("CONFIRMED");
                                orderRepo.save(order);
                                metrics.incConfirmed(); // ✅ Confirmed 카운터
                                log.info("[SAGA] Order CONFIRMED orderId={} traceId={}", order.getId(), traceId);
                                publishOutbox(order, "PAYMENT_SUCCESS", traceId);
                            } else {
                                order.setStatus("CANCELLED");
                                orderRepo.save(order);
                                metrics.incCancelled(); // ✅ Cancelled 카운터
                                log.warn("[SAGA] Order CANCELLED orderId={} traceId={}", order.getId(), traceId);
                                publishOutbox(order, "PAYMENT_FAILED", traceId);
                            }
                        } catch (Exception e) {
                            log.error("[SAGA] Failed to process orderId={} error={} traceId={}",
                                    order.getId(), e.getMessage(), traceId);
                        }
                    });
        }
    }

    private void publishOutbox(Order order, String eventType, String traceId) {
        Observation.createNotStarted("order.outbox.insert", obs)
                .lowCardinalityKeyValue("order.id", String.valueOf(order.getId()))
                .lowCardinalityKeyValue("eventType", eventType)
                .lowCardinalityKeyValue("trace.id", traceId)
                .lowCardinalityKeyValue("status", "PENDING")
                .observe(() -> {
                    try {
                        String payload = new ObjectMapper().writeValueAsString(
                                new PaymentEvent(order.getId(), order.getEventId(), order.getSeatIdList(), eventType, traceId)
                        );

                        OutboxEvent event = OutboxEvent.builder()
                                .eventType(eventType)
                                .payload(payload)
                                .status("PENDING")
                                .traceId(traceId) // 👈 traceId 저장
                                .build();

                        outboxRepo.save(event); // 👈 이 DB INSERT가 Jaeger에 span 기록됨
                        log.info("[SAGA] Published {} for orderId={} traceId={}", eventType, order.getId(), traceId);

                    } catch (Exception e) {
                        log.error("[SAGA] Failed to publish outbox for orderId={} error={} traceId={}", order.getId(), e.getMessage(), traceId);
                    }
                });
    }

    // 내부 DTO
    record PaymentRequest(Long orderId) {}
    record PaymentResponse(Long orderId, String status) {}
    record PaymentEvent(Long orderId, Long eventId, List<Long> seatIds, String eventType, String traceId) {}
}
