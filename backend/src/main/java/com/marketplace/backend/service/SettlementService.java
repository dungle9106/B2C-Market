package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.RefundRepository;
import com.marketplace.backend.repository.ReturnRequestRepository;
import com.marketplace.backend.repository.SettlementRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * "Doi soat thanh toan voi nguoi ban". Shoppi collects the buyers' money (online or COD) and pays each shop
 * for its completed orders:
 *   payout = goods (subtotal) - shop-funded discounts (combo + shop voucher) - commission - refunds deducted
 * The platform voucher is funded by Shoppi and the shipping fee belongs to the carrier, so neither changes it.
 * An order becomes payable once it has been delivered for HOLD_DAYS with no return/dispute open; a refund made
 * after its order was paid out is deducted from the shop's next statement.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SettlementService {

    private static final Set<ReturnStatus> OPEN_RETURN = EnumSet.of(ReturnStatus.REQUESTED, ReturnStatus.ESCALATED);
    private static final DateTimeFormatter CODE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PlatformConfig config;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final ReturnRequestRepository returnRepository;
    private final SettlementRepository settlementRepository;
    private final ShopRepository shopRepository;
    private final PlatformSettingService settings;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;

    record Line(Order order, BigDecimal goods, BigDecimal discounts, BigDecimal commission, BigDecimal refunds) {
        BigDecimal net() {
            return goods.subtract(discounts).subtract(commission).subtract(refunds);
        }
    }

    // ==================== admin ====================

    /** What each shop would receive for orders delivered up to `to` (and at least HOLD_DAYS ago). */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(LocalDate to) {
        BigDecimal rate = settings.commissionPercent();
        Map<Shop, List<Order>> byShop = eligible(to).stream().collect(Collectors.groupingBy(Order::getShop));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Shop, List<Order>> e : byShop.entrySet()) {
            List<Line> lines = lines(e.getValue(), rate);
            List<Refund> adjustments = refundRepository.undeductedForShop(e.getKey().getId(), Refund.Source.ORDER_CANCELLED);
            Map<String, Object> sum = totals(lines, adjustments, rate);
            sum.put("shopId", e.getKey().getId());
            sum.put("shopName", e.getKey().getName());
            rows.add(sum);
            total = total.add((BigDecimal) sum.get("netPayout"));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("netPayout")).reversed());
        return AdminUserService.map("to", cutoffDate(to), "holdDays", config.settlementHoldDays(), "commissionRate", rate, "shops", rows, "totalPayout", total);
    }

    /** Creates one statement per shop (shopIds null = every shop with something payable). */
    public List<Map<String, Object>> create(List<Long> shopIds, LocalDate to) {
        BigDecimal rate = settings.commissionPercent();
        Map<Shop, List<Order>> byShop = eligible(to).stream()
                .filter(o -> shopIds == null || shopIds.isEmpty() || shopIds.contains(o.getShop().getId()))
                .collect(Collectors.groupingBy(Order::getShop));
        if (byShop.isEmpty()) throw bad("Không có đơn hàng nào đủ điều kiện đối soát");
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate cutoff = cutoffDate(to);
        for (Map.Entry<Shop, List<Order>> e : byShop.entrySet()) {
            Shop shop = e.getKey();
            List<Line> lines = lines(e.getValue(), rate);
            Settlement s = settlementRepository.save(Settlement.builder()
                    .code("DS-" + cutoff.format(CODE) + "-" + shop.getId() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase())
                    .shop(shop).periodTo(cutoff).orderCount(lines.size())
                    .merchandise(sum(lines, Line::goods)).shopDiscounts(sum(lines, Line::discounts)).commissionRate(rate)
                    .commission(sum(lines, Line::commission)).refunds(BigDecimal.ZERO).netPayout(BigDecimal.ZERO)
                    .createdBy(CurrentUser.id()).build());
            Map<Long, BigDecimal> fees = new HashMap<>();
            lines.forEach(l -> fees.put(l.order().getId(), l.commission()));
            e.getValue().forEach(o -> {
                o.setSettlementId(s.getId());
                o.setCommissionFee(fees.get(o.getId()));
            });
            BigDecimal feeBase = s.getMerchandise().subtract(s.getShopDiscounts());
            if (feeBase.signum() > 0) {
                s.setCommissionRate(s.getCommission().multiply(BigDecimal.valueOf(100)).divide(feeBase, 2, RoundingMode.HALF_UP));
            }
            orderRepository.saveAllAndFlush(e.getValue());
            // every refund the shop bears that no statement deducted yet: these orders' and earlier orders'
            List<Refund> refunds = refundRepository.undeductedForShop(shop.getId(), Refund.Source.ORDER_CANCELLED).stream()
                    .filter(r -> r.getStatus() != Refund.Status.FAILED).toList();
            refunds.forEach(r -> r.setSettlementId(s.getId()));
            BigDecimal refundTotal = refunds.stream().map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            s.setRefunds(refundTotal);
            s.setNetPayout(s.getMerchandise().subtract(s.getShopDiscounts()).subtract(s.getCommission()).subtract(refundTotal));
            notificationService.notify(shop.getSeller().getId(), NotificationType.SYSTEM, "Bảng đối soát mới " + s.getCode(),
                    lines.size() + " đơn hàng, số tiền shop nhận " + NotificationService.vnd(s.getNetPayout()) + ". Shoppi sẽ chuyển khoản trong 1-2 ngày làm việc.",
                    "/seller/finance");
            auditService.log("CREATE_SETTLEMENT", "SHOP", shop.getId(), shop.getName(), s.getCode() + " – " + NotificationService.vnd(s.getNetPayout()));
            out.add(view(s));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(String status, Long shopId, int page, int size) {
        Settlement.Status st = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            try {
                st = Settlement.Status.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw bad("Trạng thái không hợp lệ");
            }
        }
        return PageResponse.of(settlementRepository.search(st, shopId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("id").descending())), SettlementService::view);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> counts() {
        return AdminUserService.map("PENDING_PAYMENT", settlementRepository.countByStatus(Settlement.Status.PENDING_PAYMENT),
                "PAID", settlementRepository.countByStatus(Settlement.Status.PAID),
                "pendingAmount", settlementRepository.sumNet(Settlement.Status.PENDING_PAYMENT),
                "paidAmount", settlementRepository.sumNet(Settlement.Status.PAID),
                "commissionRate", settings.commissionPercent(), "holdDays", config.settlementHoldDays());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        return detailOf(find(id));
    }

    /** The bank transfer to the shop was made. */
    public Map<String, Object> pay(Long id, String reference) {
        Settlement s = find(id);
        if (s.getStatus() == Settlement.Status.PAID) throw bad("Bảng đối soát đã được thanh toán");
        if (reference == null || reference.trim().length() < 3) throw bad("Vui lòng nhập mã giao dịch chuyển khoản");
        s.setStatus(Settlement.Status.PAID);
        s.setPaymentReference(reference.trim());
        s.setPaidAt(LocalDateTime.now());
        notificationService.notify(s.getShop().getSeller().getId(), NotificationType.SYSTEM, "Shoppi đã thanh toán " + s.getCode(),
                "Đã chuyển " + NotificationService.vnd(s.getNetPayout()) + " vào tài khoản của shop (mã giao dịch " + s.getPaymentReference() + ").",
                "/seller/finance");
        auditService.log("PAY_SETTLEMENT", "SHOP", s.getShop().getId(), s.getShop().getName(), s.getCode() + " – mã " + s.getPaymentReference());
        return view(s);
    }

    // ==================== seller ====================

    /** "Doanh thu & Doi soat" of the seller centre. */
    @Transactional(readOnly = true)
    public Map<String, Object> mine() {
        Shop shop = myShop();
        BigDecimal rate = settings.commissionPercent();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(config.settlementHoldDays());
        List<Order> unsettled = orderRepository.unsettledForShop(shop.getId(), OrderStatus.DELIVERED);
        List<Order> ready = unsettled.stream().filter(o -> o.getDeliveredAt() != null && o.getDeliveredAt().isBefore(cutoff)
                && !returnRepository.existsByOrderIdAndStatusIn(o.getId(), OPEN_RETURN)).toList();
        List<Refund> adjustments = refundRepository.undeductedForShop(shop.getId(), Refund.Source.ORDER_CANCELLED);
        Map<String, Object> estimate = totals(lines(unsettled, rate), adjustments, rate);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("commissionRate", rate);
        m.put("holdDays", config.settlementHoldDays());
        m.put("unsettled", estimate);                 // every delivered order not paid out yet
        m.put("readyOrders", ready.size());          // of which past the hold period, no return open
        m.put("pendingPayment", settlementRepository.sumNetForShop(shop.getId(), Settlement.Status.PENDING_PAYMENT));
        m.put("paidTotal", settlementRepository.sumNetForShop(shop.getId(), Settlement.Status.PAID));
        m.put("settlements", settlementRepository.findByShopIdOrderByIdDesc(shop.getId()).stream().map(SettlementService::view).toList());
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> mineDetail(Long id) {
        Settlement s = find(id);
        if (!s.getShop().getId().equals(myShop().getId())) throw new ApiException("Bảng đối soát không tồn tại", HttpStatus.NOT_FOUND);
        return detailOf(s);
    }

    // ==================== helpers ====================

    private List<Order> eligible(LocalDate to) {
        LocalDateTime cutoff = cutoffDate(to).plusDays(1).atStartOfDay();
        return orderRepository.unsettledDelivered(OrderStatus.DELIVERED, cutoff).stream()
                .filter(o -> !returnRepository.existsByOrderIdAndStatusIn(o.getId(), OPEN_RETURN)).toList();
    }

    // never later than HOLD_DAYS ago: buyers may still ask for a return before that
    private LocalDate cutoffDate(LocalDate to) {
        LocalDate latest = LocalDate.now().minusDays(config.settlementHoldDays());
        return to == null || to.isAfter(latest) ? latest : to;
    }

    private List<Line> lines(List<Order> orders, BigDecimal rate) {
        Map<Long, BigDecimal> refunds = new HashMap<>();
        if (!orders.isEmpty()) {
            refundRepository.findByOrderIdIn(orders.stream().map(Order::getId).toList()).stream()
                    .filter(r -> r.getSource() != Refund.Source.ORDER_CANCELLED && r.getStatus() != Refund.Status.FAILED && r.getSettlementId() == null)
                    .forEach(r -> refunds.merge(r.getOrder().getId(), r.getAmount(), BigDecimal::add));
        }
        Map<Long, BigDecimal> overrides = config.categoryCommission();
        return orders.stream().map(o -> {
            BigDecimal goods = o.getSubtotal();
            BigDecimal discounts = nz(o.getComboDiscount()).add(nz(o.getShopVoucherDiscount()));
            BigDecimal commission = fee(o, goods, discounts, rate, overrides);
            return new Line(o, goods, discounts, commission, refunds.getOrDefault(o.getId(), BigDecimal.ZERO));
        }).toList();
    }

    // the fee of one order: with category rates, each item's rate weighted by its value
    private BigDecimal fee(Order o, BigDecimal goods, BigDecimal discounts, BigDecimal rate, Map<Long, BigDecimal> overrides) {
        BigDecimal base = goods.subtract(discounts).max(BigDecimal.ZERO);
        BigDecimal effective = rate;
        if (!overrides.isEmpty() && goods.signum() > 0) {
            BigDecimal weighted = BigDecimal.ZERO;
            for (OrderItem i : o.getItems()) {
                BigDecimal value = i.getPriceAtPurchase().multiply(BigDecimal.valueOf(i.getQuantity()));
                weighted = weighted.add(value.multiply(config.commissionFor(i.getProduct() == null ? null : i.getProduct().getCategory(), overrides, rate)));
            }
            effective = weighted.divide(goods, 6, RoundingMode.HALF_UP);
        }
        return base.multiply(effective).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    // totals of a set of lines + refunds of earlier, already paid-out orders
    private static Map<String, Object> totals(List<Line> lines, List<Refund> adjustments, BigDecimal rate) {
        BigDecimal later = adjustments.stream().filter(r -> r.getStatus() != Refund.Status.FAILED).map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunds = sum(lines, Line::refunds).add(later);
        BigDecimal net = sum(lines, Line::goods).subtract(sum(lines, Line::discounts)).subtract(sum(lines, Line::commission)).subtract(refunds);
        return AdminUserService.map("orders", lines.size(), "merchandise", sum(lines, Line::goods), "shopDiscounts", sum(lines, Line::discounts),
                "commissionRate", rate, "commission", sum(lines, Line::commission), "refunds", refunds, "netPayout", net);
    }

    private Map<String, Object> detailOf(Settlement s) {
        Map<String, Object> m = view(s);
        List<Order> orders = orderRepository.findBySettlementId(s.getId());
        Map<Long, BigDecimal> refundOf = new HashMap<>();
        List<Refund> deducted = refundRepository.findBySettlementId(s.getId());
        deducted.forEach(r -> refundOf.merge(r.getOrder().getId(), r.getAmount(), BigDecimal::add));
        m.put("orders", orders.stream().sorted(Comparator.comparing(Order::getDeliveredAt)).map(o -> {
            BigDecimal goods = o.getSubtotal();
            BigDecimal disc = nz(o.getComboDiscount()).add(nz(o.getShopVoucherDiscount()));
            BigDecimal commission = o.getCommissionFee() != null ? o.getCommissionFee()
                    : goods.subtract(disc).max(BigDecimal.ZERO).multiply(s.getCommissionRate()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            return AdminUserService.map("id", o.getId(), "code", o.getCode(), "deliveredAt", o.getDeliveredAt(), "merchandise", goods,
                    "shopDiscounts", disc, "commission", commission, "net", goods.subtract(disc).subtract(commission));
        }).toList());
        m.put("refundLines", deducted.stream().map(RefundService::view).toList());
        return m;
    }

    static Map<String, Object> view(Settlement s) {
        return AdminUserService.map("id", s.getId(), "code", s.getCode(), "shopId", s.getShop().getId(), "shopName", s.getShop().getName(),
                "periodTo", s.getPeriodTo(), "orderCount", s.getOrderCount(), "merchandise", s.getMerchandise(), "shopDiscounts", s.getShopDiscounts(),
                "commissionRate", s.getCommissionRate(), "commission", s.getCommission(), "refunds", s.getRefunds(), "netPayout", s.getNetPayout(),
                "status", s.getStatus().name(), "paymentReference", s.getPaymentReference(), "paidAt", s.getPaidAt(), "createdAt", s.getCreatedAt());
    }

    private static <T> BigDecimal sum(List<T> rows, java.util.function.Function<T, BigDecimal> f) {
        return rows.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id()).orElseThrow(() -> bad("Bạn chưa có shop"));
    }

    private Settlement find(Long id) {
        return settlementRepository.findById(id).orElseThrow(() -> new ApiException("Bảng đối soát không tồn tại", HttpStatus.NOT_FOUND));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
