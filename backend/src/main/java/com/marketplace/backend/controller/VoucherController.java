package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.VoucherRequest;
import com.marketplace.backend.dto.response.VoucherResponse;
import com.marketplace.backend.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    // buyer's voucher picker at checkout: platform vouchers + those of the shops in the order
    @GetMapping("/api/vouchers/available")
    public ResponseEntity<List<VoucherResponse>> available(@RequestParam(required = false) List<Long> shopIds) {
        return ResponseEntity.ok(voucherService.availableFor(shopIds == null ? List.of() : shopIds));
    }

    // ---- seller: shop vouchers ----
    @GetMapping("/api/seller/vouchers")
    public ResponseEntity<List<VoucherResponse>> myShopVouchers() {
        return ResponseEntity.ok(voucherService.myShopVouchers());
    }

    @PostMapping("/api/seller/vouchers")
    public ResponseEntity<VoucherResponse> createShopVoucher(@Valid @RequestBody VoucherRequest req) {
        return ResponseEntity.ok(voucherService.createShopVoucher(req));
    }

    @PutMapping("/api/seller/vouchers/{id}")
    public ResponseEntity<VoucherResponse> updateShopVoucher(@PathVariable Long id, @Valid @RequestBody VoucherRequest req) {
        return ResponseEntity.ok(voucherService.updateShopVoucher(id, req));
    }

    @PostMapping("/api/seller/vouchers/{id}/end")
    public ResponseEntity<VoucherResponse> endShopVoucher(@PathVariable Long id) {
        return ResponseEntity.ok(voucherService.endShopVoucher(id));
    }

    // public: running, visible vouchers of a shop (?productId= those that apply to that product)
    @GetMapping("/api/shops/{shopId}/vouchers")
    public ResponseEntity<List<VoucherResponse>> shopVouchers(@PathVariable Long shopId, @RequestParam(required = false) Long productId) {
        return ResponseEntity.ok(voucherService.publicForShop(shopId, productId));
    }

    @PatchMapping("/api/seller/vouchers/{id}/toggle")
    public ResponseEntity<VoucherResponse> toggleShopVoucher(@PathVariable Long id) {
        return ResponseEntity.ok(voucherService.toggleShopVoucher(id));
    }

    // ---- admin: platform vouchers ----
    @GetMapping("/api/admin/vouchers")
    public ResponseEntity<List<VoucherResponse>> platformVouchers() {
        return ResponseEntity.ok(voucherService.platformVouchers());
    }

    @PostMapping("/api/admin/vouchers")
    public ResponseEntity<VoucherResponse> createPlatformVoucher(@Valid @RequestBody VoucherRequest req) {
        return ResponseEntity.ok(voucherService.createPlatformVoucher(req));
    }

    @PutMapping("/api/admin/vouchers/{id}")
    public ResponseEntity<VoucherResponse> updatePlatformVoucher(@PathVariable Long id, @Valid @RequestBody VoucherRequest req) {
        return ResponseEntity.ok(voucherService.updatePlatformVoucher(id, req));
    }

    @PostMapping("/api/admin/vouchers/{id}/end")
    public ResponseEntity<VoucherResponse> endPlatformVoucher(@PathVariable Long id) {
        return ResponseEntity.ok(voucherService.endPlatformVoucher(id));
    }

    @PatchMapping("/api/admin/vouchers/{id}/toggle")
    public ResponseEntity<VoucherResponse> togglePlatformVoucher(@PathVariable Long id) {
        return ResponseEntity.ok(voucherService.togglePlatformVoucher(id));
    }
}
