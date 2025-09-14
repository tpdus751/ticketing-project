package ticketing.reservation.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ticketing.reservation.service.SeatQueryService;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(SeatQueryService.EventNotFoundException.class)
    public ResponseEntity<Map<String,Object>> handleNotFound(RuntimeException ex) {
        String traceId = org.slf4j.MDC.get("traceId");
        if (traceId == null) traceId = java.util.UUID.randomUUID().toString();
        return ResponseEntity.status(404).body(Map.of(
                "code","EVENT_NOT_FOUND",
                "message","Event not found: " + ex.getMessage(),
                "traceId", traceId
        ));
    }
}
