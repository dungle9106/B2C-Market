package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.service.reviewhub.ReviewHubService;
import com.marketplace.backend.service.reviewhub.ReviewTextAnalyzer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

// "Trung tâm đánh giá": the seller's review hub (/api/seller/review-hub/**) and the platform's model (/api/admin/review-ai/**)
@RestController
@RequiredArgsConstructor
public class ReviewHubController {

    private final ReviewHubService hub;

    // ---- dashboard ----

    @GetMapping("/api/seller/review-hub/overview")
    public ResponseEntity<Map<String, Object>> overview(@RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(hub.overview(days));
    }

    @GetMapping("/api/seller/review-hub/issue-types")
    public ResponseEntity<List<Map<String, String>>> issueTypes() {
        return ResponseEntity.ok(ReviewTextAnalyzer.ISSUES.stream().map(i -> Map.of("key", i.key(), "label", i.label(), "fix", i.fix())).toList());
    }

    // ---- inbox: source, rating, maxRating, sentiment, issue, level, status (OPEN|NEW|DRAFTED|REPLIED|IGNORED),
    //      anomaly, productId, overdue, from, q, sort (priority|newest|oldest|rating|anomaly) ----

    @GetMapping("/api/seller/review-hub/items")
    public ResponseEntity<PageResponse<Map<String, Object>>> items(@RequestParam Map<String, String> filters,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(hub.list(filters, page, size));
    }

    @GetMapping("/api/seller/review-hub/items/{id}")
    public ResponseEntity<Map<String, Object>> item(@PathVariable Long id) {
        return ResponseEntity.ok(hub.get(id));
    }

    @PostMapping("/api/seller/review-hub/items/{id}/draft")
    public ResponseEntity<Map<String, Object>> draft(@PathVariable Long id) {
        return ResponseEntity.ok(hub.draft(id));
    }

    // body: { text }
    @PutMapping("/api/seller/review-hub/items/{id}/draft")
    public ResponseEntity<Map<String, Object>> saveDraft(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(hub.saveDraft(id, body.get("text")));
    }

    // body: { text } - the approved reply
    @PostMapping("/api/seller/review-hub/items/{id}/reply")
    public ResponseEntity<Map<String, Object>> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(hub.reply(id, body.get("text")));
    }

    @PostMapping("/api/seller/review-hub/items/{id}/ignore")
    public ResponseEntity<Map<String, Object>> ignore(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(hub.ignore(id, body == null ? null : body.get("note")));
    }

    @PostMapping("/api/seller/review-hub/items/{id}/reopen")
    public ResponseEntity<Map<String, Object>> reopen(@PathVariable Long id) {
        return ResponseEntity.ok(hub.reopen(id));
    }

    // body: { issues: [keys], note? } - the seller's correction (also a training label)
    @PutMapping("/api/seller/review-hub/items/{id}/issues")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> issues(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object list = body.get("issues");
        if (list != null && !(list instanceof List)) throw new ApiException("issues phải là danh sách", HttpStatus.BAD_REQUEST);
        return ResponseEntity.ok(hub.setIssues(id, (List<String>) list, (String) body.get("note")));
    }

    // body: { ids?: [] } - empty: the most urgent unanswered reviews (max 20)
    @PostMapping("/api/seller/review-hub/drafts")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> bulkDraft(@RequestBody(required = false) Map<String, Object> body) {
        List<Number> ids = body == null ? null : (List<Number>) body.get("ids");
        return ResponseEntity.ok(hub.bulkDraft(ids == null ? null : ids.stream().map(Number::longValue).toList()));
    }

    // ---- bringing reviews in ----

    @PostMapping("/api/seller/review-hub/sync")
    public ResponseEntity<Map<String, Object>> sync() {
        return ResponseEntity.ok(hub.sync());
    }

    @PostMapping(value = "/api/seller/review-hub/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importFile(@RequestParam("file") MultipartFile file, @RequestParam String source) throws Exception {
        if (file.isEmpty()) throw new ApiException("File trống", HttpStatus.BAD_REQUEST);
        if (file.getSize() > 10 * 1024 * 1024) throw new ApiException("File tối đa 10 MB", HttpStatus.BAD_REQUEST);
        return ResponseEntity.ok(hub.importFile(file.getOriginalFilename(), file.getBytes(), source));
    }

    // body: { source, items: [{ externalId?, rating, content, product, reviewer?, time?, order?, media?, reply? }] }
    @PostMapping("/api/seller/review-hub/ingest")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody Map<String, Object> body) {
        Object items = body.get("items");
        if (!(items instanceof List)) throw new ApiException("items phải là danh sách", HttpStatus.BAD_REQUEST);
        return ResponseEntity.ok(hub.ingest((String) body.get("source"), (List<Map<String, Object>>) items));
    }

    @GetMapping("/api/seller/review-hub/import-template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau-nhap-danh-gia.csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(hub.template());
    }

    @PostMapping("/api/seller/review-hub/reanalyze")
    public ResponseEntity<Map<String, Object>> reanalyze() {
        return ResponseEntity.ok(hub.reanalyze());
    }

    // ---- recurring issues & anomalies ----

    @GetMapping("/api/seller/review-hub/issues")
    public ResponseEntity<Map<String, Object>> recurring(@RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(hub.recurring(days));
    }

    // body: { key, issue, days? }
    @PostMapping("/api/seller/review-hub/issues/root-cause")
    public ResponseEntity<Map<String, Object>> rootCause(@RequestBody Map<String, Object> body) {
        Object days = body.get("days");
        return ResponseEntity.ok(hub.rootCause((String) body.get("key"), (String) body.get("issue"), days instanceof Number n ? n.intValue() : null));
    }

    @GetMapping("/api/seller/review-hub/anomalies")
    public ResponseEntity<PageResponse<Map<String, Object>>> anomalies(@RequestParam(required = false) String status,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(hub.anomalies(status, page, size));
    }

    // body: { action: OK | REPORT | RESET, note? }
    @PostMapping("/api/seller/review-hub/items/{id}/anomaly")
    public ResponseEntity<Map<String, Object>> anomaly(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(hub.decideAnomaly(id, body.get("action"), body.get("note")));
    }

    // ---- the platform's classification model (FastAPI service) ----

    @GetMapping("/api/admin/review-ai")
    public ResponseEntity<Map<String, Object>> serviceStatus() {
        return ResponseEntity.ok(hub.serviceStatus());
    }

    @PostMapping("/api/admin/review-ai/train")
    public ResponseEntity<Map<String, Object>> train() {
        return ResponseEntity.ok(hub.train());
    }
}
