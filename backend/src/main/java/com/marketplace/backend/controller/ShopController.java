package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.ShopPolicyRequest;
import com.marketplace.backend.dto.request.ShopRequest;
import com.marketplace.backend.dto.request.VerificationRequest;
import com.marketplace.backend.dto.response.AuthResponse;
import com.marketplace.backend.dto.response.ShopPublicResponse;
import com.marketplace.backend.dto.response.ShopResponse;
import com.marketplace.backend.dto.response.VerificationResponse;
import com.marketplace.backend.service.ShopService;
import com.marketplace.backend.service.ShopVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final ShopVerificationService verificationService;

    // ---- seller: profile ----

    @GetMapping("/api/seller/shop")
    public ResponseEntity<ShopResponse> myShop() {
        return ResponseEntity.ok(shopService.getMyShop());
    }

    @PostMapping("/api/seller/shop")
    public ResponseEntity<ShopResponse> createOrUpdate(@Valid @RequestBody ShopRequest request) {
        return ResponseEntity.ok(shopService.createOrUpdate(request));
    }

    // Shopee-style seller onboarding: any logged-in BUYER can apply here to
    // become a SELLER (creates their Shop + upgrades their role), separate
    // from the normal /api/auth/register flow.
    @PostMapping("/api/seller/apply")
    public ResponseEntity<AuthResponse> becomeSeller(@Valid @RequestBody ShopRequest request) {
        return ResponseEntity.ok(shopService.becomeSeller(request));
    }

    // ---- seller: policies (returns + shipping) ----

    // REJECTED -> PENDING after the seller fixed the profile
    @PostMapping("/api/seller/shop/resubmit")
    public ResponseEntity<ShopResponse> resubmit() {
        return ResponseEntity.ok(shopService.resubmit());
    }

    @PutMapping("/api/seller/shop/policies")
    public ResponseEntity<ShopResponse> updatePolicies(@Valid @RequestBody ShopPolicyRequest request) {
        return ResponseEntity.ok(shopService.updatePolicies(request));
    }

    // ---- seller: verification ----

    // 204 when the shop never applied
    @GetMapping("/api/seller/verification")
    public ResponseEntity<VerificationResponse> myVerification() {
        VerificationResponse v = verificationService.mine();
        return v == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(v);
    }

    @PostMapping("/api/seller/verification")
    public ResponseEntity<VerificationResponse> submitVerification(@Valid @RequestBody VerificationRequest request) {
        return ResponseEntity.ok(verificationService.submit(request));
    }

    // ---- public ----

    @GetMapping("/api/shops/{id}")
    public ResponseEntity<ShopPublicResponse> publicShop(@PathVariable Long id) {
        return ResponseEntity.ok(shopService.publicShop(id));
    }

    // [{ code, name, methods }] for the shipping policy form
    @GetMapping("/api/shops/carriers")
    public ResponseEntity<List<Map<String, Object>>> carriers() {
        return ResponseEntity.ok(ShopService.carrierOptions());
    }

    // ---- admin: verification review ----

    @GetMapping("/api/admin/verifications")
    public ResponseEntity<List<VerificationResponse>> verifications(@RequestParam(defaultValue = "true") boolean pending) {
        return ResponseEntity.ok(verificationService.forAdmin(pending));
    }

    @PostMapping("/api/admin/verifications/{id}/approve")
    public ResponseEntity<VerificationResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(verificationService.approve(id));
    }

    // body: { "reason": "Ảnh CCCD bị mờ" }
    @PostMapping("/api/admin/verifications/{id}/reject")
    public ResponseEntity<VerificationResponse> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(verificationService.reject(id, body.get("reason")));
    }
}
