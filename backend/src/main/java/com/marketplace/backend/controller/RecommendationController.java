package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.service.reco.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// "Co the ban thich": the recommendation model trained on buyers' behaviour (RecommendationService), with the old
// rules (same category as the recently viewed products, best sellers) when the model can't answer.
@RestController
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/api/buyer/recommendations/for-you")
    public ResponseEntity<List<ProductResponse>> forYou() {
        return ResponseEntity.ok(recommendationService.forUser(CurrentUser.id()));
    }

    // admin "Mo hinh goi y": what the model learned from and how well it does on held-out data
    @GetMapping("/api/admin/reco/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(recommendationService.status());
    }

    @PostMapping("/api/admin/reco/train")
    public ResponseEntity<Map<String, Object>> train() {
        try {
            return ResponseEntity.ok(recommendationService.train());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
