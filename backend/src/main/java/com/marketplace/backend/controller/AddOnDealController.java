package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.AddOnDealRequest;
import com.marketplace.backend.service.AddOnDealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** "Mua Kem Deal Soc" / "Mua de nhan qua". */
@RestController
@RequiredArgsConstructor
public class AddOnDealController {

    private final AddOnDealService dealService;

    @GetMapping("/api/seller/addon-deals")
    public ResponseEntity<List<Map<String, Object>>> mine() {
        return ResponseEntity.ok(dealService.mine());
    }

    @PostMapping("/api/seller/addon-deals")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody AddOnDealRequest req) {
        return ResponseEntity.ok(dealService.create(req));
    }

    @PutMapping("/api/seller/addon-deals/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody AddOnDealRequest req) {
        return ResponseEntity.ok(dealService.update(id, req));
    }

    @PatchMapping("/api/seller/addon-deals/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(dealService.toggle(id));
    }

    @PostMapping("/api/seller/addon-deals/{id}/end")
    public ResponseEntity<Map<String, Object>> end(@PathVariable Long id) {
        return ResponseEntity.ok(dealService.end(id));
    }

    // public: the add-on items / gifts unlocked by buying this product
    @GetMapping("/api/products/{id}/addon-deals")
    public ResponseEntity<List<Map<String, Object>>> forProduct(@PathVariable Long id) {
        return ResponseEntity.ok(dealService.forProduct(id));
    }
}
