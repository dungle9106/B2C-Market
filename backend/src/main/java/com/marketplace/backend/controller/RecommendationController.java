package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.ViewHistory;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ViewHistoryRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Rule-based "Co the ban thich" feed: recently viewed products + same-category
// products, deduplicated. No ML model is trained here - see the recommendation
// pipeline note in ProductService.recommendSimilar for the same technique.
@RestController
@RequestMapping("/api/buyer/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final ViewHistoryRepository viewHistoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true) // touches lazy product/category/shop
    @GetMapping("/for-you")
    public ResponseEntity<List<ProductResponse>> forYou() {
        List<ViewHistory> recentViews = viewHistoryRepository.findTop10ByUserIdOrderByViewedAtDesc(CurrentUser.id());

        Set<ProductResponse> results = new LinkedHashSet<>();
        for (ViewHistory view : recentViews) {
            Long categoryId = view.getProduct().getCategory() != null ? view.getProduct().getCategory().getId() : null;
            if (categoryId == null) continue;
            productRepository.findSimilarByCategory(categoryId, view.getProduct().getId())
                    .stream().limit(4)
                    .map(ProductResponse::from)
                    .forEach(results::add);
            if (results.size() >= 12) break;
        }

        if (results.isEmpty()) {
            // cold start: no view history yet -> just show best sellers
            productRepository.findTop10ByActiveTrueOrderBySoldCountDesc()
                    .forEach(p -> results.add(ProductResponse.from(p)));
        }

        return ResponseEntity.ok(results.stream().limit(12).collect(Collectors.toList()));
    }
}
