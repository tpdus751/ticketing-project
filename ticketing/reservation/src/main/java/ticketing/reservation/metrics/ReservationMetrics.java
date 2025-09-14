package ticketing.reservation.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ReservationMetrics {
    private final Counter holdSuccess;
    private final Counter holdConflict;
    private final Counter confirmSuccess;
    private final Counter confirmFailed;
    private final Counter extendSuccess;
    private final Counter releaseSuccess;
    private final Timer   holdLatency;
    private final Timer   confirmLatency;

    public ReservationMetrics(MeterRegistry reg) {
        holdSuccess     = Counter.builder("reservation_hold_success_total").register(reg);
        holdConflict    = Counter.builder("reservation_hold_conflict_total").register(reg);
        confirmSuccess  = Counter.builder("reservation_confirm_success_total").register(reg);
        confirmFailed   = Counter.builder("reservation_confirm_failed_total").register(reg);
        extendSuccess   = Counter.builder("reservation_extend_success_total").register(reg);
        releaseSuccess  = Counter.builder("reservation_release_success_total").register(reg);

        holdLatency = Timer.builder("reservation_hold_latency_ms_seconds")
                .description("Hold latency (seconds)")
                .publishPercentileHistogram(true)      // 👈 히스토그램
                .register(reg);

        confirmLatency = Timer.builder("reservation_confirm_latency_ms_seconds")
                .description("Confirm latency (seconds)")
                .publishPercentileHistogram(true)
                .register(reg);
    }

    // 카운터
    public void incHoldSuccess()    { holdSuccess.increment(); }
    public void incHoldConflict()   { holdConflict.increment(); }
    public void incConfirmSuccess() { confirmSuccess.increment(); }
    public void incConfirmFailed()  { confirmFailed.increment(); }
    public void incExtendSuccess()  { extendSuccess.increment(); }
    public void incReleaseSuccess() { releaseSuccess.increment(); }

    // 타이머 사용 헬퍼
    public <T> T recordHold(java.util.concurrent.Callable<T> c) {
        try { return holdLatency.recordCallable(c); } catch (Exception e) { throw new RuntimeException(e); }
    }
    public <T> T recordConfirm(java.util.concurrent.Callable<T> c) {
        try { return confirmLatency.recordCallable(c); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
