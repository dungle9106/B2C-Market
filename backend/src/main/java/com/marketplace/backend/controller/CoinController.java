package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.service.CoinService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** "Shoppi Xu" of the buyer: balance, daily check-in, history. */
@RestController
@RequestMapping("/api/buyer/coins")
@RequiredArgsConstructor
public class CoinController {

    private final CoinService coinService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(coinService.status());
    }

    @PostMapping("/check-in")
    public ResponseEntity<Map<String, Object>> checkIn() {
        return ResponseEntity.ok(coinService.checkIn());
    }

    @GetMapping("/history")
    public ResponseEntity<PageResponse<Map<String, Object>>> history(@RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(coinService.history(page, size));
    }
}
