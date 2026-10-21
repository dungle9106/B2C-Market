package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.ChatRequest;
import com.marketplace.backend.dto.response.ChatbotResponse;
import com.marketplace.backend.service.ChatbotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Buyer-facing chatbot endpoints (RAG). /api/ai/** is restricted to ROLE_BUYER in SecurityConfig.
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/chat")
    public ResponseEntity<ChatbotResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatbotService.chat(request.getMessage()));
    }

    // [{ message, reply, createdAt }] oldest first
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> history() {
        return ResponseEntity.ok(chatbotService.history());
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory() {
        chatbotService.clearHistory();
        return ResponseEntity.noContent().build();
    }
}
