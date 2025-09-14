package ticketing.catalog.dto;

import java.util.List;

public record SeatMap(int rows, int cols, List<SeatDto> seats) {}