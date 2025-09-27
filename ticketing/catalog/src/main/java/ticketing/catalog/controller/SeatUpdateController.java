package ticketing.catalog.controller;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/ticketing/api/internal") // 내부 호출 전용
@RequiredArgsConstructor
@Slf4j
public class SeatUpdateController {

    private final EventStreamController eventStreamController;

    @PostMapping("/seat-update")
    public void seatUpdate(@RequestBody SeatUpdateRequest req) {
        // 🔹 SSE publish를 비동기 태스크로 넘겨버림
        CompletableFuture.runAsync(() -> {
            try {
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
        });
        // 🔹 HTTP 응답은 즉시 반환
    }

    record SeatUpdateRequest(Long eventId, Long seatId, String status, int version, String traceId) {};

}
