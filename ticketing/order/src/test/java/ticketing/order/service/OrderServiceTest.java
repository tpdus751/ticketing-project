package ticketing.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.testcontainers.shaded.org.checkerframework.checker.units.qual.A;
import ticketing.order.dto.OrderRequest;
import ticketing.order.dto.OrderResponse;
import ticketing.order.entity.Order;
import ticketing.order.entity.OutboxEvent;
import ticketing.order.repository.OrderRepository;
import ticketing.order.repository.OutboxEventRepository;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

public class OrderServiceTest {

    private final OrderRepository orderRepo = mock(OrderRepository.class);
    private final OutboxEventRepository outboxRepo = mock(OutboxEventRepository.class);
    private final ObjectMapper om = new ObjectMapper();

    private final OrderService sut = new OrderService(orderRepo, outboxRepo, om);

    @Test
    public void 주문_생성시_Order와_Outbox가_저장된다() {
        //given
        OrderRequest req = new OrderRequest(1L, List.of(101L, 102L));
        String idempotencyKey = "abc-123";

        // when
        OrderResponse resp = sut.createOrder(req, idempotencyKey);

        // then
        ArgumentCaptor<Order> orderCap = ArgumentCaptor.forClass(Order.class);
        verify(orderRepo).save(orderCap.capture());
        assertThat(orderCap.getValue().getIdempotencyKey()).isEqualTo(idempotencyKey);

        ArgumentCaptor<OutboxEvent> eventCap = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(eventCap.capture());
        assertThat(eventCap.getValue().getEventType()).isEqualTo("ORDER_CREATED");

        assertThat(resp.status()).isEqualTo("CREATED");
    }



}
