package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.ReturnCreateRequest;
import com.marketplace.backend.dto.response.ReturnResponse;
import com.marketplace.backend.service.ReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Return / refund requests and complaints, for buyers, sellers and admins.
@RestController
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    // ---- buyer (paths under /api/orders and /api/buyer are BUYER-only in SecurityConfig) ----
    @PostMapping("/api/orders/{orderId}/returns")
    public ResponseEntity<ReturnResponse> create(@PathVariable Long orderId, @Valid @RequestBody ReturnCreateRequest req) {
        return ResponseEntity.ok(returnService.create(orderId, req));
    }

    @PostMapping("/api/buyer/returns/{id}/withdraw")
    public ResponseEntity<ReturnResponse> withdraw(@PathVariable Long id) {
        return ResponseEntity.ok(returnService.withdraw(id));
    }

    // body: { "complaint": "..." }
    @PostMapping("/api/buyer/returns/{id}/escalate")
    public ResponseEntity<ReturnResponse> escalate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(returnService.escalate(id, body.get("complaint")));
    }

    // ---- seller ----
    @GetMapping("/api/seller/returns")
    public ResponseEntity<List<ReturnResponse>> forMyShop() {
        return ResponseEntity.ok(returnService.forMyShop());
    }

    @PostMapping("/api/seller/returns/{id}/approve")
    public ResponseEntity<ReturnResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(returnService.approve(id));
    }

    // body: { "note": "ly do tu choi" }
    @PostMapping("/api/seller/returns/{id}/reject")
    public ResponseEntity<ReturnResponse> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(returnService.reject(id, body.get("note")));
    }

    // ---- admin: complaints ----
    @GetMapping("/api/admin/complaints")
    public ResponseEntity<List<ReturnResponse>> complaints(@RequestParam(defaultValue = "true") boolean open) {
        return ResponseEntity.ok(returnService.forAdmin(open));
    }

    // body: { "decision": "REFUND" | "REJECT", "note": "..." }
    @PostMapping("/api/admin/complaints/{id}/resolve")
    public ResponseEntity<ReturnResponse> resolve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(returnService.resolve(id, body.get("decision"), body.get("note")));
    }
}
