package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.service.returnrisk.ReturnRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** "AI phat hien lam dung doi tra" of the admin console (PERM_ORDERS in SecurityConfig). */
@RestController
@RequestMapping("/api/admin/return-risk")
@RequiredArgsConstructor
public class ReturnRiskController {

    private final ReturnRiskService riskService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(riskService.summary());
    }

    // ?level=ALL|LOW|MEDIUM|HIGH&review=ALL|TODO|ABUSE|LEGIT
    @GetMapping
    public ResponseEntity<PageResponse<Map<String, Object>>> list(@RequestParam(defaultValue = "ALL") String level,
                                                                  @RequestParam(defaultValue = "ALL") String review,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(riskService.list(level, review, page, size));
    }

    @GetMapping("/{returnId}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long returnId) {
        return ResponseEntity.ok(riskService.detail(returnId));
    }

    @PostMapping("/{returnId}/reanalyze")
    public ResponseEntity<Map<String, Object>> reanalyze(@PathVariable Long returnId) {
        return ResponseEntity.ok(riskService.reanalyze(returnId));
    }

    // body: { label: "ABUSE" | "LEGIT" | null, note }
    @PostMapping("/{returnId}/review")
    public ResponseEntity<Map<String, Object>> review(@PathVariable Long returnId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(riskService.review(returnId, body.get("label"), body.get("note")));
    }

    @PostMapping("/train")
    public ResponseEntity<Map<String, Object>> train() {
        return ResponseEntity.ok(riskService.train());
    }
}
