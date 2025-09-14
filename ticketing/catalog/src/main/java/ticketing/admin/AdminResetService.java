// src/main/java/ticketing/api/admin/AdminResetService.java
package ticketing.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminResetService {

    private final JdbcTemplate jdbc;                // 빠른 일괄 업데이트용
    private final StringRedisTemplate redis;        // Redis 키 삭제

    /**
     * 이벤트 단위 리셋:
     *  - seats.status → 'AVAILABLE'
     *  - Redis hold 키( seat:hold:{eventId}:* ) 삭제
     *  - 선택 캐시 키( seats:map:{eventId} ) 삭제
     */
    @Transactional
    public void resetEvent(Long eventId) {
        // 1) DB: 해당 이벤트 좌석 모두 AVAILABLE로
        //    (스키마: seats(event_id, status))
        jdbc.update("UPDATE seats SET status = 'AVAILABLE' WHERE event_id = ?", eventId);

        // 2) Redis: hold / 캐시 키 삭제
        deleteByScan("seat:hold:" + eventId + ":*");
        redis.delete("seats:map:" + eventId); // 좌석맵 캐시를 쓰는 경우
    }

    /** KEYS 대신 SCAN으로 안전하게 패턴 삭제 */
    private void deleteByScan(String pattern) throws DataAccessException {
        var conn = redis.getConnectionFactory().getConnection();
        var scan = ScanOptions.scanOptions().match(pattern).count(1000).build();
        try (var cursor = conn.scan(scan)) {
            while (cursor.hasNext()) {
                byte[] key = cursor.next();
                conn.keyCommands().del(key);
            }
        }
    }
}
