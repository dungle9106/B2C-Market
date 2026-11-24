package com.marketplace.backend.controller;

import com.marketplace.backend.service.LuckySpinService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** "Vong quay may man" (under /api/buyer: buyers only). */
@RestController
@RequestMapping("/api/buyer/lucky-spin")
@RequiredArgsConstructor
public class LuckySpinController {

    private final LuckySpinService service;

    @GetMapping
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(service.status());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> spin() {
        return ResponseEntity.ok(service.spin());
    }
}
