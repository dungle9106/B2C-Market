package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.SendMessageRequest;
import com.marketplace.backend.dto.response.ChatMessageResponse;
import com.marketplace.backend.dto.response.ConversationResponse;
import com.marketplace.backend.entity.ChatSide;
import com.marketplace.backend.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Buyer <-> shop chat. /api/buyer/** is BUYER-only and /api/seller/** SELLER-only in SecurityConfig,
// so the path decides which side of the conversation the caller is.
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // ---- buyer ----

    // body: { "shopId": 3 } -> the conversation with that shop (created if needed)
    @PostMapping("/api/buyer/chats")
    public ResponseEntity<ConversationResponse> start(@RequestBody Map<String, Long> body) {
        return ResponseEntity.ok(chatService.start(body.get("shopId")));
    }

    @GetMapping("/api/buyer/chats")
    public ResponseEntity<List<ConversationResponse>> buyerList() {
        return ResponseEntity.ok(chatService.list(ChatSide.BUYER));
    }

    @GetMapping("/api/buyer/chats/{id}")
    public ResponseEntity<ConversationResponse> buyerGet(@PathVariable Long id) {
        return ResponseEntity.ok(chatService.get(ChatSide.BUYER, id));
    }

    @GetMapping("/api/buyer/chats/{id}/messages")
    public ResponseEntity<List<ChatMessageResponse>> buyerMessages(@PathVariable Long id,
                                                                   @RequestParam(required = false) Long after,
                                                                   @RequestParam(required = false) Long before) {
        return ResponseEntity.ok(chatService.messages(ChatSide.BUYER, id, after, before));
    }

    @PostMapping("/api/buyer/chats/{id}/messages")
    public ResponseEntity<ChatMessageResponse> buyerSend(@PathVariable Long id, @Valid @RequestBody SendMessageRequest req) {
        return ResponseEntity.ok(chatService.send(ChatSide.BUYER, id, req));
    }

    @PostMapping("/api/buyer/chats/{id}/read")
    public ResponseEntity<Void> buyerRead(@PathVariable Long id) {
        chatService.markRead(ChatSide.BUYER, id);
        return ResponseEntity.noContent().build();
    }

    // ---- seller (shop side) ----

    @GetMapping("/api/seller/chats")
    public ResponseEntity<List<ConversationResponse>> shopList() {
        return ResponseEntity.ok(chatService.list(ChatSide.SHOP));
    }

    @GetMapping("/api/seller/chats/{id}")
    public ResponseEntity<ConversationResponse> shopGet(@PathVariable Long id) {
        return ResponseEntity.ok(chatService.get(ChatSide.SHOP, id));
    }

    @GetMapping("/api/seller/chats/{id}/messages")
    public ResponseEntity<List<ChatMessageResponse>> shopMessages(@PathVariable Long id,
                                                                  @RequestParam(required = false) Long after,
                                                                  @RequestParam(required = false) Long before) {
        return ResponseEntity.ok(chatService.messages(ChatSide.SHOP, id, after, before));
    }

    @PostMapping("/api/seller/chats/{id}/messages")
    public ResponseEntity<ChatMessageResponse> shopSend(@PathVariable Long id, @Valid @RequestBody SendMessageRequest req) {
        return ResponseEntity.ok(chatService.send(ChatSide.SHOP, id, req));
    }

    // response rate / time over the last 30 days
    @GetMapping("/api/seller/chats/stats")
    public ResponseEntity<Map<String, Object>> shopStats() {
        return ResponseEntity.ok(chatService.myResponseStats());
    }

    // customer panel: the buyer's orders and reviews with this shop
    @GetMapping("/api/seller/chats/{id}/customer")
    public ResponseEntity<Map<String, Object>> customer(@PathVariable Long id) {
        return ResponseEntity.ok(chatService.customer(id));
    }

    // "Goi y tra loi" for the buyer's latest message: { answer, source: ai | rule }
    @PostMapping("/api/seller/chats/{id}/suggest")
    public ResponseEntity<Map<String, String>> suggest(@PathVariable Long id) {
        return ResponseEntity.ok(chatService.suggest(id));
    }

    @PostMapping("/api/seller/chats/{id}/read")
    public ResponseEntity<Void> shopRead(@PathVariable Long id) {
        chatService.markRead(ChatSide.SHOP, id);
        return ResponseEntity.noContent().build();
    }
}
