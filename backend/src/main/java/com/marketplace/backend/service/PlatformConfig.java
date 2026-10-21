package com.marketplace.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.entity.Category;
import com.marketplace.backend.entity.PlatformSetting;
import com.marketplace.backend.repository.PlatformSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Read side of the platform configuration ("Cau hinh he thong"): values other services need at runtime.
 * It only reads the settings table (no audit / notification dependency), so any service can use it;
 * PlatformSettingService validates, saves and audits the changes.
 */
@Component
@RequiredArgsConstructor
public class PlatformConfig {

    public static final String COMMISSION_PERCENT = "platform.commissionPercent";
    public static final String CATEGORY_COMMISSION = "platform.categoryCommission";   // JSON { "<categoryId>": percent }
    public static final String SETTLEMENT_HOLD_DAYS = "platform.settlementHoldDays";
    public static final String PAYMENT_WINDOW_MINUTES = "platform.paymentWindowMinutes";
    public static final String DISPUTE_RESPONSE_HOURS = "platform.disputeResponseHours";

    public static final BigDecimal DEFAULT_COMMISSION = new BigDecimal("5");
    public static final int DEFAULT_HOLD_DAYS = 3;
    public static final int DEFAULT_PAYMENT_WINDOW = 15;
    public static final int DEFAULT_DISPUTE_HOURS = 48;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PlatformSettingRepository repository;

    public String get(String key) {
        return repository.findById(key).map(PlatformSetting::getValue).orElse(null);
    }

    private int integer(String key, int fallback) {
        String v = get(key);
        try {
            return v == null ? fallback : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Shoppi's fee, % of the goods value after the shop's discounts (default rate). */
    public BigDecimal commissionPercent() {
        String v = get(COMMISSION_PERCENT);
        return v == null ? DEFAULT_COMMISSION : new BigDecimal(v);
    }

    /** Fee overrides per top-level category: { categoryId -> percent }. */
    public Map<Long, BigDecimal> categoryCommission() {
        String v = get(CATEGORY_COMMISSION);
        if (v == null || v.isBlank()) return Collections.emptyMap();
        try {
            Map<String, BigDecimal> raw = JSON.readValue(v, new TypeReference<>() { });
            Map<Long, BigDecimal> m = new HashMap<>();
            raw.forEach((k, rate) -> m.put(Long.valueOf(k), rate));
            return m;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /** The fee of a product's category: its own override, else its parent's, else the default rate. */
    public BigDecimal commissionFor(Category c, Map<Long, BigDecimal> overrides, BigDecimal fallback) {
        if (c != null && overrides.containsKey(c.getId())) return overrides.get(c.getId());
        if (c != null && c.getParent() != null && overrides.containsKey(c.getParent().getId())) return overrides.get(c.getParent().getId());
        return fallback;
    }

    /** Days after delivery before an order is paid out to the shop (the buyer's return window). */
    public int settlementHoldDays() {
        return integer(SETTLEMENT_HOLD_DAYS, DEFAULT_HOLD_DAYS);
    }

    /** Minutes an online checkout waits for payment before it is cancelled. */
    public int paymentWindowMinutes() {
        return integer(PAYMENT_WINDOW_MINUTES, DEFAULT_PAYMENT_WINDOW);
    }

    /** Hours the seller has to answer a dispute. */
    public int disputeResponseHours() {
        return integer(DISPUTE_RESPONSE_HOURS, DEFAULT_DISPUTE_HOURS);
    }

    // ==== sign-in security ====
    public static final String MAX_FAILED_LOGINS = "security.maxFailedLogins";
    public static final String LOCK_MINUTES = "security.lockMinutes";
    public static final String IP_FAIL_LIMIT = "security.ipFailLimit";         // failed sign-ins per IP in 15 minutes
    public static final String REGISTER_PER_IP = "security.registerPerIp";     // new accounts per IP in 10 minutes
    public static final String REQUIRE_ADMIN_2FA = "security.requireAdmin2fa";

    /** Wrong passwords in a row before the account is locked for a while. */
    public int maxFailedLogins() {
        return integer(MAX_FAILED_LOGINS, 5);
    }

    public int lockMinutes() {
        return integer(LOCK_MINUTES, 15);
    }

    public int ipFailLimit() {
        return integer(IP_FAIL_LIMIT, 30);
    }

    public int registerPerIp() {
        return integer(REGISTER_PER_IP, 50);
    }

    /** Every admin must turn on two-step verification before using the admin console. */
    public boolean requireAdmin2fa() {
        return "true".equals(get(REQUIRE_ADMIN_2FA));
    }
}
