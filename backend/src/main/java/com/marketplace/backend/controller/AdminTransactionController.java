package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.DisputeMessage;
import com.marketplace.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Admin: orders and disputes (PERM_ORDERS); refunds, seller settlements and payment reconciliation (PERM_FINANCE).
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminTransactionController {

    private final AdminOrderService orderService;
    private final DisputeService disputeService;
    private final RefundService refundService;
    private final SettlementService settlementService;
    private final ReconciliationService reconciliationService;

    // ==== orders ====

    // ?status=ALL|LATE|<OrderStatus>&method=&shopId=&q=&from=&to=
    @GetMapping("/orders")
    public ResponseEntity<PageResponse<Map<String, Object>>> orders(@RequestParam(required = false) String status,
                                                                     @RequestParam(required = false) String method,
                                                                     @RequestParam(required = false) Long shopId,
                                                                     @RequestParam(required = false) String q,
                                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.list(status, method, shopId, q, from, to, page, size));
    }

    @GetMapping("/orders/summary")
    public ResponseEntity<Map<String, Object>> orderSummary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(orderService.summary(from, to));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Map<String, Object>> order(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.detail(id));
    }

    // body: { reason }
    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(orderService.cancel(id, body.get("reason")));
    }

    // body: { amount, note } - a manual / goodwill refund (PERM_FINANCE, see SecurityConfig)
    @PostMapping("/orders/{id}/refund")
    public ResponseEntity<Map<String, Object>> manualRefund(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(refundService.manual(id, money(body.get("amount")), (String) body.get("note")));
    }

    // ==== disputes ====

    // ?status=OPEN|RESOLVED|ALL
    @GetMapping("/disputes")
    public ResponseEntity<PageResponse<Map<String, Object>>> disputes(@RequestParam(defaultValue = "OPEN") String status,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(disputeService.list(status, page, size));
    }

    @GetMapping("/disputes/counts")
    public ResponseEntity<Map<String, Object>> disputeCounts() {
        return ResponseEntity.ok(disputeService.counts());
    }

    @GetMapping("/disputes/{id}")
    public ResponseEntity<Map<String, Object>> dispute(@PathVariable Long id) {
        return ResponseEntity.ok(disputeService.detail(id));
    }

    @GetMapping("/disputes/{id}/messages")
    public ResponseEntity<List<Map<String, Object>>> disputeMessages(@PathVariable Long id) {
        return ResponseEntity.ok(disputeService.messages(id, DisputeMessage.Side.ADMIN));
    }

    // body: { content, attachments: [] }
    @PostMapping("/disputes/{id}/messages")
    public ResponseEntity<Map<String, Object>> postDisputeMessage(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(disputeService.post(id, DisputeMessage.Side.ADMIN, (String) body.get("content"), strings(body.get("attachments"))));
    }

    // body: { decision: REFUND_FULL|REFUND_PARTIAL|REJECT, amount?, note }
    @PostMapping("/disputes/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveDispute(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(disputeService.resolve(id, (String) body.get("decision"), money(body.get("amount")), (String) body.get("note")));
    }

    // ==== refunds ====

    // ?status=PENDING|COMPLETED|FAILED|ALL&q=
    @GetMapping("/refunds")
    public ResponseEntity<PageResponse<Map<String, Object>>> refunds(@RequestParam(required = false) String status,
                                                                      @RequestParam(required = false) String q,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(refundService.list(status, q, page, size));
    }

    @GetMapping("/refunds/counts")
    public ResponseEntity<Map<String, Object>> refundCounts() {
        return ResponseEntity.ok(refundService.counts());
    }

    // body: { reference, note? }
    @PostMapping("/refunds/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeRefund(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(refundService.complete(id, body.get("reference"), body.get("note")));
    }

    // body: { reason }
    @PostMapping("/refunds/{id}/fail")
    public ResponseEntity<Map<String, Object>> failRefund(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(refundService.fail(id, body.get("reason")));
    }

    // ==== seller settlements ====

    @GetMapping("/settlements/preview")
    public ResponseEntity<Map<String, Object>> settlementPreview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(settlementService.preview(to));
    }

    // body: { to?: "yyyy-MM-dd", shopIds?: [] }
    @SuppressWarnings("unchecked")
    @PostMapping("/settlements")
    public ResponseEntity<List<Map<String, Object>>> createSettlements(@RequestBody Map<String, Object> body) {
        List<Number> ids = (List<Number>) body.get("shopIds");
        Object to = body.get("to");
        return ResponseEntity.ok(settlementService.create(ids == null ? null : ids.stream().map(Number::longValue).toList(),
                to == null || to.toString().isBlank() ? null : LocalDate.parse(to.toString())));
    }

    // ?status=PENDING_PAYMENT|PAID|ALL&shopId=
    @GetMapping("/settlements")
    public ResponseEntity<PageResponse<Map<String, Object>>> settlements(@RequestParam(required = false) String status,
                                                                          @RequestParam(required = false) Long shopId,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(settlementService.list(status, shopId, page, size));
    }

    @GetMapping("/settlements/counts")
    public ResponseEntity<Map<String, Object>> settlementCounts() {
        return ResponseEntity.ok(settlementService.counts());
    }

    @GetMapping("/settlements/{id}")
    public ResponseEntity<Map<String, Object>> settlement(@PathVariable Long id) {
        return ResponseEntity.ok(settlementService.detail(id));
    }

    // body: { reference }
    @PostMapping("/settlements/{id}/pay")
    public ResponseEntity<Map<String, Object>> paySettlement(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(settlementService.pay(id, body.get("reference")));
    }

    // ==== payment reconciliation ====

    @GetMapping("/reconciliation")
    public ResponseEntity<Map<String, Object>> reconciliation(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reconciliationService.report(from, to));
    }

    static BigDecimal money(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static List<String> strings(Object v) {
        return v instanceof List<?> l ? ((List<Object>) l).stream().map(String::valueOf).toList() : List.of();
    }
}
