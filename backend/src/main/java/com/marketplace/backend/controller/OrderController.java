package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.CheckoutRequest;
import com.marketplace.backend.dto.response.CheckoutQuote;
import com.marketplace.backend.dto.response.OrderResponse;
import com.marketplace.backend.service.CheckoutService;
import com.marketplace.backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CheckoutService checkoutService;

    // live totals for the checkout page; never creates anything
    @PostMapping("/preview")
    public ResponseEntity<CheckoutQuote> preview(@Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(checkoutService.preview(request));
    }

    // creates one order per shop; returns { checkoutCode, paymentRequired, amount, orders }
    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> checkout(@Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(checkoutService.placeOrders(request));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> myOrders() {
        return ResponseEntity.ok(orderService.myOrders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getMyOrder(id));
    }

    // body (optional): { "reason": "Muon thay doi dia chi giao hang" }
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        orderService.cancelOrder(id, body != null ? body.get("reason") : null);
        return ResponseEntity.noContent().build();
    }

    // "Da nhan duoc hang"
    @PostMapping("/{id}/received")
    public ResponseEntity<OrderResponse> received(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.confirmReceived(id));
    }

    // "Mua lai": { cartItemIds: [...], skipped: ["product name", ...] }
    @PostMapping("/{id}/rebuy")
    public ResponseEntity<Map<String, Object>> rebuy(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.rebuy(id));
    }
}
