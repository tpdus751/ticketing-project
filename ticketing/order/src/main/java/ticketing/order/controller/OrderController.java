package ticketing.order.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ticketing.order.dto.OrderRequest;
import ticketing.order.dto.OrderResponse;
import ticketing.order.service.OrderService;

import java.awt.print.Pageable;

@RestController
@RequestMapping("/ticketing/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody OrderRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        // 주문 생성 * Outbox 저장
        OrderResponse resp = orderService.createOrder(request, idempotencyKey);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id, HttpServletRequest request) {
        return orderService.getOrderById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
