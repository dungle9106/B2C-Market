package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.ProductSearchCriteria;
import com.marketplace.backend.dto.response.*;
import com.marketplace.backend.entity.ProductEvent;
import com.marketplace.backend.security.UserPrincipal;
import com.marketplace.backend.security.VisitorKey;
import com.marketplace.backend.service.AdService;
import com.marketplace.backend.service.ComboService;
import com.marketplace.backend.service.ProductEventService;
import com.marketplace.backend.service.ProductModerationService;
import com.marketplace.backend.service.ProductService;
import com.marketplace.backend.service.QuestionService;
import com.marketplace.backend.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Public catalogue API (no login needed): browse, search, product page data.
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final com.marketplace.backend.service.search.SemanticSearchService semanticSearch;
    private final ProductService productService;
    private final ReviewService reviewService;
    private final QuestionService questionService;
    private final AdService adService;
    private final ComboService comboService;
    private final ProductEventService eventService;
    private final ProductModerationService moderationService;

    // ?q=&categoryId=&minPrice=&maxPrice=&minRating=&provinces=A&provinces=B
    //  &freeShipping=true&onSale=true&sort=price_asc&page=0&size=30
    @GetMapping("/search")
    public ResponseEntity<PageResponse<ProductResponse>> search(@ModelAttribute ProductSearchCriteria criteria) {
        PageResponse<ProductResponse> page = productService.search(criteria);
        // "Tai tro": sponsored products on top of the first page of a plain keyword search
        if (isPlainKeywordSearch(criteria)) {
            List<ProductResponse> ads = adService.sponsored(criteria.getQ());
            if (!ads.isEmpty()) {
                List<ProductResponse> items = new ArrayList<>(ads);
                items.addAll(page.getItems());
                page.setItems(items);
            }
        }
        return ResponseEntity.ok(page);
    }

    // ads only where they can't break the buyer's filters
    private static boolean isPlainKeywordSearch(ProductSearchCriteria c) {
        return c.getQ() != null && !c.getQ().isBlank() && c.getPage() == 0
                && "relevance".equals(c.getSort()) && c.getCategoryId() == null && c.getShopId() == null
                && c.getShopCategoryId() == null && c.getMinPrice() == null && c.getMaxPrice() == null
                && c.getMinRating() == null && (c.getProvinces() == null || c.getProvinces().isEmpty())
                && !Boolean.TRUE.equals(c.getFreeShipping()) && !Boolean.TRUE.equals(c.getOnSale());
    }

    // combos ("Mua 2 giam 5%") the product is in, running or upcoming
    @GetMapping("/{id}/combos")
    public ResponseEntity<List<Map<String, Object>>> combos(@PathVariable Long id) {
        return ResponseEntity.ok(comboService.forProduct(id));
    }

    @GetMapping("/suggest")
    public ResponseEntity<SuggestResponse> suggest(@RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(productService.suggest(q));
    }

    // "tìm theo ý nghĩa": products closest in meaning to q (embeddings); empty list when AI is not configured
    @GetMapping("/semantic")
    public ResponseEntity<List<ProductResponse>> semantic(@RequestParam String q, @RequestParam(defaultValue = "12") int limit) {
        return ResponseEntity.ok(semanticSearch.search(q, limit));
    }

    @GetMapping("/flash-sale")
    public ResponseEntity<List<ProductResponse>> flashSale(@RequestParam(defaultValue = "12") int limit) {
        return ResponseEntity.ok(productService.flashSales(limit));
    }

    @GetMapping("/top-selling")
    public ResponseEntity<List<ProductResponse>> topSelling() {
        return ResponseEntity.ok(productService.topSelling());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> detail(@PathVariable Long id,
                                                  @AuthenticationPrincipal UserPrincipal principal,
                                                  HttpServletRequest request) {
        Long viewerId = principal != null ? principal.getId() : null;
        ProductResponse product = productService.getDetail(id, viewerId);
        // traffic for the seller's analytics (one view per visitor per 30 minutes)
        eventService.record(ProductEvent.Type.VIEW, product.getId(), product.getShopId(), viewerId, VisitorKey.of(request, viewerId));
        return ResponseEntity.ok(product);
    }

    // "Bao cao san pham vi pham": the reasons buyers can pick (public)
    @GetMapping("/report-reasons")
    public ResponseEntity<List<Map<String, String>>> reportReasons() {
        return ResponseEntity.ok(ProductModerationService.VIOLATIONS.entrySet().stream()
                .map(e -> Map.of("key", e.getKey(), "label", e.getValue())).toList());
    }

    // any signed-in user (POST under /api/products is not public); body: { reason, detail? }
    @PostMapping("/{id}/report")
    public ResponseEntity<Map<String, Object>> report(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(moderationService.report(id, body.get("reason"), body.get("detail")));
    }

    @GetMapping("/{id}/similar")
    public ResponseEntity<List<ProductResponse>> similar(@PathVariable Long id) {
        return ResponseEntity.ok(productService.recommendSimilar(id));
    }

    // filter: all | media | 1..5
    @GetMapping("/{id}/reviews")
    public ResponseEntity<PageResponse<ReviewResponse>> reviews(@PathVariable Long id,
                                                                @RequestParam(defaultValue = "all") String filter,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "6") int size) {
        return ResponseEntity.ok(reviewService.list(id, filter, page, size));
    }

    @GetMapping("/{id}/reviews/summary")
    public ResponseEntity<ReviewSummaryResponse> reviewSummary(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.summary(id));
    }

    @GetMapping("/{id}/questions")
    public ResponseEntity<PageResponse<QuestionResponse>> questions(@PathVariable Long id,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(questionService.forProduct(id, page, size));
    }
}
