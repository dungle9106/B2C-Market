package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.VoucherResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.SavedVoucherRepository;
import com.marketplace.backend.repository.ShopFollowRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.repository.VoucherRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * "Kho Voucher": vouchers a buyer saved. Public ones are saved with "Luu" (shop page, product page, the wallet's
 * suggestions); a private voucher can be saved by typing its code, and then shows in the checkout picker like a public
 * one. Each saved voucher carries its state for this buyer (usable, not started, used up, expired), and the buyer is
 * reminded once when a saved, unused voucher ends within 24 hours.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class VoucherWalletService {

    private final SavedVoucherRepository savedRepository;
    private final VoucherRepository voucherRepository;
    private final ShopFollowRepository followRepository;
    private final UserRepository userRepository;
    private final VoucherService voucherService;
    private final NotificationService notificationService;

    /** The wallet, usable vouchers first (ending soonest), and up to 20 public vouchers worth saving. */
    @Transactional(readOnly = true)
    public Map<String, Object> wallet() {
        Long me = CurrentUser.id();
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> items = new ArrayList<>();
        Set<Long> saved = new HashSet<>();
        for (SavedVoucher s : savedRepository.findMine(me)) {
            Voucher v = s.getVoucher();
            saved.add(v.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("voucher", VoucherResponse.from(v));
            String problem = voucherService.problemWith(v, me);
            String state = problem == null ? "USABLE"
                    : v.getEndAt() != null && !now.isBefore(v.getEndAt()) || !v.isActive() ? "EXPIRED"
                    : v.getStartAt() != null && now.isBefore(v.getStartAt()) ? "UPCOMING"
                    : "UNAVAILABLE";
            m.put("state", state);
            m.put("reason", problem);
            m.put("savedAt", s.getSavedAt());
            items.add(m);
        }
        Map<String, Integer> order = Map.of("USABLE", 0, "UPCOMING", 1, "UNAVAILABLE", 2, "EXPIRED", 3);
        items.sort(Comparator.comparing((Map<String, Object> m) -> order.get((String) m.get("state")))
                .thenComparing(m -> ((VoucherResponse) m.get("voucher")).getEndAt(), Comparator.nullsLast(Comparator.naturalOrder())));

        // worth saving: Shoppi vouchers and those of the shops the buyer follows, public, running, not saved yet
        List<Long> followed = followRepository.followedShopIds(me);
        List<VoucherResponse> collectable = voucherRepository.findActiveForShops(followed.isEmpty() ? List.of(-1L) : followed).stream()
                .filter(v -> v.isPublicVisible() && v.isRunning(now) && !v.isUsedUp() && !saved.contains(v.getId()))
                .filter(v -> voucherService.problemWith(v, me) == null)
                .sorted(Comparator.comparing((Voucher v) -> v.getScope() != VoucherScope.PLATFORM).thenComparing(Voucher::getEndAt))
                .limit(20).map(VoucherResponse::from).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("collectable", collectable);
        out.put("usable", items.stream().filter(m -> "USABLE".equals(m.get("state"))).count());
        return out;
    }

    @Transactional(readOnly = true)
    public List<Long> savedIds() {
        return savedRepository.savedIds(CurrentUser.id());
    }

    /** "Luu": a public voucher, still running or about to start. */
    public Map<String, Object> save(Long voucherId) {
        Voucher v = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ApiException("Voucher không tồn tại", HttpStatus.NOT_FOUND));
        if (!v.isPublicVisible()) throw new ApiException("Voucher không tồn tại", HttpStatus.NOT_FOUND);
        return store(v);
    }

    /** A private voucher (or any voucher) by its code. */
    public Map<String, Object> saveByCode(String code) {
        if (code == null || code.isBlank()) throw bad("Vui lòng nhập mã voucher");
        Voucher v = voucherRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> bad("Mã voucher không tồn tại"));
        return store(v);
    }

    private Map<String, Object> store(Voucher v) {
        LocalDateTime now = LocalDateTime.now();
        if (!v.isActive() || v.getEndAt() != null && !now.isBefore(v.getEndAt())) throw bad("Voucher đã kết thúc");
        if (v.isUsedUp()) throw bad("Voucher đã hết lượt sử dụng");
        Long me = CurrentUser.id();
        if (!savedRepository.existsByUserIdAndVoucherId(me, v.getId())) {
            try {
                savedRepository.saveAndFlush(SavedVoucher.builder().user(userRepository.getReferenceById(me)).voucher(v).build());
            } catch (DataIntegrityViolationException ignored) {
                // saved twice at once
            }
        }
        VoucherResponse r = VoucherResponse.from(v);
        return Map.of("saved", true, "voucher", r);
    }

    public Map<String, Object> remove(Long voucherId) {
        savedRepository.deleteMine(CurrentUser.id(), voucherId);
        return Map.of("saved", false);
    }

    /** Every hour: one reminder for each saved voucher ending within 24 hours that the buyer can still use. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    public void remindEndingSoon() {
        LocalDateTime now = LocalDateTime.now();
        for (SavedVoucher s : savedRepository.endingSoon(now, now.plusHours(24))) {
            try {
                Voucher v = s.getVoucher();
                Long userId = s.getUser().getId();
                if (voucherService.problemWith(v, userId) == null) {
                    notificationService.notify(userId, NotificationType.PROMOTION, "Voucher " + v.getCode() + " sắp hết hạn",
                            v.title() + (v.getShop() != null ? " của " + v.getShop().getName() : " (Shoppi Voucher)")
                                    + " trong Kho Voucher của bạn hết hạn lúc " + v.getEndAt().toLocalTime().withNano(0) + " "
                                    + v.getEndAt().toLocalDate() + ". Dùng ngay kẻo lỡ!", "/account/vouchers");
                }
                s.setRemindedAt(now);
            } catch (Exception e) {
                log.warn("Voucher reminder failed: {}", e.getMessage());
            }
        }
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
