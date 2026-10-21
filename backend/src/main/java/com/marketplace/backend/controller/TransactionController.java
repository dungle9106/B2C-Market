package com.marketplace.backend.controller;

import com.marketplace.backend.entity.DisputeMessage;
import com.marketplace.backend.service.DisputeService;
import com.marketplace.backend.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Dispute threads for the buyer and the seller of the order, and the seller's payouts ("Doanh thu & Doi soat").
@RestController
@RequiredArgsConstructor
public class TransactionController {

    private final DisputeService disputeService;
    private final SettlementService settlementService;

    // ---- buyer ----
    @GetMapping("/api/buyer/returns/{id}/messages")
    public ResponseEntity<List<Map<String, Object>>> buyerMessages(@PathVariable Long id) {
        return ResponseEntity.ok(disputeService.messages(id, DisputeMessage.Side.BUYER));
    }

    // body: { content, attachments: [] }
    @PostMapping("/api/buyer/returns/{id}/messages")
    public ResponseEntity<Map<String, Object>> buyerPost(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(disputeService.post(id, DisputeMessage.Side.BUYER, (String) body.get("content"),
                AdminTransactionController.strings(body.get("attachments"))));
    }

    // ---- seller ----
    @GetMapping("/api/seller/returns/{id}/messages")
    public ResponseEntity<List<Map<String, Object>>> sellerMessages(@PathVariable Long id) {
        return ResponseEntity.ok(disputeService.messages(id, DisputeMessage.Side.SELLER));
    }

    @PostMapping("/api/seller/returns/{id}/messages")
    public ResponseEntity<Map<String, Object>> sellerPost(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(disputeService.post(id, DisputeMessage.Side.SELLER, (String) body.get("content"),
                AdminTransactionController.strings(body.get("attachments"))));
    }

    @GetMapping("/api/seller/finance")
    public ResponseEntity<Map<String, Object>> finance() {
        return ResponseEntity.ok(settlementService.mine());
    }

    @GetMapping("/api/seller/finance/settlements/{id}")
    public ResponseEntity<Map<String, Object>> settlement(@PathVariable Long id) {
        return ResponseEntity.ok(settlementService.mineDetail(id));
    }
}
