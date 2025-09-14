package ticketing.order.dto;

import java.util.List;

public record OrderRequest(Long eventId, List<Long> seatIds) {
}
