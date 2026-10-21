package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * "Quang cao san pham" (search ads, cost per click):
 *
 *  - the shop tops up a prepaid ad balance (simulated payment, like the checkout gateway);
 *  - a campaign promotes one product for keywords, with a bid per click and a daily budget;
 *  - a keyword search shows up to MAX_SPONSORED matching campaigns above the results with a
 *    "Tai tro" label, highest bid first (an impression each);
 *  - a click charges the bid, at most once per visitor per DEDUP_MINUTES, never the shop's own
 *    clicks, and never past the daily budget or the balance;
 *  - orders of the product by buyers who clicked within ATTRIBUTION_DAYS count as conversions.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AdService {

    public static final BigDecimal MIN_BID = BigDecimal.valueOf(500);
    public static final BigDecimal MAX_BID = BigDecimal.valueOf(50_000);
    public static final BigDecimal MIN_BUDGET = BigDecimal.valueOf(20_000);
    public static final BigDecimal MIN_TOPUP = BigDecimal.valueOf(50_000);
    public static final BigDecimal MAX_TOPUP = BigDecimal.valueOf(10_000_000);
    private static final int MAX_SPONSORED = 2;
    private static final int MAX_KEYWORDS = 20;
    private static final int DEDUP_MINUTES = 30;
    private static final int ATTRIBUTION_DAYS = 7;

    private final AdCampaignRepository campaignRepository;
    private final AdClickRepository clickRepository;
    private final AdTransactionRepository transactionRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final NotificationService notificationService;

    // ==================== wallet ====================

    @Transactional(readOnly = true)
    public Map<String, Object> wallet(int page) {
        Shop shop = myShop();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("balance", shopRepository.adBalance(shop.getId()));
        m.put("transactions", PageResponse.of(transactionRepository.findByShopIdOrderByIdDesc(shop.getId(),
                PageRequest.of(Math.max(page, 0), 10)), t -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", t.getId());
            x.put("type", t.getType());
            x.put("amount", t.getAmount());
            x.put("balanceAfter", t.getBalanceAfter());
            x.put("note", t.getNote());
            x.put("createdAt", t.getCreatedAt());
            return x;
        }));
        return m;
    }

    /** Simulated top-up: no real payment, the balance is credited at once. */
    public Map<String, Object> topUp(BigDecimal amount) {
        if (amount == null || amount.compareTo(MIN_TOPUP) < 0 || amount.compareTo(MAX_TOPUP) > 0) {
            throw bad("Số tiền nạp từ ₫50.000 đến ₫10.000.000");
        }
        Shop shop = myShop();
        BigDecimal value = amount.setScale(0, RoundingMode.DOWN);
        shopRepository.addAdBalance(shop.getId(), value);
        BigDecimal after = shopRepository.adBalance(shop.getId());
        transactionRepository.save(AdTransaction.builder().shopId(shop.getId()).type(AdTransaction.Type.TOPUP)
                .amount(value).balanceAfter(after).note("Nạp tiền quảng cáo (mô phỏng)").build());
        return wallet(0);
    }

    // ==================== campaigns (seller) ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> campaigns() {
        Shop shop = myShop();
        List<AdCampaign> list = campaignRepository.findByShopIdOrderByCreatedAtDesc(shop.getId());
        Map<Long, BigDecimal> today = todaySpend(list.stream().map(AdCampaign::getId).toList());
        return list.stream().map(c -> view(c, today.getOrDefault(c.getId(), BigDecimal.ZERO), true)).toList();
    }

    public Map<String, Object> create(Long productId, Object keywords, BigDecimal bid, BigDecimal budget) {
        Shop shop = myShop();
        Product p = productRepository.findById(productId == null ? -1 : productId)
                .filter(x -> x.getShop().getId().equals(shop.getId()))
                .orElseThrow(() -> bad("Sản phẩm không tồn tại"));
        if (!p.isActive()) throw bad("Sản phẩm đang ẩn, không thể quảng cáo");
        if (campaignRepository.existsByProductIdAndStatusNot(p.getId(), AdCampaign.Status.ENDED)) {
            throw bad("Sản phẩm này đã có chiến dịch quảng cáo, hãy sửa chiến dịch đó");
        }
        AdCampaign c = AdCampaign.builder().shop(shop).product(p).build();
        fill(c, keywords, bid, budget);
        return view(campaignRepository.save(c), BigDecimal.ZERO, true);
    }

    public Map<String, Object> update(Long id, Object keywords, BigDecimal bid, BigDecimal budget) {
        AdCampaign c = mine(id);
        if (c.getStatus() == AdCampaign.Status.ENDED) throw bad("Chiến dịch đã kết thúc");
        fill(c, keywords, bid, budget);
        return view(c, todaySpend(List.of(id)).getOrDefault(id, BigDecimal.ZERO), true);
    }

    public Map<String, Object> setStatus(Long id, String status) {
        AdCampaign c = mine(id);
        if (c.getStatus() == AdCampaign.Status.ENDED) throw bad("Chiến dịch đã kết thúc");
        AdCampaign.Status s;
        try {
            s = AdCampaign.Status.valueOf(status);
        } catch (Exception e) {
            throw bad("Trạng thái không hợp lệ");
        }
        c.setStatus(s);
        return view(c, todaySpend(List.of(id)).getOrDefault(id, BigDecimal.ZERO), true);
    }

    private void fill(AdCampaign c, Object keywords, BigDecimal bid, BigDecimal budget) {
        if (bid == null || bid.compareTo(MIN_BID) < 0 || bid.compareTo(MAX_BID) > 0) throw bad("Giá thầu mỗi click từ ₫500 đến ₫50.000");
        if (budget == null || budget.compareTo(MIN_BUDGET) < 0) throw bad("Ngân sách ngày tối thiểu ₫20.000");
        if (budget.compareTo(bid) < 0) throw bad("Ngân sách ngày phải lớn hơn giá thầu");
        List<String> raw = keywords instanceof Collection<?> col ? col.stream().map(String::valueOf).toList()
                : keywords instanceof String s ? List.of(s.split(",")) : List.of();
        LinkedHashSet<String> kws = new LinkedHashSet<>();
        for (String k : raw) {
            String n = TextNormalizer.normalize(k);
            if (n == null || n.isEmpty()) continue;
            if (n.length() < 2 || n.length() > 50) throw bad("Từ khóa \"" + k.trim() + "\" phải từ 2 đến 50 ký tự");
            kws.add(n);
        }
        if (kws.size() > MAX_KEYWORDS) throw bad("Tối đa " + MAX_KEYWORDS + " từ khóa");
        c.setKeywords(kws.isEmpty() ? null : String.join(",", kws));
        c.setBidPerClick(bid.setScale(0, RoundingMode.DOWN));
        c.setDailyBudget(budget.setScale(0, RoundingMode.DOWN));
    }

    // ==================== search (public) ====================

    /** Up to MAX_SPONSORED campaigns matching the search, best bid first; each one shown counts an impression. */
    public List<ProductResponse> sponsored(String query) {
        String q = TextNormalizer.normalize(query);
        if (q == null || q.isBlank()) return List.of();
        List<AdCampaign> candidates = campaignRepository.showable();
        if (candidates.isEmpty()) return List.of();
        Map<Long, BigDecimal> today = todaySpend(candidates.stream().map(AdCampaign::getId).toList());

        record Match(AdCampaign c, int relevance) {}
        List<Match> matches = new ArrayList<>();
        for (AdCampaign c : candidates) {
            if (today.getOrDefault(c.getId(), BigDecimal.ZERO).add(c.getBidPerClick()).compareTo(c.getDailyBudget()) > 0) continue;
            int rel = relevance(c, q);
            if (rel > 0) matches.add(new Match(c, rel));
        }
        // equal bids take turns: shuffle first, the stable sort keeps that random order among ties
        Collections.shuffle(matches);
        List<AdCampaign> shown = matches.stream()
                .sorted(Comparator.comparing((Match m) -> m.c().getBidPerClick()).reversed()
                        .thenComparing(Comparator.comparingInt(Match::relevance).reversed()))
                .map(Match::c).limit(MAX_SPONSORED).toList();
        if (shown.isEmpty()) return List.of();
        campaignRepository.countImpressions(shown.stream().map(AdCampaign::getId).toList());
        return shown.stream().map(c -> {
            ProductResponse r = ProductResponse.from(c.getProduct());
            r.setSponsored(true);
            r.setAdId(c.getId());
            return r;
        }).toList();
    }

    // keywords: the longest one contained in the query (or containing a 3+ letter query);
    // no keywords: every word of the query in the product name
    private static int relevance(AdCampaign c, String q) {
        if (c.getKeywords() != null) {
            int best = 0;
            for (String k : c.getKeywords().split(",")) {
                if ((" " + q + " ").contains(" " + k + " ") || (q.length() >= 3 && k.contains(q))) best = Math.max(best, k.length());
            }
            return best;
        }
        String name = " " + (c.getProduct().getSearchText() != null ? c.getProduct().getSearchText() : "") + " ";
        for (String w : q.split(" ")) if (!name.contains(" " + w + " ")) return 0;
        return q.length();
    }

    /** A click on a sponsored result: charged unless repeated, the shop's own, or over budget / balance. */
    public Map<String, Object> click(Long id, Long userId, String visitorKey) {
        AdCampaign c = campaignRepository.findById(id).orElse(null);
        if (c == null) return Map.of("charged", false);
        Shop shop = c.getShop();
        if (userId != null && userId.equals(shop.getSeller().getId())) return Map.of("charged", false); // own clicks don't count
        LocalDateTime now = LocalDateTime.now();
        BigDecimal bid = c.getBidPerClick();

        boolean repeated = clickRepository.existsByCampaignIdAndVisitorKeyAndCreatedAtAfter(id, visitorKey, now.minusMinutes(DEDUP_MINUTES));
        boolean withinBudget = todaySpend(List.of(id)).getOrDefault(id, BigDecimal.ZERO).add(bid).compareTo(c.getDailyBudget()) <= 0;
        boolean charge = !repeated && withinBudget && c.getStatus() == AdCampaign.Status.ACTIVE
                && shopRepository.addAdBalance(shop.getId(), bid.negate()) == 1;

        clickRepository.save(AdClick.builder().campaignId(id).userId(userId).visitorKey(visitorKey)
                .cost(charge ? bid : BigDecimal.ZERO).charged(charge).build());
        campaignRepository.countClick(id, charge ? bid : BigDecimal.ZERO);
        if (charge) {
            BigDecimal after = shopRepository.adBalance(shop.getId());
            transactionRepository.save(AdTransaction.builder().shopId(shop.getId()).type(AdTransaction.Type.CLICK)
                    .amount(bid.negate()).balanceAfter(after).note("Click quảng cáo: " + c.getProduct().getName()).build());
            // this click took the balance below one more click: ads stop showing until a top-up
            if (after.compareTo(bid) < 0) {
                try {
                    notificationService.notify(shop.getSeller().getId(), NotificationType.SYSTEM, "Số dư quảng cáo sắp hết",
                            "Số dư còn " + NotificationService.vnd(after) + ", quảng cáo sẽ tạm ngừng hiển thị. Nạp thêm để tiếp tục.", "/seller/ads");
                } catch (Exception e) {
                    log.warn("Could not notify about ad balance: {}", e.getMessage());
                }
            }
        }
        return Map.of("charged", charge);
    }

    // ==================== helpers ====================

    private Map<Long, BigDecimal> todaySpend(Collection<Long> ids) {
        Map<Long, BigDecimal> m = new HashMap<>();
        if (ids.isEmpty()) return m;
        for (Object[] r : clickRepository.spendSince(ids, LocalDate.now().atStartOfDay())) m.put((Long) r[0], (BigDecimal) r[1]);
        return m;
    }

    private Map<String, Object> view(AdCampaign c, BigDecimal today, boolean withConversions) {
        Map<String, Object> m = new LinkedHashMap<>();
        Product p = c.getProduct();
        m.put("id", c.getId());
        m.put("productId", p.getId());
        m.put("productName", p.getName());
        m.put("thumbnailUrl", p.getThumbnailUrl());
        m.put("price", p.getEffectivePrice());
        m.put("keywords", c.getKeywords() == null ? List.of() : List.of(c.getKeywords().split(",")));
        m.put("bidPerClick", c.getBidPerClick());
        m.put("dailyBudget", c.getDailyBudget());
        m.put("status", c.getStatus());
        m.put("impressions", c.getImpressions());
        m.put("clicks", c.getClicks());
        m.put("ctr", c.getImpressions() == 0 ? 0 : Math.round(c.getClicks() * 10000.0 / c.getImpressions()) / 100.0);
        m.put("spend", c.getSpend());
        m.put("todaySpend", today);
        m.put("createdAt", c.getCreatedAt());
        if (withConversions) {
            // orders of the product by logged-in buyers within ATTRIBUTION_DAYS after their click
            List<AdClick> clicks = clickRepository.findByCampaignIdAndUserIdIsNotNull(c.getId());
            Set<Long> orders = new HashSet<>();
            BigDecimal revenue = BigDecimal.ZERO;
            if (!clicks.isEmpty()) {
                Map<Long, List<LocalDateTime>> byUser = clicks.stream().collect(Collectors.groupingBy(AdClick::getUserId,
                        Collectors.mapping(AdClick::getCreatedAt, Collectors.toList())));
                LocalDateTime first = clicks.stream().map(AdClick::getCreatedAt).min(Comparator.naturalOrder()).orElseThrow();
                for (OrderItem line : orderItemRepository.linesOfProductSince(p.getId(), first)) {
                    Order o = line.getOrder();
                    List<LocalDateTime> userClicks = byUser.get(o.getBuyer().getId());
                    if (userClicks == null) continue;
                    boolean attributed = userClicks.stream().anyMatch(t -> !o.getCreatedAt().isBefore(t)
                            && o.getCreatedAt().isBefore(t.plusDays(ATTRIBUTION_DAYS)));
                    if (attributed) {
                        orders.add(o.getId());
                        revenue = revenue.add(line.getPriceAtPurchase().multiply(BigDecimal.valueOf(line.getQuantity())));
                    }
                }
            }
            m.put("orders", orders.size());
            m.put("revenue", revenue);
            // "ROAS": revenue per dong spent
            m.put("roas", c.getSpend().signum() == 0 ? 0 : revenue.divide(c.getSpend(), 2, RoundingMode.HALF_UP));
        }
        return m;
    }

    private AdCampaign mine(Long id) {
        Long shopId = myShop().getId();
        return campaignRepository.findById(id).filter(c -> c.getShop().getId().equals(shopId))
                .orElseThrow(() -> new ApiException("Chiến dịch không tồn tại", HttpStatus.NOT_FOUND));
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
