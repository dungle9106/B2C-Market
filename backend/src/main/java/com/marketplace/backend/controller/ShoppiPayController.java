package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.service.MembershipService;
import com.marketplace.backend.service.ShoppiPayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/** "Vi ShoppiPay" and "Hang thanh vien" of the buyer. */
@RestController
@RequestMapping("/api/buyer")
@RequiredArgsConstructor
public class ShoppiPayController {

    private final ShoppiPayService walletService;
    private final MembershipService membershipService;

    @GetMapping("/wallet")
    public ResponseEntity<Map<String, Object>> wallet() {
        return ResponseEntity.ok(walletService.status());
    }

    // body: { amount }
    @PostMapping("/wallet/top-up")
    public ResponseEntity<Map<String, Object>> topUp(@RequestBody Map<String, BigDecimal> body) {
        return ResponseEntity.ok(walletService.topUp(body.get("amount")));
    }

    @GetMapping("/wallet/history")
    public ResponseEntity<PageResponse<Map<String, Object>>> history(@RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(walletService.history(page, size));
    }

    @GetMapping("/membership")
    public ResponseEntity<Map<String, Object>> membership() {
        return ResponseEntity.ok(membershipService.mine());
    }
}
