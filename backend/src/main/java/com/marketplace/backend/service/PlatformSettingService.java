package com.marketplace.backend.service;

import com.marketplace.backend.entity.PlatformSetting;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.PlatformSettingRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Platform switches an admin can change at runtime ("Cai dat san").
@Service
@Transactional
@RequiredArgsConstructor
public class PlatformSettingService {

    /** New shops wait for an admin's approval before buyers can see them. Off by default. */
    public static final String SHOP_REQUIRE_APPROVAL = "shop.requireApproval";
    /** New products wait for an admin before buyers can see them. Off by default (content checks still run). */
    public static final String PRODUCT_REQUIRE_APPROVAL = "product.requireApproval";
    /** Words that hold a product for review (one per line); see ProductModerationService.DEFAULT_BANNED. */
    public static final String PRODUCT_BANNED_KEYWORDS = "product.bannedKeywords";
    /** Shoppi's fee, % of the goods value (after shop-funded discounts) of each completed order. */
    public static final String COMMISSION_PERCENT = PlatformConfig.COMMISSION_PERCENT;
    public static final BigDecimal DEFAULT_COMMISSION = PlatformConfig.DEFAULT_COMMISSION;

    private final PlatformSettingRepository repository;
    private final AdminAuditService auditService;
    private final PlatformConfig config;
    private final com.marketplace.backend.repository.CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public boolean requireShopApproval() {
        return repository.findById(SHOP_REQUIRE_APPROVAL).map(s -> "true".equals(s.getValue())).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean requireProductApproval() {
        return repository.findById(PRODUCT_REQUIRE_APPROVAL).map(s -> "true".equals(s.getValue())).orElse(false);
    }

    /** The banned keyword list; the built-in default until an admin edits it. */
    @Transactional(readOnly = true)
    public List<String> bannedKeywords() {
        return repository.findById(PRODUCT_BANNED_KEYWORDS).map(s -> lines(s.getValue()))
                .orElse(ProductModerationService.DEFAULT_BANNED);
    }

    @Transactional(readOnly = true)
    public BigDecimal commissionPercent() {
        return config.commissionPercent();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> all() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requireShopApproval", requireShopApproval());
        m.put("requireProductApproval", requireProductApproval());
        m.put("bannedKeywords", bannedKeywords());
        m.put("commissionPercent", commissionPercent());
        // { categoryId: percent } for top-level categories that have their own fee
        Map<String, BigDecimal> cats = new LinkedHashMap<>();
        config.categoryCommission().forEach((k, v) -> cats.put(String.valueOf(k), v));
        m.put("categoryCommission", cats);
        m.put("settlementHoldDays", config.settlementHoldDays());
        m.put("paymentWindowMinutes", config.paymentWindowMinutes());
        m.put("disputeResponseHours", config.disputeResponseHours());
        m.put("maxFailedLogins", config.maxFailedLogins());
        m.put("lockMinutes", config.lockMinutes());
        m.put("ipFailLimit", config.ipFailLimit());
        m.put("registerPerIp", config.registerPerIp());
        m.put("requireAdmin2fa", config.requireAdmin2fa());
        return m;
    }

    private static List<String> lines(String v) {
        if (v == null) return List.of();
        return Arrays.stream(v.split("\\n")).map(String::trim).filter(x -> !x.isEmpty()).distinct().toList();
    }

    // a whole number between min and max; saved and audited when it changes
    private void number(Map<String, Object> body, String field, String key, int current, int min, int max, String label, String unit) {
        if (!(body.get(field) instanceof Number n)) return;
        if (n.doubleValue() != Math.floor(n.doubleValue()) || n.intValue() < min || n.intValue() > max) {
            throw new ApiException(label + " từ " + min + " đến " + max + " " + unit, HttpStatus.BAD_REQUEST);
        }
        if (n.intValue() != current) {
            put(key, String.valueOf(n.intValue()));
            auditService.log("UPDATE_SETTING", "SETTING", null, label, current + " → " + n.intValue() + " " + unit);
        }
    }

    private void put(String key, String value) {
        PlatformSetting s = repository.findById(key).orElseGet(PlatformSetting::new);
        s.setKey(key);
        s.setValue(value);
        s.setUpdatedAt(LocalDateTime.now());
        s.setUpdatedBy(CurrentUser.id());
        repository.save(s);
    }

    /** body: { requireShopApproval?, requireProductApproval?, bannedKeywords?: [..] } (other keys ignored) */
    @SuppressWarnings("unchecked")
    public Map<String, Object> update(Map<String, Object> body) {
        if (body.containsKey("requireProductApproval")) {
            boolean v = Boolean.TRUE.equals(body.get("requireProductApproval"));
            if (v != requireProductApproval()) {
                put(PRODUCT_REQUIRE_APPROVAL, String.valueOf(v));
                auditService.log("UPDATE_SETTING", "SETTING", null, "Duyệt sản phẩm mới",
                        v ? "Bật: sản phẩm mới phải được duyệt trước khi hiển thị" : "Tắt: sản phẩm mới hiển thị ngay (vẫn kiểm tra nội dung tự động)");
            }
        }
        if (body.get("bannedKeywords") instanceof List<?> list) {
            List<String> words = ((List<Object>) list).stream().map(String::valueOf).map(String::trim).filter(x -> !x.isEmpty())
                    .map(String::toLowerCase).distinct().toList();
            if (words.size() > 300) throw new ApiException("Tối đa 300 từ khóa", HttpStatus.BAD_REQUEST);
            if (!words.equals(bannedKeywords())) {
                put(PRODUCT_BANNED_KEYWORDS, String.join("\n", words));
                auditService.log("UPDATE_SETTING", "SETTING", null, "Từ khóa cấm", words.size() + " từ khóa");
            }
        }
        if (body.get("commissionPercent") instanceof Number n) {
            BigDecimal v = new BigDecimal(n.toString()).setScale(2, java.math.RoundingMode.HALF_UP);
            if (v.signum() < 0 || v.compareTo(new BigDecimal("30")) > 0) throw new ApiException("Phí sàn từ 0% đến 30%", HttpStatus.BAD_REQUEST);
            BigDecimal old = commissionPercent();
            if (v.compareTo(old) != 0) {
                put(COMMISSION_PERCENT, v.stripTrailingZeros().toPlainString());
                auditService.log("UPDATE_SETTING", "SETTING", null, "Phí sàn", old.stripTrailingZeros().toPlainString() + "% → "
                        + v.stripTrailingZeros().toPlainString() + "% (áp dụng cho các bảng đối soát tạo từ bây giờ)");
            }
        }
        if (body.get("categoryCommission") instanceof Map<?, ?> raw) {
            Map<Long, BigDecimal> next = new java.util.TreeMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                if (e.getValue() == null || e.getValue().toString().isBlank()) continue;   // blank = use the default rate
                Long id;
                BigDecimal rate;
                try {
                    id = Long.valueOf(e.getKey().toString());
                    rate = new BigDecimal(e.getValue().toString()).setScale(2, java.math.RoundingMode.HALF_UP);
                } catch (NumberFormatException ex) {
                    throw new ApiException("Phí ngành hàng không hợp lệ", HttpStatus.BAD_REQUEST);
                }
                var cat = categoryRepository.findById(id).orElseThrow(() -> new ApiException("Ngành hàng không tồn tại", HttpStatus.BAD_REQUEST));
                if (cat.getParent() != null) throw new ApiException("Chỉ đặt phí riêng cho ngành hàng cấp 1", HttpStatus.BAD_REQUEST);
                if (rate.signum() < 0 || rate.compareTo(new BigDecimal("30")) > 0) throw new ApiException("Phí sàn từ 0% đến 30%", HttpStatus.BAD_REQUEST);
                next.put(id, rate.stripTrailingZeros());
            }
            Map<Long, BigDecimal> old = new java.util.TreeMap<>(config.categoryCommission());
            if (!next.equals(old)) {
                Map<String, String> json = new LinkedHashMap<>();
                next.forEach((k, v) -> json.put(String.valueOf(k), v.toPlainString()));
                try {
                    put(PlatformConfig.CATEGORY_COMMISSION, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(json));
                } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                    throw new IllegalStateException(ex);
                }
                List<String> parts = new java.util.ArrayList<>();
                next.forEach((k, v) -> parts.add(categoryRepository.findById(k).map(c -> c.getName()).orElse("#" + k) + " " + v.toPlainString() + "%"));
                auditService.log("UPDATE_SETTING", "SETTING", null, "Phí sàn theo ngành hàng", parts.isEmpty() ? "Bỏ phí riêng, dùng mức chung" : String.join(", ", parts));
            }
        }
        number(body, "settlementHoldDays", PlatformConfig.SETTLEMENT_HOLD_DAYS, config.settlementHoldDays(), 1, 30, "Thời gian giữ tiền trước đối soát", "ngày");
        number(body, "paymentWindowMinutes", PlatformConfig.PAYMENT_WINDOW_MINUTES, config.paymentWindowMinutes(), 5, 120, "Thời hạn thanh toán online", "phút");
        number(body, "disputeResponseHours", PlatformConfig.DISPUTE_RESPONSE_HOURS, config.disputeResponseHours(), 12, 168, "Hạn người bán giải trình tranh chấp", "giờ");
        number(body, "maxFailedLogins", PlatformConfig.MAX_FAILED_LOGINS, config.maxFailedLogins(), 3, 20, "Số lần nhập sai mật khẩu trước khi khóa tạm", "lần");
        number(body, "lockMinutes", PlatformConfig.LOCK_MINUTES, config.lockMinutes(), 1, 1440, "Thời gian khóa tạm khi nhập sai", "phút");
        number(body, "ipFailLimit", PlatformConfig.IP_FAIL_LIMIT, config.ipFailLimit(), 1, 1000, "Số lần đăng nhập sai tối đa mỗi IP / 15 phút", "lần");
        number(body, "registerPerIp", PlatformConfig.REGISTER_PER_IP, config.registerPerIp(), 1, 1000, "Số tài khoản mới tối đa mỗi IP / 10 phút", "tài khoản");
        if (body.get("requireAdmin2fa") instanceof Boolean v && v != config.requireAdmin2fa()) {
            put(PlatformConfig.REQUIRE_ADMIN_2FA, String.valueOf(v));
            auditService.log("UPDATE_SETTING", "SETTING", null, "Bắt buộc xác thực 2 lớp cho quản trị viên", v ? "Bật" : "Tắt");
        }
        if (body.containsKey("requireShopApproval")) {
            boolean v = Boolean.TRUE.equals(body.get("requireShopApproval"));
            if (v != requireShopApproval()) {
                put(SHOP_REQUIRE_APPROVAL, String.valueOf(v));
                auditService.log("UPDATE_SETTING", "SETTING", null, "Duyệt shop mới",
                        v ? "Bật: shop mới phải được duyệt trước khi hiển thị" : "Tắt: shop mới hoạt động ngay");
            }
        }
        return all();
    }
}
