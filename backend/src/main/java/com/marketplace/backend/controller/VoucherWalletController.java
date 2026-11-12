package com.marketplace.backend.controller;

import com.marketplace.backend.service.VoucherWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** "Kho Voucher" of the buyer. */
@RestController
@RequestMapping("/api/buyer/voucher-wallet")
@RequiredArgsConstructor
public class VoucherWalletController {

    private final VoucherWalletService walletService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> wallet() {
        return ResponseEntity.ok(walletService.wallet());
    }

    @GetMapping("/ids")
    public ResponseEntity<List<Long>> savedIds() {
        return ResponseEntity.ok(walletService.savedIds());
    }

    @PostMapping("/{voucherId}")
    public ResponseEntity<Map<String, Object>> save(@PathVariable Long voucherId) {
        return ResponseEntity.ok(walletService.save(voucherId));
    }

    // body: { code } - also saves a private voucher shared by a shop
    @PostMapping("/code")
    public ResponseEntity<Map<String, Object>> saveByCode(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(walletService.saveByCode(body.get("code")));
    }

    @DeleteMapping("/{voucherId}")
    public ResponseEntity<Map<String, Object>> remove(@PathVariable Long voucherId) {
        return ResponseEntity.ok(walletService.remove(voucherId));
    }
}
