package com.marketplace.backend.repository;

import com.marketplace.backend.entity.OrderItem;
import com.marketplace.backend.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    boolean existsByProductId(Long productId);

    // units a buyer already got at the flash price in the current flash sale
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi WHERE oi.product.id = :productId AND oi.order.buyer.id = :buyerId "
            + "AND oi.flashSale = true AND oi.order.createdAt >= :since "
            + "AND oi.order.status <> com.marketplace.backend.entity.OrderStatus.CANCELLED")
    long flashUnitsBought(@Param("productId") Long productId, @Param("buyerId") Long buyerId, @Param("since") LocalDateTime since);

    // order lines of a product since a time (ad conversions)
    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.order o WHERE oi.product.id = :productId AND o.createdAt >= :since "
            + "AND o.status <> com.marketplace.backend.entity.OrderStatus.CANCELLED")
    List<OrderItem> linesOfProductSince(@Param("productId") Long productId, @Param("since") LocalDateTime since);

    // [productId, variantId, quantity] still to be shipped: already taken out of stock, not yet handed over
    @Query("SELECT oi.product.id, oi.variantId, SUM(oi.quantity) FROM OrderItem oi WHERE oi.order.shop.id = :shopId "
            + "AND oi.order.status IN :statuses GROUP BY oi.product.id, oi.variantId")
    List<Object[]> quantitiesInOrders(@Param("shopId") Long shopId, @Param("statuses") Collection<OrderStatus> statuses);

    // seller analytics: [productId (null if deleted), productName, orderId, quantity, price, status, buyerId, createdAt]
    @Query("SELECT p.id, i.productNameSnapshot, o.id, i.quantity, i.priceAtPurchase, o.status, o.buyer.id, o.createdAt "
            + "FROM OrderItem i JOIN i.order o LEFT JOIN i.product p "
            + "WHERE o.shop.id = :shopId AND o.createdAt >= :from AND o.createdAt < :to")
    List<Object[]> analyticsRows(@Param("shopId") Long shopId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // a Shoppi Flash Sale slot's results: [units, revenue, orders] sold at the flash price during [from, to)
    @Query("SELECT COALESCE(SUM(oi.quantity), 0), COALESCE(SUM(oi.priceAtPurchase * oi.quantity), 0), COUNT(DISTINCT oi.order.id) "
            + "FROM OrderItem oi WHERE oi.flashSale = true AND oi.product.id IN :productIds AND oi.order.createdAt >= :from "
            + "AND oi.order.createdAt < :to AND oi.order.status <> com.marketplace.backend.entity.OrderStatus.CANCELLED")
    List<Object[]> flashResults(@Param("productIds") Collection<Long> productIds, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // platform analytics: [productId, name, thumbnail, shopId, shopName, categoryId, units, revenue, orders] of sales in [from, to)
    @Query("SELECT p.id, p.name, p.thumbnailUrl, o.shop.id, o.shop.name, c.id, SUM(i.quantity), SUM(i.priceAtPurchase * i.quantity), "
            + "COUNT(DISTINCT o.id) FROM OrderItem i JOIN i.order o JOIN i.product p LEFT JOIN p.category c "
            + "WHERE o.createdAt >= :from AND o.createdAt < :to AND o.status NOT IN :notSale "
            + "GROUP BY p.id, p.name, p.thumbnailUrl, o.shop.id, o.shop.name, c.id")
    List<Object[]> productSales(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                @Param("notSale") Collection<OrderStatus> notSale);

    // [shopId, units] of sales in [from, to)
    @Query("SELECT o.shop.id, SUM(i.quantity) FROM OrderItem i JOIN i.order o WHERE o.createdAt >= :from AND o.createdAt < :to "
            + "AND o.status NOT IN :notSale GROUP BY o.shop.id")
    List<Object[]> unitsByShop(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                               @Param("notSale") Collection<OrderStatus> notSale);
}
