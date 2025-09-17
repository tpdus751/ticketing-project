package ticketing.catalog.controller;

import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
@Slf4j
public class EventController {
    private final EventRepository events;
    private final SeatQueryService seatQueryService;

    // GET /api/events
    @Observed(name = "catalog.events.list")
    @GetMapping("/events")
    public List<EventSummary> list() {
        String traceId = MDC.get("traceId");
        log.info("[EVENT-LIST] traceId={}", traceId);
        return events.findAll().stream()
                .map(e -> new EventSummary(e.getId(), e.getTitle(), e.getDateTime(), e.getDescription()))
                .toList();
    }

    // GET /api/events/{id}
    @Observed(name = "catalog.events.get")
    @GetMapping("/events/{id}")
    public ResponseEntity<EventSummary> get(@PathVariable Long id) {
        String traceId = MDC.get("traceId");
        log.info("[EVENT-GET] id={} traceId={}", id, traceId);
        return events.findById(id)
                .map(e -> {
                    log.debug("[EVENT-GET] 조회 성공 id={} traceId={}", id, traceId);
                    return ResponseEntity.ok(new EventSummary(
                            e.getId(), e.getTitle(), e.getDateTime(), e.getDescription()));
                })
                .orElseGet(() -> {
                    log.warn("[EVENT-GET] 조회 실패 - Not Found id={} traceId={}", id, traceId);
                    return ResponseEntity.notFound().build();
                });
    }

    // GET /api/events/{id}/seats
    @Observed(name = "catalog.events.seats")
    @GetMapping("/events/{id}/seats")
    public ResponseEntity<SeatMap> seatMap(@PathVariable Long id) {
        String traceId = MDC.get("traceId");
        log.info("[EVENT-SEATS] eventId={} traceId={}", id, traceId);
        if (!events.existsById(id)) {
            log.warn("[EVENT-SEATS] 좌석맵 조회 실패 - 이벤트 없음 eventId={} traceId={}", id, traceId);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(seatQueryService.getSeats(id));
    }
}