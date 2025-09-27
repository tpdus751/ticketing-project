package ticketing.catalog.controller;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ticketing/api/internal") // 내부 호출 전용
@RequiredArgsConstructor
@Slf4j
public class SeatUpdateController {

    private final EventStreamController eventStreamController;

    @Observed(name = "catalog.seat-update")
    @PostMapping("/seat-update")
    public void seatUpdate(@RequestBody SeatUpdateRequest req) {
        try {
            // SSE 이벤트 발행 -> 구독자들에게 push
            eventStreamController.publishSeatChange(
                    req.eventId(),
                    req.seatId(),
                    req.status(),
                    req.version(),
                    req.traceId()
            );
        } catch (Exception e) {
            log.error("[SEAT-UPDATE] SSE push failed, but ignoring. error={}", e.getMessage());
        }
    }

    record SeatUpdateRequest(Long eventId, Long seatId, String status, int version, String traceId) {};

}
