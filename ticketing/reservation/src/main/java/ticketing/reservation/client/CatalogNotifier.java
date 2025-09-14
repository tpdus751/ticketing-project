package ticketing.reservation.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class CatalogNotifier {

    // 단순 HTTP 호출을 위해 RestTemplate 사용
    private final RestTemplate restTemplate;

    public void notifySeatChange(Long eventId, Long seatId, String status, int version, String traceId) {
        // Catalog 모듈의 SSE 컨트롤러 publishSeatChange를 직접 호출 불가
        // 대신 Catalog에 알림용 REST API(/internal/seat-update)를 만들어 두고 호출
        String url = "http://localhost:8080/ticketing/api/internal/seat-update";
        restTemplate.postForObject(url, new SeatUpdate(eventId, seatId, status, version, traceId), Void.class);
    }

    record SeatUpdate(Long eventId, Long seatId, String status, int version, String traceId) {};

}
