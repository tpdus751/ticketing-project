// src/main/java/ticketing/reservation/service/ReservationService.java
package ticketing.reservation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ticketing.common.ApiException;
import ticketing.common.Errors;
import ticketing.reservation.client.CatalogNotifier;
import ticketing.reservation.metrics.ReservationMetrics;
import ticketing.reservation.repository.SeatRepository;
import io.micrometer.observation.Observation;
import io.micrometer.common.KeyValue;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReservationService {

    private final StringRedisTemplate redis;
    private final ObjectMapper om;
    private final SeatRepository seatRepository;
    private final ReservationMetrics metrics;
    private final ObservationRegistry obs;
    private final CatalogNotifier catalogNotifier;

    public ReservationService(StringRedisTemplate redis,
                              ObjectMapper om,
                              SeatRepository seatRepository,
                              ReservationMetrics metrics,
                              ObservationRegistry obs,
                              CatalogNotifier catalogNotifier) {
        this.redis = redis;
        this.om = om;
        this.seatRepository = seatRepository;
        this.metrics = metrics;
        this.obs = obs;
        this.catalogNotifier = catalogNotifier;
    }

    private String holdKey(long eventId, long seatId) {
        return "seat:hold:%d:%d".formatted(eventId, seatId);
    }

    private String soldKey(long eventId, long seatId) {
        return "seat:sold:%d:%d".formatted(eventId, seatId);
    }

    // ✅ 서버 기동 시 SOLD 좌석 Preload
    @PostConstruct
    public void preloadSoldSeats() {
        redis.delete(redis.keys("seat:sold:*"));
        List<Object[]> soldSeats = seatRepository.findSoldSeats();
        for (Object[] row : soldSeats) {
            Long eventId = ((Number) row[0]).longValue();
            Long seatId = ((Number) row[1]).longValue();
            redis.opsForValue().set(soldKey(eventId, seatId), "true");
        }
        log.info("[PRELOAD] {} SOLD seats preloaded into Redis", soldSeats.size());
    }

    private static final String LUA = """
        local key = KEYS[1]
        local ttl = tonumber(ARGV[1])
        local val = ARGV[2]
        if redis.call('EXISTS', key) == 1 then return 0 end
        redis.call('SET', key, val, 'EX', ttl)
        return ttl
    """;

    private static final String EXTEND_LUA = """
        local key = KEYS[1]
        local add = tonumber(ARGV[1])
        if redis.call('EXISTS', key) == 0 then return -1 end
        local remain = redis.call('TTL', key)
        if remain < 0 then remain = 0 end
        local newttl = remain + add
        redis.call('EXPIRE', key, newttl)
        return newttl
    """;

    private static final String RELEASE_LUA = """
        local key = KEYS[1]
        if redis.call('EXISTS', key) == 0 then
          return -1
        end
        redis.call('DEL', key)
        return 1
    """;

    public String extendHold(long eventId, long seatId, int seconds, String callerId) {
        String k = holdKey(eventId, seatId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(EXTEND_LUA, Long.class);
        Long newTtl = redis.execute(script, List.of(k), String.valueOf(seconds));

        if (newTtl == null || newTtl < 0L) {
            log.warn("[RESERVATION-EXTEND-FAILED] eventId={} seatId={} callerId={} traceId=? reason=not_found_or_expired",
                    eventId, seatId, callerId);
            throw holdExpired();
        }

        metrics.incExtendSuccess();
        catalogNotifier.notifySeatChange(eventId, seatId, "HELD", 1, "x");

        String expiresAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(newTtl));
        log.info("[RESERVATION-EXTEND] eventId={} seatId={} newTtl={}s expiresAt={} callerId={}",
                eventId, seatId, newTtl, expiresAt, callerId);
        return expiresAt;
    }

    public void releaseHold(long eventId, long seatId, String callerId) {
        String k = holdKey(eventId, seatId);
        Long r = redis.execute(new DefaultRedisScript<>(RELEASE_LUA, Long.class), List.of(k));
        if (r == null || r == -1L) {
            log.warn("[RESERVATION-RELEASE-FAILED] eventId={} seatId={} callerId={} traceId=? reason=not_found_or_expired",
                    eventId, seatId, callerId);
            throw holdExpired();
        }
        metrics.incReleaseSuccess();
        catalogNotifier.notifySeatChange(eventId, seatId, "AVAILABLE", 1, "x");
        log.info("[RESERVATION-RELEASE] eventId={} seatId={} callerId={} traceId=?",
                eventId, seatId, callerId);
    }

    private ResponseStatusException holdExpired() {
        return problem(HttpStatus.GONE, "HOLD_EXPIRED", "Hold already expired or not found");
    }

    private ResponseStatusException problem(HttpStatus status, String code, String msg) {
        return new ResponseStatusException(status, msg);
    }

    public record HoldResult(boolean success, Instant expiresAt) {
    }

    @Observed(name = "reservation.hold")
    public HoldResult holdSeat(long eventId, long seatId, int holdSeconds, String traceId) {
        return metrics.recordHold(() -> {
            // ✅ Hold 정보 JSON 직렬화
            String value;
            try {
                value = om.writeValueAsString(Map.of(
                        "eventId", eventId,
                        "seatId", seatId,
                        "traceId", traceId,
                        "heldAt", Instant.now().toString(),
                        "ttlSec", holdSeconds
                ));
            } catch (JsonProcessingException e) {
                log.error("[RESERVATION-HOLD-ERROR] eventId={} seatId={} traceId={} error={}",
                        eventId, seatId, traceId, e.getMessage());
                throw new RuntimeException(e);
            }

            // ✅ Redis LuaScript (is-sold + setnx 통합)
            String HOLD_LUA = """
            -- KEYS[1]=soldKey, KEYS[2]=holdKey
            -- ARGV[1]=ttl, ARGV[2]=value
            if redis.call('EXISTS', KEYS[1]) == 1 then
              return -1   -- 이미 SOLD
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
              return 0    -- 이미 HELD
            end
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[1])
            return 1       -- 성공
        """;

            Long ret = Observation
                    .createNotStarted("reservation.redis.hold", obs)
                    .lowCardinalityKeyValue(KeyValue.of("event.id", String.valueOf(eventId)))
                    .lowCardinalityKeyValue(KeyValue.of("seat.id", String.valueOf(seatId)))
                    .observe(() -> redis.execute(
                            new DefaultRedisScript<>(HOLD_LUA, Long.class),
                            List.of(soldKey(eventId, seatId), holdKey(eventId, seatId)),
                            String.valueOf(holdSeconds), value
                    ));

            // ✅ 결과 처리
            if (ret == null || ret == 0L) {
                metrics.incHoldConflict();
                log.warn("[RESERVATION-HOLD-CONFLICT] eventId={} seatId={} traceId={} reason=already_held",
                        eventId, seatId, traceId);
                return new HoldResult(false, null);
            } else if (ret == -1L) {
                metrics.incHoldConflict();
                log.warn("[RESERVATION-HOLD-CONFLICT] eventId={} seatId={} traceId={} reason=already_sold",
                        eventId, seatId, traceId);
                throw new ApiException(Errors.VALIDATION_FAILED, "Seat already sold");
            }

            // ✅ Hold 성공 처리
            metrics.incHoldSuccess();
            catalogNotifier.notifySeatChange(eventId, seatId, "HELD", 1, traceId);

            Instant expiresAt = Instant.now().plusSeconds(holdSeconds);
            log.info("[RESERVATION-HOLD] eventId={} seatId={} holdSeconds={} expiresAt={} traceId={}",
                    eventId, seatId, holdSeconds, expiresAt, traceId);

            return new HoldResult(true, expiresAt);
        });
    }


    public boolean isHeld(long eventId, long seatId) {
        return Boolean.TRUE.equals(redis.hasKey(holdKey(eventId, seatId)));
    }

    @Observed(name = "reservation.confirm")
    @Transactional
    public void markSeatSold(long eventId, long seatId, String traceId) {
        metrics.recordConfirm(() -> {
            // ✅ Redis LuaScript (holdKey 삭제 + soldKey 세팅 통합)
            String CONFIRM_LUA = """
            -- KEYS[1]=holdKey, KEYS[2]=soldKey
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], 'true')
            return 1
        """;

            // ✅ DB 업데이트 (status = SOLD)
            int rows = seatRepository.markSold(eventId, seatId);
            if (rows == 0) {
                log.error("[RESERVATION-CONFIRM-FAILED] eventId={} seatId={} traceId={} reason=invalid_ids",
                        eventId, seatId, traceId);
                throw new ApiException(Errors.VALIDATION_FAILED, "Invalid eventId/seatId");
            }

            // ✅ Redis 원자적 갱신 실행
            Observation
                    .createNotStarted("reservation.redis.confirm", obs)
                    .lowCardinalityKeyValue(KeyValue.of("event.id", String.valueOf(eventId)))
                    .lowCardinalityKeyValue(KeyValue.of("seat.id", String.valueOf(seatId)))
                    .observe(() -> redis.execute(
                            new DefaultRedisScript<>(CONFIRM_LUA, Long.class),
                            List.of(holdKey(eventId, seatId), soldKey(eventId, seatId))
                    ));

            // ✅ Metrics + SSE 반영
            metrics.incConfirmSuccess();
            catalogNotifier.notifySeatChange(eventId, seatId, "SOLD", 1, traceId);

            log.info("[RESERVATION-CONFIRM] eventId={} seatId={} traceId={}", eventId, seatId, traceId);
            return null;
        });
    }
}
