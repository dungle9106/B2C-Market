package com.marketplace.backend.controller;

import com.marketplace.backend.service.ShopPerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** "Hieu qua hoat dong" (seller) and shop performance / penalty points (admin, permission SHOPS). */
@RestController
@RequiredArgsConstructor
public class ShopPerformanceController {

    private final ShopPerformanceService performanceService;

    @GetMapping("/api/seller/performance")
    public ResponseEntity<Map<String, Object>> mine() {
        return ResponseEntity.ok(performanceService.mine());
    }

    // ?q=&sort=points|nfr|late|orders&page=
    @GetMapping("/api/admin/shops/performance")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) String sort,
                                                    @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(performanceService.adminList(q, sort, page));
    }

    @GetMapping("/api/admin/shops/performance/settings")
    public ResponseEntity<Map<String, Object>> settings() {
        return ResponseEntity.ok(performanceService.settings());
    }

    @PutMapping("/api/admin/shops/performance/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(performanceService.updateSettings(body));
    }

    @PostMapping("/api/admin/shops/performance/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate() {
        return ResponseEntity.ok(performanceService.evaluateAll());
    }

    @GetMapping("/api/admin/shops/performance/{shopId}")
    public ResponseEntity<Map<String, Object>> shop(@PathVariable Long shopId) {
        return ResponseEntity.ok(performanceService.adminShop(shopId));
    }

    // body: { points 1-5, reason }
    @PostMapping("/api/admin/shops/performance/{shopId}/penalties")
    public ResponseEntity<Map<String, Object>> addPenalty(@PathVariable Long shopId, @RequestBody Map<String, Object> body) {
        Integer points = body.get("points") instanceof Number n ? n.intValue() : null;
        return ResponseEntity.ok(performanceService.addManual(shopId, points, (String) body.get("reason")));
    }

    // body: { reason }
    @PostMapping("/api/admin/shops/performance/penalties/{id}/revoke")
    public ResponseEntity<Map<String, Object>> revoke(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(performanceService.revoke(id, body.get("reason")));
    }
}
