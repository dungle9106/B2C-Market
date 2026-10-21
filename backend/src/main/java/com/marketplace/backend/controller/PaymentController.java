package com.marketplace.backend.controller;

import com.marketplace.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Simulated payment gateway page for one checkout (all its orders together).
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final com.marketplace.backend.service.VnpayService vnpay;

    // ---- VNPay (real gateway) ----

    // which real gateways checkout may offer (public)
    @GetMapping("/methods")
    public ResponseEntity<Map<String, Object>> methods() {
        return ResponseEntity.ok(Map.of("vnpay", vnpay.available()));
    }

    // the signed VNPay URL to send the buyer to
    @PostMapping("/{checkoutCode}/vnpay")
    public ResponseEntity<Map<String, Object>> vnpayUrl(@PathVariable String checkoutCode) {
        return ResponseEntity.ok(vnpay.payUrl(checkoutCode));
    }

    // the buyer's browser comes back with VNPay's (signed) query string; the frontend forwards it here (public)
    @GetMapping("/vnpay/return")
    public ResponseEntity<Map<String, Object>> vnpayReturn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(vnpay.handleReturn(params));
    }

    // VNPay's server-to-server confirmation (public; must answer {RspCode, Message})
    @GetMapping("/vnpay/ipn")
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(vnpay.handleIpn(params));
    }

    @GetMapping("/{checkoutCode}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String checkoutCode) {
        return ResponseEntity.ok(paymentService.get(checkoutCode));
    }

    // CARD: { cardNumber, cardHolder, expiry: "MM/YY", cvv }; wallets / bank transfer: {}
    @PostMapping("/{checkoutCode}/pay")
    public ResponseEntity<Map<String, Object>> pay(@PathVariable String checkoutCode,
                                                   @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(paymentService.pay(checkoutCode, body == null ? Map.of() : body));
    }

    @PostMapping("/{checkoutCode}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String checkoutCode) {
        return ResponseEntity.ok(paymentService.cancel(checkoutCode));
    }
}
