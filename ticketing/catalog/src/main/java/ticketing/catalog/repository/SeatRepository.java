package ticketing.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ticketing.catalog.entity.Seat;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByEventIdOrderByRowNoAscColNoAsc(Long eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
      UPDATE seats
         SET status = 'SOLD'
       WHERE id = :seatId
         AND event_id = :eventId
      """, nativeQuery = true)
    int markSold(@Param("eventId") long eventId, @Param("seatId") long seatId);

    // ✅ 해당 좌석이 SOLD인지 확인
    @Query(value = """
      SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
        FROM seats
       WHERE id = :seatId AND event_id = :eventId AND status = 'SOLD'
      """, nativeQuery = true)
    int isSold(@Param("eventId") long eventId, @Param("seatId") long seatId);
}