package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// "Van hanh he thong" (PERM_SETTINGS): AI keys & LLM costs, monitoring (health, traffic, errors, logs),
// platform policies. Public policy pages are served under /api/policies.
@RestController
@RequiredArgsConstructor
public class AdminSystemController {

    private final AiOpsService aiOps;
    private final AIService aiService;
    private final SystemHealthService health;
    private final RequestMetricsService metrics;
    private final ErrorMonitorService errors;
    private final LogBufferService logs;
    private final PolicyService policies;
    private final EmailService email;
    private final AdminSecurityService security;
    private final VnpayService vnpay;
    private final GhnService ghn;
    private final com.marketplace.backend.service.search.SemanticSearchService semanticSearch;

    // ==================== AI ====================

    // semantic search index (product embeddings, pgvector)
    @GetMapping("/api/admin/system/ai/embeddings")
    public ResponseEntity<Map<String, Object>> embeddings() {
        return ResponseEntity.ok(semanticSearch.status());
    }

    @PostMapping("/api/admin/system/ai/embeddings/reindex")
    public ResponseEntity<Map<String, Object>> reindexEmbeddings() {
        return ResponseEntity.ok(semanticSearch.reindex());
    }

    @GetMapping("/api/admin/system/ai/config")
    public ResponseEntity<Map<String, Object>> aiConfig() {
        return ResponseEntity.ok(aiOps.config());
    }

    @PutMapping("/api/admin/system/ai/config")
    public ResponseEntity<Map<String, Object>> updateAiConfig(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(aiOps.update(body));
    }

    @PostMapping("/api/admin/system/ai/test")
    public ResponseEntity<Map<String, Object>> testAi() {
        return ResponseEntity.ok(aiService.testConnection());
    }

    @GetMapping("/api/admin/system/ai/usage")
    public ResponseEntity<Map<String, Object>> aiUsage(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(aiOps.usage(from, to));
    }

    // ?status=SUCCESS|FAILED|BLOCKED|ALL&feature=
    @GetMapping("/api/admin/system/ai/calls")
    public ResponseEntity<PageResponse<Map<String, Object>>> aiCalls(@RequestParam(required = false) String status,
                                                                      @RequestParam(required = false) String feature,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(aiOps.calls(status, feature, page, size));
    }

    // ==================== monitoring ====================

    @GetMapping("/api/admin/system/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(health.health());
    }

    // ?minutes=60 (5 .. 1440)
    @GetMapping("/api/admin/system/metrics")
    public ResponseEntity<Map<String, Object>> metrics(@RequestParam(defaultValue = "60") int minutes,
                                                       @RequestParam(required = false) String q) {
        return ResponseEntity.ok(metrics.snapshot(minutes, q));
    }

    // ?level=INFO|WARN|ERROR&q=&limit=200
    @GetMapping("/api/admin/system/logs")
    public ResponseEntity<Map<String, Object>> logs(@RequestParam(defaultValue = "INFO") String level,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(logs.query(level, q, limit));
    }

    // ?status=OPEN|RESOLVED|IGNORED|ALL&q=
    @GetMapping("/api/admin/system/errors")
    public ResponseEntity<PageResponse<Map<String, Object>>> errors(@RequestParam(defaultValue = "OPEN") String status,
                                                                     @RequestParam(required = false) String q,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(errors.list(status, q, page, size));
    }

    /** "Gui loi thu": checks that errors are recorded and the admins alerted, like the test event of a monitoring tool. */
    @PostMapping("/api/admin/system/errors/test")
    public ResponseEntity<Void> testError() {
        throw new IllegalStateException("Lỗi thử nghiệm từ trang Giám sát hệ thống");
    }

    @GetMapping("/api/admin/system/errors/counts")
    public ResponseEntity<Map<String, Object>> errorCounts() {
        return ResponseEntity.ok(errors.counts());
    }

    @GetMapping("/api/admin/system/errors/{id}")
    public ResponseEntity<Map<String, Object>> error(@PathVariable Long id) {
        return ResponseEntity.ok(errors.detail(id));
    }

    // body: { status: RESOLVED|IGNORED|OPEN, note? }
    @PostMapping("/api/admin/system/errors/{id}/status")
    public ResponseEntity<Map<String, Object>> errorStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(errors.setStatus(id, body.get("status"), body.get("note")));
    }

    // ==================== e-mail ====================

    @GetMapping("/api/admin/system/email/config")
    public ResponseEntity<Map<String, Object>> emailConfig() {
        return ResponseEntity.ok(email.config());
    }

    @PutMapping("/api/admin/system/email/config")
    public ResponseEntity<Map<String, Object>> updateEmailConfig(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(email.update(body));
    }

    // body: { to }
    @PostMapping("/api/admin/system/email/test")
    public ResponseEntity<Map<String, Object>> testEmail(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(email.sendTest(body.get("to")));
    }

    // ?status=QUEUED|SENT|FAILED|LOGGED|ALL&type=&q=
    @GetMapping("/api/admin/system/email/messages")
    public ResponseEntity<PageResponse<Map<String, Object>>> emails(@RequestParam(required = false) String status,
                                                                     @RequestParam(required = false) String type,
                                                                     @RequestParam(required = false) String q,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(email.list(status, type, q, page, size));
    }

    @GetMapping("/api/admin/system/email/messages/{id}")
    public ResponseEntity<Map<String, Object>> emailMessage(@PathVariable Long id) {
        return ResponseEntity.ok(email.detail(id));
    }

    @PostMapping("/api/admin/system/email/messages/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryEmail(@PathVariable Long id) {
        return ResponseEntity.ok(email.retry(id));
    }

    // ==================== sign-in security ====================

    // ?hours=24
    @GetMapping("/api/admin/system/security")
    public ResponseEntity<Map<String, Object>> securityOverview(@RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(security.overview(hours));
    }

    // ?failedOnly=true
    @GetMapping("/api/admin/system/security/logins")
    public ResponseEntity<PageResponse<Map<String, Object>>> logins(@RequestParam(defaultValue = "true") boolean failedOnly,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(security.logins(failedOnly, page, size));
    }

    // lifts the automatic lock after too many wrong passwords
    @PostMapping("/api/admin/system/security/unlock/{userId}")
    public ResponseEntity<Void> unlockLogin(@PathVariable Long userId) {
        security.unlock(userId);
        return ResponseEntity.noContent().build();
    }

    // ==================== payment gateway ====================

    @GetMapping("/api/admin/system/payment/vnpay")
    public ResponseEntity<Map<String, Object>> vnpayConfig() {
        return ResponseEntity.ok(vnpay.config());
    }

    // body: { enabled, tmnCode, hashSecret?, payUrl?, apiUrl? }
    @PutMapping("/api/admin/system/payment/vnpay")
    public ResponseEntity<Map<String, Object>> updateVnpay(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(vnpay.update(body));
    }

    // ==================== shipping gateway ====================

    @GetMapping("/api/admin/system/shipping/ghn")
    public ResponseEntity<Map<String, Object>> ghnConfig() {
        return ResponseEntity.ok(ghn.config());
    }

    // body: { enabled, shopId, token?, apiUrl? }
    @PutMapping("/api/admin/system/shipping/ghn")
    public ResponseEntity<Map<String, Object>> updateGhn(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ghn.update(body));
    }

    @PostMapping("/api/admin/system/shipping/ghn/test")
    public ResponseEntity<Map<String, Object>> testGhn() {
        return ResponseEntity.ok(ghn.test());
    }

    // ==================== policies ====================

    @GetMapping("/api/admin/policies")
    public ResponseEntity<List<Map<String, Object>>> adminPolicies() {
        return ResponseEntity.ok(policies.list());
    }

    // body: { title, content, changeNote?, notify? }
    @PostMapping("/api/admin/policies/{key}")
    public ResponseEntity<Map<String, Object>> publish(@PathVariable String key, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(policies.publish(key, (String) body.get("title"), (String) body.get("content"),
                (String) body.get("changeNote"), Boolean.TRUE.equals(body.get("notify"))));
    }

    @GetMapping("/api/policies")
    public ResponseEntity<List<Map<String, Object>>> publicPolicies() {
        return ResponseEntity.ok(policies.list());
    }

    // ?version= (default: the one in force)
    @GetMapping("/api/policies/{key}")
    public ResponseEntity<Map<String, Object>> policy(@PathVariable String key, @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(policies.get(key, version));
    }
}
