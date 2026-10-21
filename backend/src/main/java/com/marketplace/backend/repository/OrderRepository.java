package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.entity.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    java.util.Optional<Order> findFirstByTrackingNumberAndShippingGateway(String trackingNumber, String shippingGateway);
    List<Order> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);
    List<Order> findByShopIdOrderByCreatedAtDesc(Long shopId);
    List<Order> findByShopIdAndStatus(Long shopId, OrderStatus status);
    Optional<Order> findByCode(String code);

    long countByShopId(Long shopId);

    List<Order> findByCheckoutCodeAndBuyerId(String checkoutCode, Long buyerId);

    List<Order> findByCheckoutCode(String checkoutCode);

    // unpaid online orders past the payment window
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);

    // per-buyer voucher usage (cancelled orders give the voucher back)
    long countByBuyerIdAndVoucherIdAndStatusNot(Long buyerId, Long voucherId, OrderStatus status);

    // a platform voucher is used once per checkout, even though it spans several orders
    @Query("SELECT COUNT(DISTINCT o.checkoutCode) FROM Order o WHERE o.buyer.id = :buyerId " +
           "AND o.platformVoucher.id = :voucherId AND o.status <> com.marketplace.backend.entity.OrderStatus.CANCELLED")
    long countPlatformVoucherUses(@Param("buyerId") Long buyerId, @Param("voucherId") Long voucherId);

    List<Order> findByStatus(OrderStatus status);

    // customers of a shop, told about its new vouchers
    @Query("SELECT DISTINCT o.buyer.id FROM Order o WHERE o.shop.id = :shopId " +
           "AND o.status <> com.marketplace.backend.entity.OrderStatus.CANCELLED")
    List<Long> findBuyerIdsOfShop(@Param("shopId") Long shopId);

    // seller's voucher statistics: [voucherId, orders, discount given, order value]
    @Query("SELECT o.voucher.id, COUNT(o), COALESCE(SUM(o.shopVoucherDiscount), 0), COALESCE(SUM(o.total), 0) FROM Order o "
            + "WHERE o.shop.id = :shopId AND o.voucher IS NOT NULL "
            + "AND o.status <> com.marketplace.backend.entity.OrderStatus.CANCELLED GROUP BY o.voucher.id")
    List<Object[]> voucherStats(@Param("shopId") Long shopId);

    // Shoppi Vouchers: [voucherId, checkouts, buyers, merchandise discount, shipping discount, order value] (not cancelled)
    @Query("SELECT o.platformVoucher.id, COUNT(DISTINCT o.checkoutCode), COUNT(DISTINCT o.buyer.id), COALESCE(SUM(o.platformDiscount), 0), "
            + "COALESCE(SUM(CASE WHEN o.platformVoucher.type = com.marketplace.backend.entity.VoucherType.SHIPPING THEN o.shippingDiscount ELSE 0 END), 0), "
            + "COALESCE(SUM(o.total), 0) FROM Order o WHERE o.platformVoucher IS NOT NULL "
            + "AND o.status NOT IN (com.marketplace.backend.entity.OrderStatus.CANCELLED, com.marketplace.backend.entity.OrderStatus.AWAITING_PAYMENT) "
            + "GROUP BY o.platformVoucher.id")
    List<Object[]> platformVoucherStats();

    // what one Shoppi Voucher has cost so far (for its budget); shipping vouchers count the shipping discount
    @Query("SELECT COALESCE(SUM(CASE WHEN o.platformVoucher.type = com.marketplace.backend.entity.VoucherType.SHIPPING "
            + "THEN o.shippingDiscount ELSE o.platformDiscount END), 0) FROM Order o WHERE o.platformVoucher.id = :voucherId "
            + "AND o.status <> com.marketplace.backend.entity.OrderStatus.CANCELLED")
    java.math.BigDecimal platformVoucherSpent(@Param("voucherId") Long voucherId);

    // "khach hang moi": the buyer never placed an order that went through
    @Query("SELECT COUNT(o) > 0 FROM Order o WHERE o.buyer.id = :buyerId "
            + "AND o.status NOT IN (com.marketplace.backend.entity.OrderStatus.CANCELLED, com.marketplace.backend.entity.OrderStatus.AWAITING_PAYMENT)")
    boolean hasPlacedOrder(@Param("buyerId") Long buyerId);

    // the chatbot's "where is my order" context
    List<Order> findTop5ByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    // shop service rating given by buyers with their reviews: [avg, count]
    @Query("SELECT AVG(o.shopRating), COUNT(o.shopRating) FROM Order o WHERE o.shop.id = :shopId AND o.shopRating IS NOT NULL")
    List<Object[]> shopRating(@Param("shopId") Long shopId);

    // seller analytics: [id, createdAt, total, status, buyerId, shopRating, shippingRating] of orders placed in [from, to)
    @Query("SELECT o.id, o.createdAt, o.total, o.status, o.buyer.id, o.shopRating, o.shippingRating FROM Order o "
            + "WHERE o.shop.id = :shopId AND o.createdAt >= :from AND o.createdAt < :to")
    List<Object[]> analyticsRows(@Param("shopId") Long shopId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // a buyer's orders with one shop (customer panel of the seller's chat)
    List<Order> findByBuyerIdAndShopIdOrderByCreatedAtDesc(Long buyerId, Long shopId);

    // promotion timing for a shop without enough history: the same rows for the whole marketplace
    @Query("SELECT o.id, o.createdAt, o.total, o.status, o.buyer.id, o.shopRating, o.shippingRating FROM Order o "
            + "WHERE o.createdAt >= :from AND o.createdAt < :to")
    List<Object[]> analyticsRowsAll(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ---- admin: every order on the platform ----

    // status: an OrderStatus name, or null = all; late = past the expected step time (see AdminOrderService)
    @Query(value = "SELECT o FROM Order o JOIN FETCH o.shop s JOIN FETCH o.buyer b LEFT JOIN FETCH o.payment p "
            + "WHERE (:status IS NULL OR o.status = :status) "
            + "AND (:late = FALSE OR (o.status = :pending AND o.createdAt < :pendingBefore) "
            + "  OR (o.status = :confirmed AND COALESCE(o.confirmedAt, o.createdAt) < :confirmedBefore) "
            + "  OR (o.status = :shipping AND COALESCE(o.shippedAt, o.createdAt) < :shippingBefore)) "
            + "AND (:method IS NULL OR p.method = :method) "
            + "AND (:shopId IS NULL OR s.id = :shopId) "
            + "AND o.createdAt >= :from AND o.createdAt < :to "
            + "AND (:q = '' OR LOWER(o.code) LIKE CONCAT('%', :q, '%') OR LOWER(b.email) LIKE CONCAT('%', :q, '%') "
            + "  OR LOWER(b.fullName) LIKE CONCAT('%', :q, '%') OR LOWER(s.name) LIKE CONCAT('%', :q, '%'))",
            countQuery = "SELECT COUNT(o) FROM Order o JOIN o.shop s JOIN o.buyer b LEFT JOIN o.payment p "
            + "WHERE (:status IS NULL OR o.status = :status) "
            + "AND (:late = FALSE OR (o.status = :pending AND o.createdAt < :pendingBefore) "
            + "  OR (o.status = :confirmed AND COALESCE(o.confirmedAt, o.createdAt) < :confirmedBefore) "
            + "  OR (o.status = :shipping AND COALESCE(o.shippedAt, o.createdAt) < :shippingBefore)) "
            + "AND (:method IS NULL OR p.method = :method) "
            + "AND (:shopId IS NULL OR s.id = :shopId) "
            + "AND o.createdAt >= :from AND o.createdAt < :to "
            + "AND (:q = '' OR LOWER(o.code) LIKE CONCAT('%', :q, '%') OR LOWER(b.email) LIKE CONCAT('%', :q, '%') "
            + "  OR LOWER(b.fullName) LIKE CONCAT('%', :q, '%') OR LOWER(s.name) LIKE CONCAT('%', :q, '%'))")
    Page<Order> searchForAdmin(@Param("status") OrderStatus status, @Param("late") boolean late,
                               @Param("pending") OrderStatus pending, @Param("pendingBefore") LocalDateTime pendingBefore,
                               @Param("confirmed") OrderStatus confirmed, @Param("confirmedBefore") LocalDateTime confirmedBefore,
                               @Param("shipping") OrderStatus shipping, @Param("shippingBefore") LocalDateTime shippingBefore,
                               @Param("method") PaymentMethod method, @Param("shopId") Long shopId,
                               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("q") String q, Pageable pageable);

    // [status, count, total] of orders placed in [from, to)
    @Query("SELECT o.status, COUNT(o), COALESCE(SUM(o.total), 0) FROM Order o WHERE o.createdAt >= :from AND o.createdAt < :to GROUP BY o.status")
    List<Object[]> statusTotals(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ---- settlements ----

    // completed orders not paid out yet, delivered before the cutoff (the hold period for returns has passed)
    @Query("SELECT o FROM Order o JOIN FETCH o.shop WHERE o.status = :delivered AND o.settlementId IS NULL AND o.deliveredAt < :cutoff")
    List<Order> unsettledDelivered(@Param("delivered") OrderStatus delivered, @Param("cutoff") LocalDateTime cutoff);

    List<Order> findBySettlementId(Long settlementId);

    @Query("SELECT o FROM Order o WHERE o.shop.id = :shopId AND o.status = :delivered AND o.settlementId IS NULL")
    List<Order> unsettledForShop(@Param("shopId") Long shopId, @Param("delivered") OrderStatus delivered);

    // ---- payment reconciliation ----

    @Query("SELECT o FROM Order o JOIN FETCH o.payment p WHERE o.createdAt >= :from AND o.createdAt < :to")
    List<Order> withPayment(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ---- platform analytics ----

    // [id, createdAt, total, status, buyerId, shopId, subtotal, platformDiscount, comboDiscount, shopVoucherDiscount] placed in [from, to)
    @Query("SELECT o.id, o.createdAt, o.total, o.status, o.buyer.id, o.shop.id, o.subtotal, o.platformDiscount, o.comboDiscount, "
            + "o.shopVoucherDiscount FROM Order o WHERE o.createdAt >= :from AND o.createdAt < :to")
    List<Object[]> platformRows(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // [buyerId, time of the buyer's first real purchase]
    @Query("SELECT o.buyer.id, MIN(o.createdAt) FROM Order o WHERE o.status NOT IN :notSale GROUP BY o.buyer.id")
    List<Object[]> firstPurchases(@Param("notSale") java.util.Collection<OrderStatus> notSale);

    // [shopId, average shop rating, ratings]
    @Query("SELECT o.shop.id, AVG(o.shopRating), COUNT(o.shopRating) FROM Order o WHERE o.shop.id IN :shopIds AND o.shopRating IS NOT NULL GROUP BY o.shop.id")
    List<Object[]> shopRatings(@Param("shopIds") java.util.Collection<Long> shopIds);
}
