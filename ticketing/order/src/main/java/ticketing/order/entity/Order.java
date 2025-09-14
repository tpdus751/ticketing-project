package ticketing.order.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"idempotency_key"})} // 중복 방지
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "seat_ids", columnDefinition = "json", nullable = false)
    private String seatIds;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    // ✅ traceId 추가
    @Column(name = "trace_id", length = 64, nullable = true)
    private String traceId;

    @Column(name = "created_at", updatable = false, nullable = false)
    @org.hibernate.annotations.CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @org.hibernate.annotations.UpdateTimestamp
    private LocalDateTime updatedAt;

    // ✅ JSON 변환 유틸리티
    private static final ObjectMapper mapper = new ObjectMapper();

    public List<Long> getSeatIdList() {
        try {
            return mapper.readValue(seatIds, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public void setSeatIdList(List<Long> seats) {
        try {
            this.seatIds = mapper.writeValueAsString(seats); // "[272,273]" 형태 저장
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
