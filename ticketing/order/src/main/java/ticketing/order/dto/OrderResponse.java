package ticketing.order.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

// 주문 응답 DTO
public record OrderResponse(Long orderId, String status, Long eventId, List<Long> seatIds, LocalDateTime createdAt) {

}
