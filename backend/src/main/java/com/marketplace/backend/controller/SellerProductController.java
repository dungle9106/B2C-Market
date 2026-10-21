package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.ProductRequest;
import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.service.ProductExcelService;
import com.marketplace.backend.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/seller/products")
@RequiredArgsConstructor
public class SellerProductController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ProductService productService;
    private final ProductExcelService excelService;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> myProducts() {
        return ResponseEntity.ok(productService.myProducts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> one(@PathVariable Long id) {
        return ResponseEntity.ok(productService.myProduct(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<Void> toggleActive(@PathVariable Long id) {
        productService.toggleActive(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- quick edits ----

    // body: { "stock": 10 } for a product without variants, { "variants": { "<variantId>": 5, ... } } otherwise
    @PatchMapping("/{id}/stock")
    public ResponseEntity<ProductResponse> updateStock(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer stock = body.get("stock") instanceof Number n ? n.intValue() : null;
        Map<Long, Integer> variants = new HashMap<>();
        if (body.get("variants") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if (v instanceof Number n) variants.put(Long.valueOf(k.toString()), n.intValue());
            });
        }
        return ResponseEntity.ok(productService.updateStock(id, stock, variants));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ProductResponse> duplicate(@PathVariable Long id) {
        return ResponseEntity.ok(productService.duplicate(id));
    }

    // body: { "ids": [1,2], "action": "HIDE|SHOW|SOLD_OUT|DELETE|ADD_CATEGORY|REMOVE_CATEGORY", "shopCategoryId": 3 }
    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> bulk(@RequestBody Map<String, Object> body) {
        List<Long> ids = new ArrayList<>();
        if (body.get("ids") instanceof List<?> list) list.forEach(x -> ids.add(Long.valueOf(x.toString())));
        ProductService.BulkAction action;
        try {
            action = ProductService.BulkAction.valueOf(String.valueOf(body.get("action")));
        } catch (IllegalArgumentException e) {
            action = null;
        }
        Long categoryId = body.get("shopCategoryId") instanceof Number n ? n.longValue() : null;
        return ResponseEntity.ok(productService.bulk(ids, action, categoryId));
    }

    // REJECTED / REMOVED -> PENDING after the seller fixed it
    @PostMapping("/{id}/resubmit")
    public ResponseEntity<ProductResponse> resubmit(@PathVariable Long id) {
        return ResponseEntity.ok(productService.resubmit(id));
    }

    // ---- Excel ----

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template() {
        return file(excelService.template(), "Shoppi_mau_dang_san_pham.xlsx");
    }

    // dryRun=true: check only, nothing is created. Returns { dryRun, rows, products, errors, created }
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importFile(@RequestParam("file") MultipartFile file,
                                                          @RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(excelService.importFile(file, dryRun));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return file(excelService.export(), "Shoppi_san_pham_" + LocalDate.now() + ".xlsx");
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
