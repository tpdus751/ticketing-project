package ticketing.catalog.controller;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ticketing.catalog.entity.Event;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

@RestController
@RequestMapping("/ticketing/api/events")
@RequiredArgsConstructor
@Slf4j // ✅ 롬복 로그 어노테이션
public class EventStreamController {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByEvent = new ConcurrentHashMap<>();
    private final Map<Long, ConcurrentLinkedDeque<ServerEvent>> historyByEvent = new ConcurrentHashMap<>();

    private static final int HISTORY_LIMIT = 1000;
    private final ObservationRegistry obs;

    // SSE 전송을 비동기 처리하기 위해 ExecutorService 사용
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    // 클라이언트가 SSE 연결을 맺을 때 호출되는 엔드포인트
    @GetMapping(value = "/{eventId}/seats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSeats(
            @PathVariable Long eventId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        log.info("[SSE-CONNECT] eventId={} lastEventId={}", eventId, lastEventId);
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        // 해당 이벤트(eventId)의 emitter 리스트에 추가
        emittersByEvent.computeIfAbsent(eventId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // 연결 종료되면 emitter 제거
        emitter.onCompletion(() -> {
            emittersByEvent.getOrDefault(eventId, new CopyOnWriteArrayList<>()).remove(emitter);
            log.info("[SSE-DISCONNECT] eventId={} emitter removed", eventId);
        });

        // 타임아웃 시 emitter 제거
        emitter.onTimeout(() -> {
            emittersByEvent.getOrDefault(eventId, new CopyOnWriteArrayList<>()).remove(emitter);
            log.warn("[SSE-TIMEOUT] eventId={} emitter removed", eventId);
        });
        try {
            emitter.send(
                    SseEmitter.event()
                            .id("init-" + System.currentTimeMillis()) // 이벤트 고유 ID
                            .name("INIT")
                            .data("connected to event " + eventId)
            );

            // 클라이언트가 Last-Event-ID를 보냈으면, 히스토리에서 놓친 이벤트 재전송
            if (lastEventId != null) {
                try {
                    long lastId = Long.parseLong(lastEventId);
                    var history = historyByEvent.getOrDefault(eventId, new ConcurrentLinkedDeque<>());
                    history.stream()
                            .filter(ev -> ev.id > lastId)
                            .forEach(ev -> safeSend(emitter, ev, "x"));
                    log.info("[SSE-RESEND] eventId={} resent events after lastEventId={}", eventId, lastId);
                } catch (NumberFormatException e) {
                    log.warn("[SSE-RESEND] invalid lastEventId={} ignored", lastEventId);
                }
            }
        } catch (IOException e) {
            // 전송 실패 시
            log.error("[SSE-INIT-ERROR] eventId={} error={}", eventId, e.getMessage());
            emittersByEvent.getOrDefault(eventId, new CopyOnWriteArrayList<>()).remove(emitter);
        }

        return emitter;
    }

    // 서버에서 좌석 상태 변경을 알릴 때 호출하는 메서드
    public void publishSeatChange(Long eventId, Long seatId, String status, int version, String traceId) {
        Observation.createNotStarted("catalog.sse.publish", obs)
                .lowCardinalityKeyValue("eventId", eventId.toString())
                .lowCardinalityKeyValue("seatId", seatId.toString())
                .lowCardinalityKeyValue("status", status)
                .lowCardinalityKeyValue("traceId", traceId)
                .observe(() -> {
                    long id = System.currentTimeMillis();
                    ServerEvent ev = new ServerEvent(id, "SEAT_UPDATE", new SeatUpdate(seatId, status, version));

                    // 히스토리에 저장
                    var history = historyByEvent.computeIfAbsent(eventId, k -> new ConcurrentLinkedDeque<>());
                    history.addLast(ev);
                    if (history.size() > HISTORY_LIMIT) {
                        history.pollFirst(); // 오래된 건 버림
                    }

                    var emitters = emittersByEvent.getOrDefault(eventId, new CopyOnWriteArrayList<>());
                    log.info("[SSE-PUBLISH] eventId={} seatId={} status={} version={} emitters={} traceId={}",
                            eventId, seatId, status, version, emitters.size(), traceId);
                    // 현재 연결된 모든 구독자에게 이벤트 전송
                    for (SseEmitter emitter : emitters) {
                        safeSend(emitter, ev, traceId);
                    }
                });
    }

    private void safeSend(SseEmitter emitter, ServerEvent ev, String traceId) {
        // 새로운 작업을 스레드 풀에 제출 (즉시 리턴 -> 호출부는 블로킹 안됨)
        try {
            emitter.send(
                    SseEmitter.event()
                            .id(String.valueOf(ev.id))
                            .name(ev.event)
                            .data(ev.data)
            );
        } catch (IOException e) {
            log.warn("[SSE-SEND-ERROR] emitter already closed, removing. error={} traceId={}", e.getMessage(), traceId);
            // 전송 실패
            emitter.completeWithError(e); // <- complete() 대신 completeWithError 로 마무리
            emittersByEvent.values().forEach(list -> list.remove(emitter));
        }
    }

    record SeatUpdate(Long seatId, String status, int version) {}
    record ServerEvent(long id, String event, Object data) {}
}
