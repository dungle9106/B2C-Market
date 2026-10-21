package com.marketplace.backend.controller;

import com.marketplace.backend.service.CustomerCareService;
import com.marketplace.backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// "Cham soc khach hang" tools of the seller centre (SELLER only, see SecurityConfig /api/seller/**)
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class CustomerCareController {

    private final CustomerCareService careService;
    private final ReviewService reviewService;

    // counters for the sidebar badges
    @GetMapping("/engagement")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(careService.summary());
    }

    // ---- reply templates: { CHAT: [...], QUESTION: [...], REVIEW: [...] } ----

    @GetMapping("/reply-templates")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> templates() {
        return ResponseEntity.ok(careService.templates());
    }

    // body: { kind: CHAT | QUESTION | REVIEW, content }
    @PostMapping("/reply-templates")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(careService.createTemplate(body.get("kind"), body.get("content")));
    }

    @PutMapping("/reply-templates/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(careService.updateTemplate(id, body.get("content")));
    }

    @DeleteMapping("/reply-templates/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        careService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    // ---- chat auto-replies ----

    @GetMapping("/chat-settings")
    public ResponseEntity<Map<String, Object>> chatSettings() {
        return ResponseEntity.ok(careService.chatSettings());
    }

    @PutMapping("/chat-settings")
    public ResponseEntity<Map<String, Object>> updateChatSettings(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(careService.updateChatSettings(body));
    }

    // ---- reviews: one template to many reviews ----

    // body: { ids: [..], reply } -> { replied, skipped }
    @PostMapping("/reviews/bulk-reply")
    public ResponseEntity<Map<String, Object>> bulkReply(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        return ResponseEntity.ok(reviewService.bulkReply(ids == null ? null : ids.stream().map(Number::longValue).toList(),
                (String) body.get("reply")));
    }
}
