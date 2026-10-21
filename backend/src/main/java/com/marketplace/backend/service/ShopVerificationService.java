package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.VerificationRequest;
import com.marketplace.backend.dto.response.VerificationResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.repository.ShopVerificationRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * "Xac minh thong tin" (optional, advanced): the seller submits identity and,
 * for a household business or company, business-registration details with
 * scans; an admin approves (shop gets the "Da xac minh" badge) or rejects with a
 * reason (the seller fixes and submits again).
 *
 * Documents are checked by a person: nothing here talks to a government registry.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ShopVerificationService {

    public static final Map<BusinessType, String> TYPE_NAMES = Map.of(
            BusinessType.INDIVIDUAL, "Cá nhân",
            BusinessType.HOUSEHOLD, "Hộ kinh doanh",
            BusinessType.COMPANY, "Doanh nghiệp");

    private final ShopVerificationRepository verificationRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // ==== seller ====

    /** The latest submission, or null if the shop never applied. */
    @Transactional(readOnly = true)
    public VerificationResponse mine() {
        return verificationRepository.findFirstByShopIdOrderByIdDesc(myShop().getId())
                .map(v -> VerificationResponse.from(v, false)).orElse(null);
    }

    public VerificationResponse submit(VerificationRequest req) {
        Shop shop = myShop();
        verificationRepository.findFirstByShopIdOrderByIdDesc(shop.getId()).ifPresent(last -> {
            if (last.getStatus() == VerificationStatus.PENDING) throw bad("Hồ sơ của bạn đang chờ duyệt");
            if (last.getStatus() == VerificationStatus.APPROVED) throw bad("Shop đã được xác minh");
        });

        BusinessType type = req.getBusinessType();
        boolean business = type != BusinessType.INDIVIDUAL;
        String taxCode = blankToNull(req.getTaxCode());
        String license = blankToNull(req.getLicenseNumber());
        if (business && taxCode == null) throw bad("Vui lòng nhập mã số thuế của " + TYPE_NAMES.get(type).toLowerCase());
        if (business && license == null) throw bad("Vui lòng nhập số Giấy chứng nhận đăng ký kinh doanh");
        if (business && blankToNull(req.getBusinessAddress()) == null) throw bad("Vui lòng nhập địa chỉ đăng ký kinh doanh");

        List<String> docs = req.getDocumentUrls() == null ? List.of() : req.getDocumentUrls().stream()
                .filter(u -> u != null && u.startsWith("/uploads/")).distinct().toList(); // only our own uploads
        // CCCD front + back, plus the licence for a business
        int needed = business ? 3 : 2;
        if (docs.size() < needed) {
            throw bad(business ? "Vui lòng tải lên ảnh 2 mặt CCCD và Giấy chứng nhận đăng ký kinh doanh"
                    : "Vui lòng tải lên ảnh 2 mặt CCCD");
        }

        ShopVerification v = verificationRepository.save(ShopVerification.builder()
                .shop(shop)
                .businessType(type)
                .legalName(req.getLegalName().trim())
                .idNumber(req.getIdNumber())
                .taxCode(taxCode)
                .licenseNumber(business ? license : null)
                .businessAddress(blankToNull(req.getBusinessAddress()))
                .documentUrls(new ArrayList<>(docs))
                .status(VerificationStatus.PENDING)
                .build());

        notificationService.notifyAdmins(NotificationType.SYSTEM, "Hồ sơ xác minh shop mới",
                shop.getName() + " (" + TYPE_NAMES.get(type) + ") đang chờ duyệt", "/admin/verifications");
        return VerificationResponse.from(v, false);
    }

    // ==== admin ====

    @Transactional(readOnly = true)
    public List<VerificationResponse> forAdmin(boolean pending) {
        List<ShopVerification> list = pending
                ? verificationRepository.findByStatusOrderBySubmittedAtAsc(VerificationStatus.PENDING)
                : verificationRepository.findTop100ByStatusNotOrderByReviewedAtDesc(VerificationStatus.PENDING);
        return list.stream().map(v -> VerificationResponse.from(v, true)).toList();
    }

    public VerificationResponse approve(Long id) {
        ShopVerification v = pending(id);
        v.setStatus(VerificationStatus.APPROVED);
        reviewed(v);
        Shop shop = v.getShop();
        shop.setVerified(true);
        shop.setVerifiedAt(v.getReviewedAt());
        shop.setBusinessType(v.getBusinessType());
        notificationService.notify(shop.getSeller().getId(), NotificationType.SYSTEM, "Shop đã được xác minh 🎉",
                "Hồ sơ " + TYPE_NAMES.get(v.getBusinessType()).toLowerCase()
                        + " đã được duyệt. Huy hiệu \"Đã xác minh\" hiển thị trên trang shop và sản phẩm của bạn.",
                "/seller/verification");
        return VerificationResponse.from(v, true);
    }

    public VerificationResponse reject(Long id, String reason) {
        if (reason == null || reason.isBlank()) throw bad("Vui lòng nhập lý do từ chối");
        ShopVerification v = pending(id);
        v.setStatus(VerificationStatus.REJECTED);
        v.setRejectReason(NotificationService.cut(reason.trim(), 500));
        reviewed(v);
        notificationService.notify(v.getShop().getSeller().getId(), NotificationType.SYSTEM,
                "Hồ sơ xác minh shop chưa được duyệt", "Lý do: " + v.getRejectReason() + ". Vui lòng bổ sung và gửi lại.",
                "/seller/verification");
        return VerificationResponse.from(v, true);
    }

    // ==== helpers ====

    private void reviewed(ShopVerification v) {
        v.setReviewedAt(LocalDateTime.now());
        v.setReviewedBy(userRepository.getReferenceById(CurrentUser.id()));
    }

    private ShopVerification pending(Long id) {
        ShopVerification v = verificationRepository.findById(id)
                .orElseThrow(() -> new ApiException("Hồ sơ không tồn tại", HttpStatus.NOT_FOUND));
        if (v.getStatus() != VerificationStatus.PENDING) throw bad("Hồ sơ này đã được xử lý");
        return v;
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
