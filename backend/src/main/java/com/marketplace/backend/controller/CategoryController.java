package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.CategoryNode;
import com.marketplace.backend.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryNode>> listTopLevel() {
        return ResponseEntity.ok(categoryService.ordered().stream().filter(c -> c.getParent() == null && c.isActive())
                .map(c -> new CategoryNode(c.getId(), c.getName(), null, List.of())).toList());
    }

    // full tree (2 levels in practice) for menus and the seller's category picker
    @GetMapping("/tree")
    public ResponseEntity<List<CategoryNode>> tree() {
        return ResponseEntity.ok(categoryService.tree());
    }

    // breadcrumb path + direct children, for the category page
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.detail(id));
    }

    @GetMapping("/{id}/children")
    public ResponseEntity<List<CategoryNode>> children(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.ordered().stream()
                .filter(c -> c.isActive() && c.getParent() != null && c.getParent().getId().equals(id))
                .map(c -> new CategoryNode(c.getId(), c.getName(), id, List.of())).toList());
    }
}
