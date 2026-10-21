package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.repository.ShopVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Duyet dang ky shop moi": with the platform setting shop.requireApproval on, a new shop starts PENDING
 * (the seller can already set it up and add products) and becomes visible once an admin approves it.
 * Admins can also reject (with a reason; the seller fixes the profile and submits again), suspend a live
 * shop for a violation, and reinstate it.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AdminShopService {

    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final ShopVerificationRepository verificationRepository;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;
    private final PlatformSettingService settings;

    /** Buyers see a shop only when it is approved and its seller's account is usable. */
    public static void syncVisibility(Shop s) {
        User seller = s.getSeller();
        s.setActive(s.getStatus() == ShopStatus.APPROVED && seller.isEnabled() && seller.getDeletedAt() == null);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(String status, String q, int page, int size) {
        ShopStatus st = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            try {
                st = ShopStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw bad("Trạng thái không hợp lệ");
            }
        }
        // pending shops: oldest first (first come, first served); otherwise newest first
        Sort sort = st == ShopStatus.PENDING ? Sort.by("createdAt").ascending() : Sort.by("id").descending();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
        return PageResponse.of(shopRepository.searchForAdmin(st, q == null ? "" : q.trim().toLowerCase(), pageable), this::view);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> counts() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (ShopStatus s : ShopStatus.values()) m.put(s.name(), shopRepository.countByStatus(s));
        m.put("requireApproval", settings.requireShopApproval());
        return m;
    }

    public Map<String, Object> approve(Long id) {
        Shop s = find(id);
        if (s.getStatus() != ShopStatus.PENDING && s.getStatus() != ShopStatus.REJECTED) throw bad("Chỉ duyệt được shop đang chờ duyệt hoặc đã bị từ chối");
        change(s, ShopStatus.APPROVED, null);
        notifySeller(s, "Shop của bạn đã được duyệt 🎉", "\"" + s.getName() + "\" đã hiển thị với người mua trên Shoppi. Chúc bạn bán hàng thuận lợi!");
        auditService.log("APPROVE_SHOP", "SHOP", s.getId(), s.getName(), "Duyệt shop");
        return view(s);
    }

    public Map<String, Object> reject(Long id, String reason) {
        Shop s = find(id);
        if (s.getStatus() != ShopStatus.PENDING) throw bad("Chỉ từ chối được shop đang chờ duyệt");
        String r = reason(reason);
        change(s, ShopStatus.REJECTED, r);
        notifySeller(s, "Đăng ký shop chưa được duyệt", "Lý do: " + r + ". Bạn hãy cập nhật hồ sơ shop rồi gửi duyệt lại.");
        auditService.log("REJECT_SHOP", "SHOP", s.getId(), s.getName(), r);
        return view(s);
    }

    public Map<String, Object> suspend(Long id, String reason) {
        Shop s = find(id);
        if (s.getStatus() != ShopStatus.APPROVED) throw bad("Chỉ đình chỉ được shop đang hoạt động");
        String r = reason(reason);
        change(s, ShopStatus.SUSPENDED, r);
        notifySeller(s, "Shop của bạn bị tạm đình chỉ", "Lý do: " + r + ". Sản phẩm của shop tạm thời không hiển thị. Liên hệ Trung tâm hỗ trợ để được giải quyết.");
        auditService.log("SUSPEND_SHOP", "SHOP", s.getId(), s.getName(), r);
        return view(s);
    }

    public Map<String, Object> reinstate(Long id) {
        Shop s = find(id);
        if (s.getStatus() != ShopStatus.SUSPENDED) throw bad("Shop không ở trạng thái bị đình chỉ");
        if (s.getSeller().getDeletedAt() != null) throw bad("Tài khoản người bán đã bị xóa");
        change(s, ShopStatus.APPROVED, null);
        notifySeller(s, "Shop của bạn đã được mở lại", "\"" + s.getName() + "\" đã hiển thị trở lại với người mua.");
        auditService.log("REINSTATE_SHOP", "SHOP", s.getId(), s.getName(), "Mở lại shop");
        return view(s);
    }

    // ---- seller side ----

    /** A new shop: approved at once, or PENDING (hidden) when the platform requires approval. */
    public void onNewShop(Shop s) {
        boolean review = settings.requireShopApproval();
        s.setStatus(review ? ShopStatus.PENDING : ShopStatus.APPROVED);
        s.setStatusChangedAt(LocalDateTime.now());
        syncVisibility(s);
        if (review) {
            auditService.notifyAdmins(AdminPermission.SHOPS, "Shop mới chờ duyệt", "\"" + s.getName() + "\" vừa đăng ký bán hàng", "/admin/shops?status=PENDING");
        }
    }

    /** The seller fixed a rejected profile and asks again. */
    public void resubmit(Shop s) {
        if (s.getStatus() != ShopStatus.REJECTED) throw bad("Shop không ở trạng thái bị từ chối");
        change(s, ShopStatus.PENDING, null);
        auditService.notifyAdmins(AdminPermission.SHOPS, "Shop gửi duyệt lại", "\"" + s.getName() + "\" đã cập nhật hồ sơ và gửi duyệt lại", "/admin/shops?status=PENDING");
    }

    // ---- helpers ----

    private void change(Shop s, ShopStatus status, String reason) {
        s.setStatus(status);
        s.setStatusReason(reason);
        s.setStatusChangedAt(LocalDateTime.now());
        syncVisibility(s);
    }

    private void notifySeller(Shop s, String title, String content) {
        notificationService.notify(s.getSeller().getId(), NotificationType.SYSTEM, title, content, "/seller/shop");
    }

    private Map<String, Object> view(Shop s) {
        User seller = s.getSeller();
        Map<String, Object> m = AdminUserService.map("id", s.getId(), "name", s.getName(), "logoUrl", s.getLogoUrl(),
                "description", s.getDescription(), "province", s.getProvince(), "pickupAddress", s.getPickupAddress(),
                "pickupPhone", s.getPickupPhone(), "status", s.getStatus().name(), "statusReason", s.getStatusReason(),
                "statusChangedAt", s.getStatusChangedAt(), "active", s.isActive(), "verified", s.isVerified(), "createdAt", s.getCreatedAt(),
                "products", productRepository.countByShopId(s.getId()),
                "verification", verificationRepository.findFirstByShopIdOrderByIdDesc(s.getId()).map(v -> v.getStatus().name()).orElse(null),
                "seller", AdminUserService.map("id", seller.getId(), "fullName", seller.getFullName(), "email", seller.getEmail(),
                        "phone", seller.getPhone(), "enabled", seller.isEnabled(), "deleted", seller.getDeletedAt() != null));
        // what is missing from the profile, to help the admin decide
        List<String> missing = new java.util.ArrayList<>();
        if (s.getLogoUrl() == null) missing.add("logo");
        if (s.getDescription() == null || s.getDescription().isBlank()) missing.add("giới thiệu");
        if (productRepository.countByShopId(s.getId()) == 0) missing.add("sản phẩm");
        m.put("missing", missing);
        return m;
    }

    private Shop find(Long id) {
        return shopRepository.findById(id).orElseThrow(() -> new ApiException("Shop không tồn tại", HttpStatus.NOT_FOUND));
    }

    private static String reason(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.length() < 5) throw bad("Vui lòng nhập lý do (ít nhất 5 ký tự)");
        if (r.length() > 500) throw bad("Lý do tối đa 500 ký tự");
        return r;
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
