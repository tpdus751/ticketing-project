package ticketing.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ticketing.catalog.entity.Event;

public interface EventRepository extends JpaRepository<Event, Long> {}