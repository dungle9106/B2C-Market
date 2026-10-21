package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.InventoryLog;
import com.marketplace.backend.entity.InventoryReason;
import com.marketplace.backend.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// "Quan ly ton kho" (seller only: /api/seller/** in SecurityConfig)
@RestController
@RequestMapping("/api/seller/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // { updatedAt, lowStockThreshold, summary, rows }; polled by the inventory page
    @GetMapping
    public ResponseEntity<Map<String, Object>> overview() {
        return ResponseEntity.ok(inventoryService.overview());
    }

    // movement history, newest first; ?productId= for one product
    @GetMapping("/logs")
    public ResponseEntity<PageResponse<InventoryLog>> logs(@RequestParam(required = false) Long productId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inventoryService.logs(productId, page, size));
    }

    // body: { productId, variantId?, mode: ADD|SUBTRACT|SET, quantity, reason: RESTOCK|STOCKTAKE|DAMAGED|OTHER, note }
    @PostMapping("/adjust")
    public ResponseEntity<Map<String, Object>> adjust(@RequestBody Map<String, Object> body) {
        InventoryReason reason;
        try {
            reason = InventoryReason.valueOf(String.valueOf(body.get("reason")));
        } catch (IllegalArgumentException e) {
            reason = null;
        }
        return ResponseEntity.ok(inventoryService.adjust(
                number(body.get("productId")), number(body.get("variantId")), (String) body.get("mode"),
                body.get("quantity") instanceof Number n ? n.intValue() : null, reason, (String) body.get("note")));
    }

    private static Long number(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }
}
