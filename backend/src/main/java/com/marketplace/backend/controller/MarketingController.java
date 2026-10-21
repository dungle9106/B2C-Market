package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.ComboRequest;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.security.VisitorKey;
import com.marketplace.backend.service.AdService;
import com.marketplace.backend.service.ComboService;
import com.marketplace.backend.service.FlashSaleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// "Kenh Marketing" of the seller centre (flash sales, combos, ads) + the public ad click.
@RestController
@RequiredArgsConstructor
public class MarketingController {

    private final FlashSaleService flashSaleService;
    private final ComboService comboService;
    private final AdService adService;

    // ---- flash sale ----

    @GetMapping("/api/seller/flash-sales")
    public ResponseEntity<List<Map<String, Object>>> flashSales() {
        return ResponseEntity.ok(flashSaleService.list());
    }

    // body: { startAt, endAt, items: [{ productId, flashPrice, flashStock?, maxPerBuyer? }] }
    @SuppressWarnings("unchecked")
    @PostMapping("/api/seller/flash-sales")
    public ResponseEntity<Map<String, Object>> scheduleFlashSale(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(flashSaleService.schedule(time(body.get("startAt")), time(body.get("endAt")),
                body.get("items") instanceof List<?> l ? (List<Map<String, Object>>) l : null));
    }

    @DeleteMapping("/api/seller/flash-sales/{productId}")
    public ResponseEntity<Void> endFlashSale(@PathVariable Long productId) {
        flashSaleService.end(productId);
        return ResponseEntity.noContent().build();
    }

    // ---- combos ----

    @GetMapping("/api/seller/combos")
    public ResponseEntity<List<Map<String, Object>>> combos() {
        return ResponseEntity.ok(comboService.mine());
    }

    @PostMapping("/api/seller/combos")
    public ResponseEntity<Map<String, Object>> createCombo(@Valid @RequestBody ComboRequest req) {
        return ResponseEntity.ok(comboService.create(req));
    }

    @PutMapping("/api/seller/combos/{id}")
    public ResponseEntity<Map<String, Object>> updateCombo(@PathVariable Long id, @Valid @RequestBody ComboRequest req) {
        return ResponseEntity.ok(comboService.update(id, req));
    }

    @PatchMapping("/api/seller/combos/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleCombo(@PathVariable Long id) {
        return ResponseEntity.ok(comboService.toggle(id));
    }

    @PostMapping("/api/seller/combos/{id}/end")
    public ResponseEntity<Map<String, Object>> endCombo(@PathVariable Long id) {
        return ResponseEntity.ok(comboService.end(id));
    }

    // ---- ads ----

    @GetMapping("/api/seller/ads/wallet")
    public ResponseEntity<Map<String, Object>> wallet(@RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(adService.wallet(page));
    }

    // body: { "amount": 200000 } (simulated payment)
    @PostMapping("/api/seller/ads/wallet/topup")
    public ResponseEntity<Map<String, Object>> topUp(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adService.topUp(money(body.get("amount"))));
    }

    @GetMapping("/api/seller/ads")
    public ResponseEntity<List<Map<String, Object>>> campaigns() {
        return ResponseEntity.ok(adService.campaigns());
    }

    // body: { productId, keywords: ["ao thun", ...], bidPerClick, dailyBudget }
    @PostMapping("/api/seller/ads")
    public ResponseEntity<Map<String, Object>> createCampaign(@RequestBody Map<String, Object> body) {
        Long productId = body.get("productId") instanceof Number n ? n.longValue() : null;
        return ResponseEntity.ok(adService.create(productId, body.get("keywords"), money(body.get("bidPerClick")), money(body.get("dailyBudget"))));
    }

    @PutMapping("/api/seller/ads/{id}")
    public ResponseEntity<Map<String, Object>> updateCampaign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adService.update(id, body.get("keywords"), money(body.get("bidPerClick")), money(body.get("dailyBudget"))));
    }

    // body: { "status": "ACTIVE" | "PAUSED" | "ENDED" }
    @PatchMapping("/api/seller/ads/{id}/status")
    public ResponseEntity<Map<String, Object>> campaignStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(adService.setStatus(id, body.get("status")));
    }

    // public: a buyer (or guest) clicked a sponsored result
    @PostMapping("/api/ads/{id}/click")
    public ResponseEntity<Map<String, Object>> click(@PathVariable Long id, HttpServletRequest request) {
        Long userId = VisitorKey.userIdOrNull();
        return ResponseEntity.ok(adService.click(id, userId, VisitorKey.of(request, userId)));
    }

    private static BigDecimal money(Object o) {
        return o instanceof Number n ? new BigDecimal(n.toString()) : null;
    }

    private static LocalDateTime time(Object o) {
        if (o == null) return null;
        try {
            return LocalDateTime.parse(o.toString());
        } catch (Exception e) {
            throw new ApiException("Thời gian không hợp lệ", HttpStatus.BAD_REQUEST);
        }
    }
}
