package com.marketplace.backend.controller;

import com.marketplace.backend.service.BannerService;
import com.marketplace.backend.service.CampaignService;
import com.marketplace.backend.service.PlatformFlashSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// "Marketing toan san": promotion programmes, home banners and Shoppi Flash Sale slots.
// Admin paths need PERM_VOUCHERS ("Marketing toan san"), seller paths ROLE_SELLER, the others are public.
@RestController
@RequiredArgsConstructor
public class PlatformMarketingController {

    private final CampaignService campaignService;
    private final BannerService bannerService;
    private final PlatformFlashSaleService flashSaleService;

    // ==================== admin: programmes ====================

    @GetMapping("/api/admin/campaigns")
    public ResponseEntity<List<Map<String, Object>>> campaigns() {
        return ResponseEntity.ok(campaignService.list());
    }

    // body: { name, slug?, tagline?, description?, bannerUrl?, themeColor?, startAt, endAt, active? }
    @PostMapping("/api/admin/campaigns")
    public ResponseEntity<Map<String, Object>> createCampaign(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(campaignService.create(body));
    }

    @PutMapping("/api/admin/campaigns/{id}")
    public ResponseEntity<Map<String, Object>> updateCampaign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(campaignService.update(id, body));
    }

    @PostMapping("/api/admin/campaigns/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.toggle(id));
    }

    @DeleteMapping("/api/admin/campaigns/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable Long id) {
        campaignService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== admin: banners ====================

    @GetMapping("/api/admin/banners")
    public ResponseEntity<Map<String, Object>> banners() {
        return ResponseEntity.ok(bannerService.list());
    }

    // body: { title, imageUrl, linkUrl?, placement: HOME_CAROUSEL|HOME_SIDE, startAt?, endAt?, campaignId?, active? }
    @PostMapping("/api/admin/banners")
    public ResponseEntity<Map<String, Object>> createBanner(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(bannerService.create(body));
    }

    @PutMapping("/api/admin/banners/{id}")
    public ResponseEntity<Map<String, Object>> updateBanner(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(bannerService.update(id, body));
    }

    @PostMapping("/api/admin/banners/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleBanner(@PathVariable Long id) {
        return ResponseEntity.ok(bannerService.toggle(id));
    }

    @DeleteMapping("/api/admin/banners/{id}")
    public ResponseEntity<Void> deleteBanner(@PathVariable Long id) {
        bannerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // body: { placement, ids: [...] } in display order
    @SuppressWarnings("unchecked")
    @PostMapping("/api/admin/banners/reorder")
    public ResponseEntity<Void> reorderBanners(@RequestBody Map<String, Object> body) {
        List<Number> ids = (List<Number>) body.get("ids");
        bannerService.reorder((String) body.get("placement"), ids == null ? null : ids.stream().map(Number::longValue).toList());
        return ResponseEntity.noContent().build();
    }

    // ==================== admin: Shoppi Flash Sale slots ====================

    @GetMapping("/api/admin/flash-sales")
    public ResponseEntity<List<Map<String, Object>>> flashEvents() {
        return ResponseEntity.ok(flashSaleService.events());
    }

    // body: { name, startAt, endAt, registrationDeadline, minDiscountPercent?, maxProductsPerShop?, campaignId? }
    @PostMapping("/api/admin/flash-sales")
    public ResponseEntity<Map<String, Object>> createFlashEvent(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(flashSaleService.create(body));
    }

    @PutMapping("/api/admin/flash-sales/{id}")
    public ResponseEntity<Map<String, Object>> updateFlashEvent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(flashSaleService.update(id, body));
    }

    // ?status=PENDING|APPROVED|REJECTED|WITHDRAWN|ALL
    @GetMapping("/api/admin/flash-sales/{id}")
    public ResponseEntity<Map<String, Object>> flashEvent(@PathVariable Long id, @RequestParam(required = false) String status) {
        return ResponseEntity.ok(flashSaleService.detail(id, status));
    }

    // body: { reason }
    @PostMapping("/api/admin/flash-sales/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelFlashEvent(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(flashSaleService.cancel(id, body.get("reason")));
    }

    @PostMapping("/api/admin/flash-sales/{id}/approve-all")
    public ResponseEntity<Map<String, Object>> approveAll(@PathVariable Long id) {
        return ResponseEntity.ok(flashSaleService.approveAll(id));
    }

    @PostMapping("/api/admin/flash-sales/registrations/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveRegistration(@PathVariable Long id) {
        return ResponseEntity.ok(flashSaleService.approve(id));
    }

    // body: { reason }
    @PostMapping("/api/admin/flash-sales/registrations/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectRegistration(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(flashSaleService.reject(id, body.get("reason")));
    }

    // ==================== seller ====================

    @GetMapping("/api/seller/flash-events")
    public ResponseEntity<Map<String, Object>> sellerFlashEvents() {
        return ResponseEntity.ok(flashSaleService.forSeller());
    }

    // body: { items: [{ productId, flashPrice, flashStock?, maxPerBuyer? }] }
    @SuppressWarnings("unchecked")
    @PostMapping("/api/seller/flash-events/{id}/registrations")
    public ResponseEntity<List<Map<String, Object>>> register(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(flashSaleService.register(id, (List<Map<String, Object>>) body.get("items")));
    }

    @PostMapping("/api/seller/flash-events/registrations/{id}/withdraw")
    public ResponseEntity<Void> withdraw(@PathVariable Long id) {
        flashSaleService.withdraw(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== public ====================

    @GetMapping("/api/banners")
    public ResponseEntity<Map<String, Object>> showingBanners() {
        return ResponseEntity.ok(bannerService.showing());
    }

    @PostMapping("/api/banners/{id}/click")
    public ResponseEntity<Map<String, Object>> clickBanner(@PathVariable Long id) {
        return ResponseEntity.ok(bannerService.click(id));
    }

    @GetMapping("/api/campaigns")
    public ResponseEntity<List<Map<String, Object>>> visibleCampaigns() {
        return ResponseEntity.ok(campaignService.visible());
    }

    @GetMapping("/api/campaigns/{slug}")
    public ResponseEntity<Map<String, Object>> campaign(@PathVariable String slug) {
        return ResponseEntity.ok(campaignService.landing(slug));
    }

    // the running Shoppi Flash Sale slot and the next ones
    @GetMapping("/api/flash-sale/slots")
    public ResponseEntity<List<Map<String, Object>>> flashSlots(@RequestParam(defaultValue = "12") int limit) {
        return ResponseEntity.ok(flashSaleService.publicSlots(limit));
    }

    @GetMapping("/api/flash-sale/slots/{id}")
    public ResponseEntity<Map<String, Object>> flashSlot(@PathVariable Long id, @RequestParam(defaultValue = "60") int limit) {
        return ResponseEntity.ok(flashSaleService.publicSlot(id, limit));
    }
}
