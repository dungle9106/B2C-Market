package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.AnswerRequest;
import com.marketplace.backend.dto.request.QuestionRequest;
import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.dto.response.QuestionResponse;
import com.marketplace.backend.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Product Q&A. Listing a product's questions is public (ProductController).
@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    // any logged-in user (SecurityConfig: anyRequest().authenticated())
    @PostMapping("/api/questions")
    public ResponseEntity<QuestionResponse> ask(@Valid @RequestBody QuestionRequest req) {
        return ResponseEntity.ok(questionService.ask(req));
    }

    // status=UNANSWERED|ANSWERED|HIDDEN, q= text search, productId=
    @GetMapping("/api/seller/questions")
    public ResponseEntity<PageResponse<QuestionResponse>> myShopQuestions(@RequestParam(defaultValue = "UNANSWERED") String status,
                                                                          @RequestParam(required = false) String q,
                                                                          @RequestParam(required = false) Long productId,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(questionService.forMyShop(status, q, productId, page, size));
    }

    @GetMapping("/api/seller/questions/counts")
    public ResponseEntity<Map<String, Long>> counts() {
        return ResponseEntity.ok(questionService.counts());
    }

    // answer, or edit the answer
    @PostMapping("/api/seller/questions/{id}/answer")
    public ResponseEntity<QuestionResponse> answer(@PathVariable Long id, @Valid @RequestBody AnswerRequest req) {
        return ResponseEntity.ok(questionService.answer(id, req.getAnswer()));
    }

    @PostMapping("/api/seller/questions/{id}/hide")
    public ResponseEntity<QuestionResponse> hide(@PathVariable Long id) {
        return ResponseEntity.ok(questionService.setHidden(id, true));
    }

    @PostMapping("/api/seller/questions/{id}/unhide")
    public ResponseEntity<QuestionResponse> unhide(@PathVariable Long id) {
        return ResponseEntity.ok(questionService.setHidden(id, false));
    }

    // "Goi y tra loi": { answer, source: ai | rule }
    @PostMapping("/api/seller/questions/{id}/suggest")
    public ResponseEntity<Map<String, String>> suggest(@PathVariable Long id) {
        return ResponseEntity.ok(questionService.suggest(id));
    }
}
