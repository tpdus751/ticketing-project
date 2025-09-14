package ticketing.catalog.dto;

import java.time.LocalDateTime;

public record EventSummary(Long id, String title, LocalDateTime dateTime, String description) {}