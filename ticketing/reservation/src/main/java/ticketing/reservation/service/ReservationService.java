// src/main/java/ticketing/api/service/ReservationService.java
package ticketing.reservation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
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
import ticketing.reservation.metrics.ReservationMetrics;   // ✅ 메트릭 추가
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
    private final ReservationMetrics metrics;      // ✅ 메트릭 주입
    private final ObservationRegistry obs; // 주입
    private final CatalogNotifier catalogNotifier;

    public ReservationService(StringRedisTemplate redis,
                              ObjectMapper om,
                              SeatRepository seatRepository,
                              ReservationMetrics metrics,
                              ObservationRegistry obs,
                              CatalogNotifier catalogNotifier) { // ✅ 생성자에 추가
        this.redis = redis;
        this.om = om;
        this.seatRepository = seatRepository;
        this.metrics = metrics;
        this.obs = obs;
        this.catalogNotifier = catalogNotifier;
    }

    private String key(long eventId, long seatId) {
        return "seat:hold:%d:%d".formatted(eventId, seatId);
    }

    private static final String LUA =
            "local key = KEYS[1]\n" +
                    "local ttl = tonumber(ARGV[1])\n" +
                    "local val = ARGV[2]\n" +
                    "if redis.call('EXISTS', key) == 1 then return 0 end\n" +
                    "redis.call('SET', key, val, 'EX', ttl)\n" +
                    "return ttl\n";

    // ✔ EXTEND: 존재하면 TTL만 연장, 없으면 410
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


    // ✔ RELEASE: 존재하면 DEL, 없으면 410
    private static final String RELEASE_LUA = """
              local key = KEYS[1]
              if redis.call('EXISTS', key) == 0 then
                return -1
              end
              redis.call('DEL', key)
              return 1
            """;

    public String extendHold(long eventId, long seatId, int seconds, String callerId) {
        // 1) 항상 동일 키 규칙 사용
        String k = key(eventId, seatId);

        // 2) new TTL(초)을 반환하는 Lua 실행
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(EXTEND_LUA, Long.class);
        Long newTtl = redis.execute(script, List.of(k), String.valueOf(seconds));

        // 3) 예외 매핑
        if (newTtl == null || newTtl < 0L) {
            log.warn("[RESERVATION-EXTEND-FAILED] eventId={} seatId={} callerId={} traceId=? reason=not_found_or_expired",
                    eventId, seatId, callerId);
            throw holdExpired(); // 410
        }

        // 4) 메트릭 (선택)
        metrics.incExtendSuccess();

        // TTL 연장 성공 시에도 Catalog 알림 (좌석 상태 HELD 유지, 버전만 증가 가능)
        catalogNotifier.notifySeatChange(eventId, seatId, "HELD", 1, "x");

        // 5) 현재 시각 + newTtl(초)로 만료시각 계산해 반환
        String expiresAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(newTtl));
        log.info("[RESERVATION-EXTEND] eventId={} seatId={} newTtl={}s expiresAt={} callerId={}",
                eventId, seatId, newTtl, expiresAt, callerId); // ✅ 로그 추가
        return expiresAt;
    }

    public void releaseHold(long eventId, long seatId, String callerId) {
        String key = key(eventId, seatId);
        Long r = redis.execute(new DefaultRedisScript<>(RELEASE_LUA, Long.class), List.of(key));
        if (r == null || r == -1L) {
            log.warn("[RESERVATION-RELEASE-FAILED] eventId={} seatId={} callerId={} traceId=? reason=not_found_or_expired",
                    eventId, seatId, callerId);
            throw holdExpired();
        }
        metrics.incReleaseSuccess(); // ✅ 추가

        // 성공적으로 해제되면 Catalog에 알림 (좌석 상태 AVAILABLE)
        catalogNotifier.notifySeatChange(eventId, seatId, "AVAILABLE", 1, "x");

        log.info("[RESERVATION-RELEASE] eventId={} seatId={} callerId={} traceId=?",
                eventId, seatId, callerId); // ✅ 로그 추가
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
            boolean sold = Observation
                    .createNotStarted("db.isSold", obs)
                    .lowCardinalityKeyValue(KeyValue.of("event.id", String.valueOf(eventId)))
                    .lowCardinalityKeyValue(KeyValue.of("seat.id", String.valueOf(seatId)))
                    .observe(() -> seatRepository.isSold(eventId, seatId) > 0);

            if (sold) {
                metrics.incHoldConflict();
                log.warn("[RESERVATION-HOLD-CONFLICT] eventId={} seatId={} traceId={} reason=already_sold",
                        eventId, seatId, traceId); // ✅ 로그 추가
                throw new ApiException(Errors.VALIDATION_FAILED, "Seat already sold");
            }

            // ✅ JSON을 람다 밖에서 미리 생성 (체크 예외 처리)
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

            Long ret = Observation
                    .createNotStarted("redis.hold.setnx", obs)
                    .lowCardinalityKeyValue(KeyValue.of("event.id", String.valueOf(eventId)))
                    .lowCardinalityKeyValue(KeyValue.of("seat.id", String.valueOf(seatId)))
                    .observe(() -> redis.execute(
                            new DefaultRedisScript<>(LUA, Long.class),
                            List.of(key(eventId, seatId)),
                            String.valueOf(holdSeconds),  // ARGV[1]
                            value                          // ARGV[2]  ← 여기서 예외 없음
                    ));

            if (ret == null || ret == 0L) {
                metrics.incHoldConflict();
                log.warn("[RESERVATION-HOLD-CONFLICT] eventId={} seatId={} traceId={} reason=already_held",
                        eventId, seatId, traceId); // ✅ 로그 추가
                return new HoldResult(false, null);
            }
            metrics.incHoldSuccess();

            // 성공 시 Catalog에 알림 (좌석 상태 HELD)
            catalogNotifier.notifySeatChange(eventId, seatId, "HELD", 1, traceId);

            Instant expiresAt = Instant.now().plusSeconds(holdSeconds);
            log.info("[RESERVATION-HOLD] eventId={} seatId={} holdSeconds={} expiresAt={} traceId={}",
                    eventId, seatId, holdSeconds, expiresAt, traceId); // ✅ 로그 추가
            return new HoldResult(true, expiresAt);
        });
    }


    public boolean isHeld(long eventId, long seatId) {
        return Boolean.TRUE.equals(redis.hasKey(key(eventId, seatId)));
    }

    @Observed(name = "reservation.confirm")
    @Transactional
    public void markSeatSold(long eventId, long seatId, String traceId) {
        metrics.recordConfirm(() -> {
            redis.delete(key(eventId, seatId));

            int rows = seatRepository.markSold(eventId, seatId);
            if (rows == 0) {
                log.error("[RESERVATION-CONFIRM-FAILED] eventId={} seatId={} traceId={} reason=invalid_ids",
                        eventId, seatId, traceId); // ✅ 로그 추가
                throw new ApiException(Errors.VALIDATION_FAILED, "Invalid eventId/seatId");
            }

            metrics.incConfirmSuccess();
            catalogNotifier.notifySeatChange(eventId, seatId, "SOLD", 1, traceId);

            log.info("[RESERVATION-CONFIRM] eventId={} seatId={} traceId={}",
                    eventId, seatId, traceId); // ✅ 로그 추가
            return null;
        });
    }
}
