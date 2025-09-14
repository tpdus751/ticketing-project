package ticketing.reservation.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ticketing.common.ErrorResponse;
import ticketing.common.TraceIdFilter;
import ticketing.reservation.dto.ReservationDtos.CreateReservationRequest;
import ticketing.reservation.dto.ReservationDtos.CreateReservationResponse;
import ticketing.reservation.service.ReservationService;

import java.util.Map;

@RestController
@RequestMapping("/ticketing/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateReservationRequest req, HttpServletRequest http) {
        int seconds = Math.max(5, Math.min(req.holdSeconds(), 120)); // sanity clamp
        String traceId = (String) http.getAttribute(TraceIdFilter.HEADER);

        var result = reservationService.holdSeat(req.eventId(), req.seatId(), seconds, traceId);
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    new ErrorResponse("RESERVATION_CONFLICT", "Seat already held", traceId)
            );
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new CreateReservationResponse(req.eventId(), req.seatId(), seconds, result.expiresAt(), traceId)
        );
    }

    @PostMapping("/{eventId}/{seatId}/extend")
    public Map<String, Object> extend(@PathVariable long eventId,
                                      @PathVariable long seatId,
                                      @RequestBody(required = false) Map<String,Integer> body,
                                      @RequestHeader(value="X-Caller-Id", required=false) String callerId) {
        int seconds = body != null && body.get("seconds") != null ? body.get("seconds") : 30;
        return Map.of("expiresAt", reservationService.extendHold(eventId, seatId, seconds, callerId));
    }

    @DeleteMapping("/{eventId}/{seatId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable long eventId,
                        @PathVariable long seatId,
                        @RequestHeader(value="X-Caller-Id", required=false) String callerId) {
        reservationService.releaseHold(eventId, seatId, callerId);
    }
}
