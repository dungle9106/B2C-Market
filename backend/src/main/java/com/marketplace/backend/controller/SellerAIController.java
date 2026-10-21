package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.GenerateDescriptionRequest;
import com.marketplace.backend.service.PromoAdvisorService;
import com.marketplace.backend.service.ReviewInsightService;
import com.marketplace.backend.service.SalesAssistantService;
import com.marketplace.backend.service.SellerAIService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// "AI danh cho Seller". Every tool works without an LLM (rules + statistics over the shop's data);
// with app.ai.provider configured the texts are written by the model from the same facts.
// The seller always reviews AI output - nothing is published automatically.
@RestController
@RequestMapping("/api/seller/ai")
@RequiredArgsConstructor
public class SellerAIController {

    private final SellerAIService sellerAIService;
    private final ReviewInsightService reviewInsightService;
    private final SalesAssistantService salesAssistant;
    private final PromoAdvisorService promoAdvisor;

    // { reply: description draft, titleSuggestion, source: ai | rule }
    @PostMapping("/generate-description")
    public ResponseEntity<Map<String, String>> generateDescription(@Valid @RequestBody GenerateDescriptionRequest req) {
        return ResponseEntity.ok(sellerAIService.describe(req.getProductName(), req.getMaterial(), req.getUsage(), req.getTargetUser(),
                req.getHighlights(), req.getCategoryId(), req.getTone(), req.getVariants()));
    }

    // body: { name, categoryId?, cost? } -> price range of comparable products and a suggested price
    @PostMapping("/price-suggestion")
    public ResponseEntity<Map<String, Object>> priceSuggestion(@RequestBody Map<String, Object> body) {
        Object cat = body.get("categoryId");
        Object cost = body.get("cost");
        return ResponseEntity.ok(sellerAIService.suggestPrice((String) body.get("name"),
                cat instanceof Number n ? n.longValue() : null,
                cost instanceof Number c && c.doubleValue() > 0 ? BigDecimal.valueOf(c.doubleValue()) : null));
    }

    // ?productId=&days=90 : praise / complaint themes of the shop's reviews
    @GetMapping("/review-insights")
    public ResponseEntity<Map<String, Object>> reviewInsights(@RequestParam(required = false) Long productId,
                                                              @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(reviewInsightService.insights(productId, days));
    }

    // body: { question } -> { answer, period, table?, chart?, suggestions }
    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(salesAssistant.ask(body.get("question")));
    }

    @GetMapping("/ask/examples")
    public ResponseEntity<List<String>> examples() {
        return ResponseEntity.ok(salesAssistant.examples());
    }

    // when to run promotions: weekdays, hours, month days, upcoming events, products to discount
    @GetMapping("/promo-timing")
    public ResponseEntity<Map<String, Object>> promoTiming() {
        return ResponseEntity.ok(promoAdvisor.advise());
    }
}
