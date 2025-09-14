package ticketing.payment.handler;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ticketing.common.ApiException;
import ticketing.common.ErrorResponse;
import ticketing.common.Errors;
import ticketing.common.TraceIdFilter;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 예외 (ApiException) 처리
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

    // 모든 예외 (fallback)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> onEtc(Exception ex, HttpServletRequest req) {
        String traceId = (String) req.getAttribute(TraceIdFilter.HEADER);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", ex.getMessage(), traceId));
    }
}
