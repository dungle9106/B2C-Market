package com.marketplace.backend.controller;

import com.marketplace.backend.service.PriceWatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** "Bao khi giam gia" of the buyer (under /api/buyer: buyers only). */
@RestController
@RequestMapping("/api/buyer/price-watches")
@RequiredArgsConstructor
public class PriceWatchController {

    private final PriceWatchService service;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> mine() {
        return ResponseEntity.ok(service.mine());
    }

    @GetMapping("/{productId}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long productId) {
        return ResponseEntity.ok(service.status(productId));
    }

    // body (optional): { targetPrice } - only tell me at or below this price
    @PutMapping("/{productId}")
    public ResponseEntity<Map<String, Object>> watch(@PathVariable Long productId, @RequestBody(required = false) Map<String, Object> body) {
        Object t = body == null ? null : body.get("targetPrice");
        BigDecimal target;
        try {
            target = t == null || t.toString().isBlank() ? null : new BigDecimal(t.toString().trim());
        } catch (NumberFormatException e) {
            throw new com.marketplace.backend.exception.ApiException("Giá mong muốn không hợp lệ", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(service.watch(productId, target));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> unwatch(@PathVariable Long productId) {
        service.unwatch(productId);
        return ResponseEntity.noContent().build();
    }
}
