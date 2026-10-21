package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.TicketCreateRequest;
import com.marketplace.backend.dto.request.TicketReplyRequest;
import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.dto.response.TicketResponse;
import com.marketplace.backend.entity.TicketCategory;
import com.marketplace.backend.entity.TicketPriority;
import com.marketplace.backend.entity.TicketStatus;
import com.marketplace.backend.service.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Support tickets. /api/support/** is open to any logged-in user (buyer or seller);
// /api/admin/** is ADMIN-only in SecurityConfig.
@RestController
@RequiredArgsConstructor
public class SupportController {

    private final SupportTicketService ticketService;

    // ---- requester ----

    @PostMapping("/api/support/tickets")
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketCreateRequest req) {
        return ResponseEntity.ok(ticketService.create(req));
    }

    // active=true: still being handled; false: closed
    @GetMapping("/api/support/tickets")
    public ResponseEntity<PageResponse<TicketResponse>> mine(@RequestParam(defaultValue = "true") boolean active,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ticketService.mine(active, page, size));
    }

    @GetMapping("/api/support/tickets/{id}")
    public ResponseEntity<TicketResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.myTicket(id));
    }

    @PostMapping("/api/support/tickets/{id}/replies")
    public ResponseEntity<TicketResponse> reply(@PathVariable Long id, @Valid @RequestBody TicketReplyRequest req) {
        return ResponseEntity.ok(ticketService.reply(id, req));
    }

    @PostMapping("/api/support/tickets/{id}/close")
    public ResponseEntity<TicketResponse> close(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.close(id));
    }

    // body: { "rating": 5, "comment": "..." }
    @PostMapping("/api/support/tickets/{id}/rating")
    public ResponseEntity<TicketResponse> rate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object rating = body.get("rating");
        return ResponseEntity.ok(ticketService.rate(id,
                rating instanceof Number n ? n.intValue() : null, (String) body.get("comment")));
    }

    // ---- support staff (admin) ----

    @GetMapping("/api/admin/support/tickets")
    public ResponseEntity<PageResponse<TicketResponse>> adminList(@RequestParam(required = false) TicketStatus status,
                                                                  @RequestParam(required = false) TicketCategory category,
                                                                  @RequestParam(required = false) String q,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ticketService.forAdmin(status, category, q, page, size));
    }

    @GetMapping("/api/admin/support/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(ticketService.stats());
    }

    @GetMapping("/api/admin/support/tickets/{id}")
    public ResponseEntity<TicketResponse> adminDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.adminDetail(id));
    }

    @PostMapping("/api/admin/support/tickets/{id}/replies")
    public ResponseEntity<TicketResponse> adminReply(@PathVariable Long id, @Valid @RequestBody TicketReplyRequest req) {
        return ResponseEntity.ok(ticketService.adminReply(id, req));
    }

    // body: { "status": "RESOLVED", "priority": "HIGH" } (either may be omitted)
    @PatchMapping("/api/admin/support/tickets/{id}")
    public ResponseEntity<TicketResponse> adminUpdate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        TicketStatus status = body.get("status") != null ? TicketStatus.valueOf(body.get("status")) : null;
        TicketPriority priority = body.get("priority") != null ? TicketPriority.valueOf(body.get("priority")) : null;
        return ResponseEntity.ok(ticketService.adminUpdate(id, status, priority));
    }
}
