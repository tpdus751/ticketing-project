package ticketing.order.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import ticketing.order.entity.OutboxEvent;
import ticketing.order.repository.OutboxEventRepository;
import ticketing.order.worker.OutboxWorker;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OutboxWorkerTest {

    private final OutboxEventRepository outboxRepo = mock(OutboxEventRepository.class);
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private final OutboxWorker worker = new OutboxWorker(outboxRepo, kafkaTemplate);

    @Test
    void Pending_이벤트를_SENT로_변경한다() {
        // given
        OutboxEvent event = OutboxEvent.builder()
                .eventType("ORDER_CREATED")
                .payload("{\"orderId\":1}")
                .status("PENDING")
                .build();

        when(outboxRepo.findByStatus("PENDING")).thenReturn(List.of(event));

        // when
        worker.publishPendingEvents();

        // then
        verify(kafkaTemplate).send("order.events", event.getPayload());
        ArgumentCaptor<OutboxEvent> cap = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("SENT");
    }
}
