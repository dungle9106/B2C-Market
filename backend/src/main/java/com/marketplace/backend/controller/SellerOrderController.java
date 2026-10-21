package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.OrderResponse;
import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seller/orders")
@RequiredArgsConstructor
public class SellerOrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<List<OrderResponse>> shopOrders() {
        return ResponseEntity.ok(orderService.shopOrders());
    }

    // order page with the tracking timeline
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> one(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.shopOrder(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        OrderStatus status;
        try {
            status = OrderStatus.valueOf(body.get("status"));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException("Trạng thái không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(orderService.updateStatus(id, status, body.get("reason")));
    }

    // "Chuan bi hang" body: { "pickupMethod": "PICKUP" | "DROPOFF", "pickupDate": "2026-10-03" }
    @PostMapping("/{id}/prepare")
    public ResponseEntity<OrderResponse> prepare(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        Map<String, String> b = body == null ? Map.of() : body;
        return ResponseEntity.ok(orderService.prepare(id, b.get("pickupMethod"), date(b.get("pickupDate"))));
    }

    // body: { "stage": "IN_TRANSIT" | "OUT_FOR_DELIVERY" | "DELIVERY_FAILED", "note": "...", "location": "..." }
    @PostMapping("/{id}/tracking")
    public ResponseEntity<OrderResponse> tracking(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(orderService.addShippingEvent(id, body.get("stage"), body.get("note"), body.get("location")));
    }

    @PostMapping("/{id}/cancel-request/accept")
    public ResponseEntity<OrderResponse> acceptCancel(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.acceptCancelRequest(id));
    }

    // body: { "reason": "Đơn hàng đã đóng gói xong" }
    @PostMapping("/{id}/cancel-request/reject")
    public ResponseEntity<OrderResponse> rejectCancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(orderService.rejectCancelRequest(id, body.get("reason")));
    }

    // body: { "ids": [..], "action": "CONFIRM" | "PREPARE" | "SHIP", "pickupMethod", "pickupDate" }
    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> bulk(@RequestBody Map<String, Object> body) {
        List<Long> ids = new ArrayList<>();
        if (body.get("ids") instanceof List<?> list) list.forEach(x -> ids.add(Long.valueOf(x.toString())));
        OrderService.BulkOrderAction action;
        try {
            action = OrderService.BulkOrderAction.valueOf(String.valueOf(body.get("action")));
        } catch (IllegalArgumentException e) {
            action = null;
        }
        return ResponseEntity.ok(orderService.bulk(ids, action, (String) body.get("pickupMethod"), date((String) body.get("pickupDate"))));
    }

    // ?ids=1,2,3 -> { labels, skipped }; marks them printed
    @PostMapping("/labels")
    public ResponseEntity<Map<String, Object>> labels(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(orderService.labels(ids));
    }

    private static LocalDate date(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new ApiException("Ngày không hợp lệ", HttpStatus.BAD_REQUEST);
        }
    }
}
