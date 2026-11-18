package com.marketplace.backend.controller;

import com.marketplace.backend.service.risk.ChurnService;
import com.marketplace.backend.service.risk.FraudService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// Admin "AI phat hien gian lan don" (PERM_ORDERS) and "AI du doan roi bo" (PERM_VOUCHERS: it sends vouchers).
@RestController
@RequiredArgsConstructor
public class RiskController {

    private final FraudService fraudService;
    private final ChurnService churnService;

    // ==================== fraud ====================

    @GetMapping("/api/admin/fraud/status")
    public Map<String, Object> fraudStatus() {
        return fraudService.status();
    }

    @PostMapping("/api/admin/fraud/train")
    public ResponseEntity<Map<String, Object>> fraudTrain() {
        return training(fraudService::train);
    }

    @GetMapping("/api/admin/fraud/orders")
    public Map<String, Object> flagged(@RequestParam(required = false) String level, @RequestParam(required = false) String verdict,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return fraudService.list(level, verdict, page, size);
    }

    // body: {"verdict": "SAFE" | "FRAUD" | null}
    @PatchMapping("/api/admin/fraud/{id}/verdict")
    public Map<String, Object> verdict(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String v = body.get("verdict");
        return fraudService.review(id, v == null || v.isBlank() ? null : v);
    }

    // ==================== churn ====================

    @GetMapping("/api/admin/churn/status")
    public Map<String, Object> churnStatus() {
        return churnService.status();
    }

    @PostMapping("/api/admin/churn/train")
    public ResponseEntity<Map<String, Object>> churnTrain() {
        return training(churnService::train);
    }

    @GetMapping("/api/admin/churn/buyers")
    public Map<String, Object> atRisk(@RequestParam(required = false) String level, @RequestParam(defaultValue = "risk") String sort,
                                      @RequestParam(defaultValue = "100") int limit) {
        return churnService.atRisk(level, sort, limit);
    }

    public record WinbackRequest(List<Long> userIds, Integer percent, Long maxDiscount, Long minOrder, Integer days) {}

    @PostMapping("/api/admin/churn/winback")
    public Map<String, Object> winback(@RequestBody WinbackRequest req) {
        return churnService.sendWinback(req.userIds(), req.percent() == null ? 15 : req.percent(),
                req.maxDiscount() == null ? 50000 : req.maxDiscount(), req.minOrder() == null ? 0 : req.minOrder(),
                req.days() == null ? 14 : req.days());
    }

    private static ResponseEntity<Map<String, Object>> training(Supplier<Map<String, Object>> train) {
        try {
            return ResponseEntity.ok(train.get());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
