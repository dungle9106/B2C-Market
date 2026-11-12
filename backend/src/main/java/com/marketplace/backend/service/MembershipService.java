package com.marketplace.backend.service;

import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Hang thanh vien": worked out from what the buyer paid for orders delivered in the last 6 months, so it needs
 * no table or job and a tier drops by itself when spending stops. A returned order is no longer DELIVERED and
 * stops counting. The perk is a multiplier on the Shoppi Xu earned per order.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MembershipService {

    public record Tier(String code, String name, BigDecimal minSpend, int coinMultiplierPercent) {}

    public static final List<Tier> TIERS = List.of(
            new Tier("MEMBER", "Thành viên", BigDecimal.ZERO, 100),
            new Tier("SILVER", "Bạc", new BigDecimal("2000000"), 125),
            new Tier("GOLD", "Vàng", new BigDecimal("10000000"), 150),
            new Tier("DIAMOND", "Kim Cương", new BigDecimal("30000000"), 200));

    private static final int WINDOW_MONTHS = 6;

    private final OrderRepository orderRepository;

    public Tier tierOf(Long userId) {
        return tierFor(spend(userId));
    }

    public Map<String, Object> mine() {
        BigDecimal spend = spend(CurrentUser.id());
        Tier tier = tierFor(spend);
        Tier next = TIERS.stream().filter(t -> t.minSpend().compareTo(spend) > 0).findFirst().orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tier", tier);
        m.put("spend", spend);
        m.put("windowMonths", WINDOW_MONTHS);
        m.put("nextTier", next);
        m.put("remaining", next == null ? null : next.minSpend().subtract(spend));
        m.put("tiers", TIERS);
        return m;
    }

    private BigDecimal spend(Long userId) {
        return orderRepository.deliveredSpend(userId, OrderStatus.DELIVERED, LocalDateTime.now().minusMonths(WINDOW_MONTHS));
    }

    private static Tier tierFor(BigDecimal spend) {
        Tier tier = TIERS.get(0);
        for (Tier t : TIERS) if (spend.compareTo(t.minSpend()) >= 0) tier = t;
        return tier;
    }
}
