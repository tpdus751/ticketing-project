package ticketing.reservation.handler;

import jakarta.servlet.http.HttpServletRequest;
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
    public ResponseEntity<ErrorResponse> onEtc(Exception ex, HttpServletRequest req) {
        String traceId = (String) req.getAttribute(TraceIdFilter.HEADER);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", ex.getMessage(), traceId));
    }
}
