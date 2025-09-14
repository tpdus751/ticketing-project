package ticketing.payment.controller;

import io.micrometer.observation.annotation.Observed;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ticketing.common.TraceIdFilter;
import ticketing.payment.metrics.PaymentMetrics;

import java.util.Random;

@RestController
@RequestMapping("/ticketing/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final Random random = new Random();
    private final PaymentMetrics metrics;

    @Observed(name = "payment.authorize") // 👈 Jaeger에 span 남김
    @PostMapping("/authorize")
    public ResponseEntity<PaymentResponse> authorize(@RequestBody PaymentRequest req,
                                                     HttpServletRequest http) {

        String traceId = (String) http.getAttribute(TraceIdFilter.HEADER);

        // 1. 랜덤 지연 (500 ~ 1500ms 사이) -> 실제 PG 네트워크 지연 흉내
        try {
            Thread.sleep(500 + random.nextInt(1000));
        } catch (InterruptedException ignored) {}

        // 2. 80% 성공, 20% 실패 시뮬레이션
        boolean success = random.nextDouble() < 0.8;

        if (success) {
            metrics.incSuccess(); // Prometheus에 성공 카운트 증가
            log.info("[PAYMENT] orderId={} status=success traceId={}", req.orderId(), traceId);
            return ResponseEntity.ok(new PaymentResponse(req.orderId(), "success", traceId));
        } else {
            metrics.incFaile(); // Prometheus에 실패 카운트 증가
            log.warn("[PAYMENT] orderId={} status=fail traceId={}", req.orderId(), traceId);
            return ResponseEntity.ok(new PaymentResponse(req.orderId(), "fail", traceId));
        }
    }

    // 요청 DTO
    record PaymentRequest(Long orderId) {}

    // 응답 DTO
    record PaymentResponse(Long orderId, String status, String traceId) {}

}
