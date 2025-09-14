package ticketing.catalog.controller;

import io.micrometer.observation.annotation.Observed;
import ticketing.catalog.dto.EventSummary;
import ticketing.catalog.dto.SeatMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ticketing.catalog.repository.EventRepository;
import ticketing.catalog.service.SeatQueryService;

import java.util.List;

@RestController
@RequestMapping("/ticketing/api")
@RequiredArgsConstructor
public class EventController {
    private final EventRepository events;
    private final SeatQueryService seatQueryService;

    // GET /api/events
    @Observed(name = "catalog.events.list")
    @GetMapping("/events")
    public List<EventSummary> list() {
        return events.findAll().stream()
                .map(e -> new EventSummary(e.getId(), e.getTitle(), e.getDateTime(), e.getDescription()))
                .toList();
    }

    // GET /api/events/{id}
    @Observed(name = "catalog.events.get")
    @GetMapping("/events/{id}")
    public ResponseEntity<EventSummary> get(@PathVariable Long id) {
        return events.findById(id)
                .map(e -> ResponseEntity.ok(new EventSummary(e.getId(), e.getTitle(), e.getDateTime(), e.getDescription())))
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/events/{id}/seats
    @Observed(name = "catalog.events.seats")
    @GetMapping("/events/{id}/seats")
    public ResponseEntity<SeatMap> seatMap(@PathVariable Long id) {
        if (!events.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(seatQueryService.getSeats(id));
    }
}