package ticketing.catalog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate; // ✅ 추가
import org.springframework.stereotype.Service;
import ticketing.catalog.entity.Seat;
import ticketing.catalog.dto.SeatDto;
import ticketing.catalog.dto.SeatMap;
import ticketing.catalog.repository.SeatRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatQueryService {
    private final SeatRepository seatRepository;
    private final StringRedisTemplate redis; // ✅ 주입

    public SeatMap getSeats(Long eventId) {
        List<Seat> entities = seatRepository.findByEventIdOrderByRowNoAscColNoAsc(eventId);
        if (entities.isEmpty()) throw new EventNotFoundException(eventId.toString());

        int rows = entities.stream().map(Seat::getRowNo).max(Comparator.naturalOrder()).orElse(0);
        int cols = entities.stream().map(Seat::getColNo).max(Comparator.naturalOrder()).orElse(0);

        // ✅ Redis에서 hold된 좌석 id 수집
        // 키 포맷: seat:hold:{eventId}:{seatId}
        Set<String> keys = Optional.ofNullable(redis.keys("seat:hold:%d:*".formatted(eventId)))
                .orElseGet(Set::of);

        Set<Long> heldIds = keys.stream()
                .map(k -> {
                    // seat:hold:EV:SEAT → 4 토큰
                    String[] p = k.split(":");
                    if (p.length >= 4) {
                        try { return Long.parseLong(p[3]); } catch (NumberFormatException ignore) {}
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<SeatDto> seats = entities.stream()
                .map(s -> {
                    String status = s.getStatus().name(); // AVAILABLE/SOLD
                    // SOLD가 아닌 좌석이 Redis에 잡혀있으면 HELD로 오버레이
                    if (!"SOLD".equals(status) && heldIds.contains(s.getId())) {
                        status = "HELD";
                    }
                    return new SeatDto(
                            s.getId(),
                            s.getRowNo(),
                            s.getColNo(),
                            s.getPrice(),
                            status
                    );
                })
                .toList();

        return new SeatMap(rows, cols, seats);
    }

    public static class EventNotFoundException extends RuntimeException {
        public EventNotFoundException(String eventId) { super(eventId); }
    }
}
