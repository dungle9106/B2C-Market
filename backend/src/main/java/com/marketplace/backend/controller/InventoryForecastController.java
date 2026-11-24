package com.marketplace.backend.controller;

import com.marketplace.backend.service.forecast.InventoryForecastService;
import com.marketplace.backend.service.forecast.InventoryForecastService.Params;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** "Du bao nhap hang (AI)" of the seller's own shop. leadTime: days until new stock arrives; cover: days it should last. */
@RestController
@RequestMapping("/api/seller/inventory/forecast")
@RequiredArgsConstructor
public class InventoryForecastController {

    private final InventoryForecastService service;

    @GetMapping
    public ResponseEntity<Map<String, Object>> forecast(@RequestParam(required = false) Integer leadTime,
                                                        @RequestParam(required = false) Integer cover,
                                                        @RequestParam(required = false) Integer serviceLevel) {
        return ResponseEntity.ok(service.forecast(Params.of(leadTime, cover, serviceLevel)));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long productId,
                                                      @RequestParam(required = false) Long variantId,
                                                      @RequestParam(required = false) Integer leadTime,
                                                      @RequestParam(required = false) Integer cover,
                                                      @RequestParam(required = false) Integer serviceLevel) {
        return ResponseEntity.ok(service.detail(productId, variantId, Params.of(leadTime, cover, serviceLevel)));
    }

    @PostMapping("/advice")
    public ResponseEntity<Map<String, Object>> advice(@RequestParam(required = false) Integer leadTime,
                                                      @RequestParam(required = false) Integer cover,
                                                      @RequestParam(required = false) Integer serviceLevel) {
        return ResponseEntity.ok(service.advice(Params.of(leadTime, cover, serviceLevel)));
    }
}
