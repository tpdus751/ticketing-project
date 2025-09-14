package ticketing.catalog.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ticketing.common.ApiException;
import ticketing.common.ErrorResponse;
import ticketing.common.Errors;
import ticketing.common.TraceIdFilter;


@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> onApi(ApiException ex, HttpServletRequest req) {
        String traceId = (String) req.getAttribute(TraceIdFilter.HEADER);
        HttpStatus status =
                Errors.RESERVATION_CONFLICT.equals(ex.code()) ? HttpStatus.CONFLICT :
                        Errors.RESERVATION_EXPIRED.equals(ex.code())  ? HttpStatus.GONE :
                                Errors.VALIDATION_FAILED.equals(ex.code())    ? HttpStatus.UNPROCESSABLE_ENTITY :
                                        HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status)
                .body(new ErrorResponse(ex.code(), ex.getMessage(), traceId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> onValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String traceId = (String) req.getAttribute(TraceIdFilter.HEADER);
        return ResponseEntity.unprocessableEntity()
                .body(new ErrorResponse(Errors.VALIDATION_FAILED, ex.getMessage(), traceId));
    }

    @ExceptionHandler(Exception.class)
    public void onEtc(Exception e, HttpServletRequest req) throws Exception {
        String ct = req.getHeader("Accept");
        if (ct != null && ct.contains("text/event-stream")) {
            log.warn("[SSE-ERROR] {}", e.getMessage());
            // SSE 응답에는 ErrorResponse 전송하지 않음
            return;
        }
        throw e; // 일반 요청은 그대로 처리
    }
}
