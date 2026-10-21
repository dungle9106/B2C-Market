package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.service.CategoryAdminService;
import com.marketplace.backend.service.ProductModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Admin: platform categories and product moderation (PERM_PRODUCTS, see SecurityConfig).
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCatalogController {

    private final CategoryAdminService categoryService;
    private final ProductModerationService moderationService;

    // ==== categories ====

    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> categories() {
        return ResponseEntity.ok(categoryService.tree());
    }

    // body: { name, parentId? }
    @PostMapping("/categories")
    public ResponseEntity<Map<String, Object>> createCategory(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(categoryService.create((String) body.get("name"), id(body.get("parentId"))));
    }

    // body: { name, parentId? } - rename / move
    @PutMapping("/categories/{id}")
    public ResponseEntity<Map<String, Object>> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(categoryService.update(id, (String) body.get("name"), id(body.get("parentId"))));
    }

    @PostMapping("/categories/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleCategory(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.toggle(id));
    }

    // body: { parentId?, ids: [...] } in display order
    @SuppressWarnings("unchecked")
    @PostMapping("/categories/reorder")
    public ResponseEntity<Void> reorder(@RequestBody Map<String, Object> body) {
        List<Number> ids = (List<Number>) body.get("ids");
        categoryService.reorder(id(body.get("parentId")), ids == null ? null : ids.stream().map(Number::longValue).toList());
        return ResponseEntity.noContent().build();
    }

    // ?moveTo= : where its products go
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id, @RequestParam(required = false) Long moveTo) {
        categoryService.delete(id, moveTo);
        return ResponseEntity.noContent().build();
    }

    // ==== product moderation ====

    // ?status=PENDING|APPROVED|REJECTED|REMOVED|REPORTED|ALL&categoryId=&q=
    @GetMapping("/products")
    public ResponseEntity<PageResponse<Map<String, Object>>> products(@RequestParam(defaultValue = "PENDING") String status,
                                                                      @RequestParam(required = false) Long categoryId,
                                                                      @RequestParam(required = false) String q,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(moderationService.list(status, categoryId, q, page, size));
    }

    @GetMapping("/products/counts")
    public ResponseEntity<Map<String, Object>> counts() {
        return ResponseEntity.ok(moderationService.counts());
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Map<String, Object>> product(@PathVariable Long id) {
        return ResponseEntity.ok(moderationService.detail(id));
    }

    @PostMapping("/products/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(moderationService.approve(id));
    }

    // body: { ids: [...] }
    @SuppressWarnings("unchecked")
    @PostMapping("/products/bulk-approve")
    public ResponseEntity<Map<String, Object>> bulkApprove(@RequestBody Map<String, Object> body) {
        List<Number> ids = (List<Number>) body.get("ids");
        return ResponseEntity.ok(moderationService.bulkApprove(ids == null ? null : ids.stream().map(Number::longValue).toList()));
    }

    @PostMapping("/products/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(moderationService.reject(id, body.get("reason")));
    }

    // body: { violation, reason }
    @PostMapping("/products/{id}/remove")
    public ResponseEntity<Map<String, Object>> remove(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(moderationService.remove(id, body.get("violation"), body.get("reason")));
    }

    @PostMapping("/products/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(@PathVariable Long id) {
        return ResponseEntity.ok(moderationService.restore(id));
    }

    @PostMapping("/products/{id}/dismiss-reports")
    public ResponseEntity<Map<String, Object>> dismiss(@PathVariable Long id) {
        return ResponseEntity.ok(moderationService.dismissReports(id));
    }

    private static Long id(Object o) {
        return o instanceof Number n ? n.longValue() : o instanceof String s && !s.isBlank() ? Long.valueOf(s) : null;
    }
}
