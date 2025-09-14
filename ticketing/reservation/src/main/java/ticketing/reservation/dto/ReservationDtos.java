package ticketing.reservation.dto;

import java.time.Instant;

public class ReservationDtos {
    public record CreateReservationRequest(long eventId, long seatId, int holdSeconds) { }
    public record CreateReservationResponse(long eventId, long seatId, int holdSeconds, Instant expiresAt, String traceId) { }
}
