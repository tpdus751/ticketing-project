package ticketing.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ticketing.order.dto.OrderRequest;
import ticketing.order.dto.OrderResponse;
import ticketing.order.entity.Order;
import ticketing.order.entity.OutboxEvent;
import ticketing.order.metrics.OrderMetrics;
import ticketing.order.repository.OrderRepository;
import ticketing.order.repository.OutboxEventRepository;

import java.awt.print.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepo;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper om;
    private final OrderMetrics metrics; // ✅ 메트릭 주입

    @Observed(name = "order.create")
    @Transactional
    public OrderResponse createOrder(OrderRequest req, String idempotencyKey) {
        return metrics.recordLatency(() -> { // ✅ 전체 Latency 측정
            try {
                Optional<Order> existing = orderRepo.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    log.warn("[ORDER-CREATE-CONFLICT] idempotencyKey={} traceId={}",
                            idempotencyKey, org.slf4j.MDC.get("traceId"));
                    return toResponse(existing.get());
                }

                String traceId = org.slf4j.MDC.get("traceId");

                Order order = new Order();
                order.setEventId(req.eventId());
                order.setSeatIdList(req.seatIds());
                order.setStatus("CREATED");
                order.setIdempotencyKey(idempotencyKey);
                order.setTraceId(traceId);

                orderRepo.saveAndFlush(order);

                metrics.incCreated(); // ✅ 생성 카운터 증가

                log.info("[ORDER-CREATE] orderId={} eventId={} seatIds={} traceId={}",
                        order.getId(), order.getEventId(), order.getSeatIdList(), traceId);
                return toResponse(order);
            } catch (DataIntegrityViolationException e) {
                return orderRepo.findByIdempotencyKey(idempotencyKey)
                        .map(o -> {
                            log.warn("[ORDER-CREATE-DUPLICATE] idempotencyKey={} traceId={}",
                                    idempotencyKey, org.slf4j.MDC.get("traceId"));
                            return toResponse(o);
                        })
                        .orElseThrow(() -> e);
            }
        });
    }

    // 내부 DTO (직렬화용)
    record OrderCreatedPayload(Long orderId, Long eventId, List<Long> seatIds) {}

    @Transactional(readOnly = true)
    public Optional<OrderResponse> getOrderById(Long id) {
        return orderRepo.findById(id).map(this::toResponse);
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getEventId(),
                order.getSeatIdList(),
                order.getCreatedAt()
        );
    }
}
