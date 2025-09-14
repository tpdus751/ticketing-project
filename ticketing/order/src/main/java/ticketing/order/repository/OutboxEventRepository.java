package ticketing.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ticketing.order.entity.OutboxEvent;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    // 아직 전송 안 된 이벤트만 조회
    List<OutboxEvent> findByStatus(String status);
}
