package ticketing.reservation.dto;

public record SeatDto(Long id, int r, int c, int price, String status) {}