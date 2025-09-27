package ticketing.reservation.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogNotifier {

    // 단순 HTTP 호출을 위해 RestTemplate 사용
    private final RestTemplate restTemplate;

    // 🔹 비동기 실행 (별도 쓰레드풀)
    @Async("notifierExecutor")
    public void notifySeatChange(Long eventId, Long seatId, String status, int version, String traceId) {
        try {
            // TraceContext 분리 → 원래 요청 trace와 독립
            String url = "http://catalog:8080/ticketing/api/internal/seat-update";
            restTemplate.postForObject(
                    url,
                    new SeatUpdate(eventId, seatId, status, version, traceId),
                    Void.class
            );
            log.info("[NOTIFY] eventId={} seatId={} status={} traceId={}", eventId, seatId, status, traceId);
        } catch (Exception e) {
            log.error("[NOTIFY-FAILED] eventId={} seatId={} status={} traceId={} error={}",
                    eventId, seatId, status, traceId, e.getMessage());
        }
    }

    record SeatUpdate(Long eventId, Long seatId, String status, int version, String traceId) {};

}
