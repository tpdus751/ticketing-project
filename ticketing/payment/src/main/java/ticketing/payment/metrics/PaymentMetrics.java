package ticketing.payment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {
    private final Counter paymentSuccess;
    private final Counter paymentFail;

    public PaymentMetrics(MeterRegistry reg) {
        paymentSuccess = Counter.builder("payment_success_total").register(reg);
        paymentFail = Counter.builder("payment_fail_total").register(reg);
    }

    public void incSuccess() { paymentSuccess.increment(); }
    public void incFaile() { paymentFail.increment(); }
}
