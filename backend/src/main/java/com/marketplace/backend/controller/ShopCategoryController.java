package com.marketplace.backend.controller;

import com.marketplace.backend.service.ShopCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// "Danh muc cua Shop": the seller manages them, buyers see them as tabs on the shop page.
@RestController
@RequiredArgsConstructor
public class ShopCategoryController {

    private final ShopCategoryService categoryService;

    // ---- seller ----

    @GetMapping("/api/seller/shop-categories")
    public ResponseEntity<List<Map<String, Object>>> mine() {
        return ResponseEntity.ok(categoryService.mine());
    }

    // body: { "name": "Hàng mới về" }
    @PostMapping("/api/seller/shop-categories")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(categoryService.create(body.get("name")));
    }

    @PutMapping("/api/seller/shop-categories/{id}")
    public ResponseEntity<Map<String, Object>> rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(categoryService.rename(id, body.get("name")));
    }

    @DeleteMapping("/api/seller/shop-categories/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // body: { "ids": [3, 1, 2] } in the new order
    @PutMapping("/api/seller/shop-categories/order")
    public ResponseEntity<List<Map<String, Object>>> reorder(@RequestBody Map<String, List<Number>> body) {
        return ResponseEntity.ok(categoryService.reorder(toIds(body.get("ids"))));
    }

    // body: { "productIds": [..], "add": true }
    @PostMapping("/api/seller/shop-categories/{id}/products")
    public ResponseEntity<Map<String, Object>> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        List<Number> ids = body.get("productIds") instanceof List<?> l ? l.stream().map(x -> (Number) x).toList() : List.of();
        return ResponseEntity.ok(categoryService.assign(id, toIds(ids), !Boolean.FALSE.equals(body.get("add"))));
    }

    // ---- public ----

    @GetMapping("/api/shops/{shopId}/categories")
    public ResponseEntity<List<Map<String, Object>>> forShop(@PathVariable Long shopId) {
        return ResponseEntity.ok(categoryService.forShop(shopId));
    }

    private static List<Long> toIds(List<Number> ids) {
        List<Long> out = new ArrayList<>();
        if (ids != null) ids.forEach(n -> out.add(n.longValue()));
        return out;
    }
}
