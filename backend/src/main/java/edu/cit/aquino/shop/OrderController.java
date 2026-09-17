package edu.cit.aquino.shop;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:5173")
class OrderController {
    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    ResponseEntity<OrderResult> placeOrder(@RequestBody OrderRequest request) {
        if (request == null || request.items() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(orderService.placeOrder(request.items()));
    }

    @GetMapping
    ResponseEntity<List<OrderResponse>> getOrders() {
        return ResponseEntity.ok(orderService.getOrders());
    }

    @PostMapping("/{orderId}/cancel")
    ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId));
    }
}
