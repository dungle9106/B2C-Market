package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.BroadcastRequest;
import com.marketplace.backend.dto.response.NotificationResponse;
import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.NotificationType;
import com.marketplace.backend.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Notifications of the logged-in user (any role), plus the admin broadcast tool.
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/api/notifications")
    public ResponseEntity<PageResponse<NotificationResponse>> mine(@RequestParam(required = false) NotificationType type,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.mine(type, page, size));
    }

    // polled by the header for the badges: { total, byType, chat }
    @GetMapping("/api/notifications/unread")
    public ResponseEntity<Map<String, Object>> unread() {
        return ResponseEntity.ok(notificationService.unreadSummary());
    }

    @PostMapping("/api/notifications/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markRead(id));
    }

    // ?type=ORDER marks one tab only
    @PostMapping("/api/notifications/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(@RequestParam(required = false) NotificationType type) {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead(type)));
    }

    @DeleteMapping("/api/notifications/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- admin ----
    @GetMapping("/api/admin/broadcasts")
    public ResponseEntity<List<Map<String, Object>>> broadcasts() {
        return ResponseEntity.ok(notificationService.broadcasts());
    }

    @PostMapping("/api/admin/broadcasts")
    public ResponseEntity<Map<String, Object>> broadcast(@Valid @RequestBody BroadcastRequest req) {
        return ResponseEntity.ok(notificationService.broadcast(req));
    }
}
