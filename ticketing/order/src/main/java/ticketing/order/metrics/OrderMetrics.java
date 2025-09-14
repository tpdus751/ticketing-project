package ticketing.order.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {
    private final Counter orderCreated;
    private final Counter orderConfirmed;
    private final Counter orderCancelled;
    private final Timer orderLatency;

    public OrderMetrics(MeterRegistry reg) {
        orderCreated = Counter.builder("order_request_total")
                .description("Total number of created orders")
                .register(reg);

        orderConfirmed = Counter.builder("order_confirmed_total")
                .description("Total number of confirmed orders")
                .register(reg);
        orderCancelled = Counter.builder("order_cancelled_total").register(reg);

        orderLatency = Timer.builder("order_latency_seconds")
                .publishPercentileHistogram(true)
                .register(reg);
    }

    public void incCreated() { orderCreated.increment(); }
    public void incConfirmed() { orderConfirmed.increment(); }
    public void incCancelled() { orderCancelled.increment(); }

    public <T> T recordLatency(java.util.concurrent.Callable<T> c) {
        try {
            return orderLatency.recordCallable(c);
        } catch (Exception e) {
            throw new RuntimeException(e); // 👈 checked → unchecked
        }
    }
}
