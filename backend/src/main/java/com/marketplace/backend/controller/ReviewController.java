package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.ReviewRequest;
import com.marketplace.backend.dto.response.ReviewResponse;
import com.marketplace.backend.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Reading reviews is public (ProductController /api/products/{id}/reviews);
// writing one needs a delivered order line of the current buyer.
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/api/buyer/reviews")
    public ResponseEntity<ReviewResponse> create(@Valid @RequestBody ReviewRequest req) {
        return ResponseEntity.ok(reviewService.create(req));
    }
}
