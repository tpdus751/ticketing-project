package ticketing.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ticketing.reservation.entity.Seat;

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

    // ✅ EXISTS 기반 최적화
    @Query(value = """
      SELECT EXISTS(
        SELECT 1
          FROM seats
         WHERE id = :seatId
           AND event_id = :eventId
           AND status = 'SOLD'
      )
      """, nativeQuery = true)
    int isSold(@Param("eventId") long eventId, @Param("seatId") long seatId);

    // ✅ SOLD 좌석 전체 조회 (eventId, seatId 쌍)
    @Query(value = """
            SELECT event_id, id
                FROM seats
                WHERE status = 'SOLD'
            """, nativeQuery = true)
    List<Long[]> findSoldSeats();
}