package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByOrderIdOrderByIdAsc(Long orderId);

    List<Refund> findByOrderIdIn(Collection<Long> orderIds);

    boolean existsByOrderId(Long orderId);

    long countByStatus(Refund.Status status);

    @Query(value = "SELECT r FROM Refund r JOIN FETCH r.order o JOIN FETCH o.shop WHERE (:status IS NULL OR r.status = :status) "
            + "AND (:q = '' OR LOWER(o.code) LIKE CONCAT('%', :q, '%') OR LOWER(o.buyer.fullName) LIKE CONCAT('%', :q, '%') OR LOWER(COALESCE(r.reference, '')) LIKE CONCAT('%', :q, '%'))",
            countQuery = "SELECT COUNT(r) FROM Refund r JOIN r.order o WHERE (:status IS NULL OR r.status = :status) "
            + "AND (:q = '' OR LOWER(o.code) LIKE CONCAT('%', :q, '%') OR LOWER(o.buyer.fullName) LIKE CONCAT('%', :q, '%') OR LOWER(COALESCE(r.reference, '')) LIKE CONCAT('%', :q, '%'))")
    Page<Refund> search(@Param("status") Refund.Status status, @Param("q") String q, Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.status = :status AND r.createdAt >= :from AND r.createdAt < :to")
    BigDecimal sumByStatus(@Param("status") Refund.Status status, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // refunds a seller bears that no statement has deducted yet, for orders already paid out (or being paid out now)
    @Query("SELECT r FROM Refund r WHERE r.order.shop.id = :shopId AND r.settlementId IS NULL AND r.source <> :cancelled "
            + "AND r.order.settlementId IS NOT NULL")
    List<Refund> undeductedForShop(@Param("shopId") Long shopId, @Param("cancelled") Refund.Source cancelled);

    List<Refund> findBySettlementId(Long settlementId);

    List<Refund> findByStatusAndCreatedAtBefore(Refund.Status status, LocalDateTime before);
}
