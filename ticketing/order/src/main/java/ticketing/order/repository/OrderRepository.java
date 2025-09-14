package ticketing.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ticketing.order.entity.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatus(String created);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
