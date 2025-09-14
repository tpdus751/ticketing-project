package ticketing.catalog.dto;

public record SeatDto(Long id, int r, int c, int price, String status) {}