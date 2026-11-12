package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ReturnRequest;
import com.marketplace.backend.entity.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    List<ReturnRequest> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<ReturnRequest> findByOrderIdIn(Collection<Long> orderIds);

    List<ReturnRequest> findByOrderShopIdOrderByCreatedAtDesc(Long shopId);

    List<ReturnRequest> findByStatusInOrderByCreatedAtAsc(Collection<ReturnStatus> statuses);

    long countByOrderShopIdAndStatus(Long shopId, ReturnStatus status);

    // ---- admin: disputes = return requests the buyer escalated (they keep the complaint once resolved) ----

    @Query(value = "SELECT r FROM ReturnRequest r JOIN FETCH r.order o JOIN FETCH o.shop JOIN FETCH o.buyer "
            + "WHERE r.complaint IS NOT NULL AND r.status IN :statuses",
            countQuery = "SELECT COUNT(r) FROM ReturnRequest r WHERE r.complaint IS NOT NULL AND r.status IN :statuses")
    Page<ReturnRequest> disputes(@Param("statuses") Collection<ReturnStatus> statuses, Pageable pageable);

    long countByComplaintIsNotNullAndStatus(ReturnStatus status);

    // ---- case history of the return abuse checks (ReturnRiskService) ----

    // requests never checked (closed ones too: they are case history for the models),
    // and open ones that became a dispute since their last check
    @Query("SELECT r.id FROM ReturnRequest r WHERE NOT EXISTS (SELECT a.id FROM ReturnRiskAssessment a WHERE a.returnRequestId = r.id) "
            + "OR (r.status IN :open AND EXISTS (SELECT a.id FROM ReturnRiskAssessment a WHERE a.returnRequestId = r.id AND a.updatedAt < r.escalatedAt)) ORDER BY r.id")
    List<Long> needingRiskCheck(@Param("open") Collection<ReturnStatus> open, Pageable pageable);

    @Query("SELECT r FROM ReturnRequest r JOIN FETCH r.order o WHERE o.buyer.id = :buyerId AND r.createdAt >= :since ORDER BY r.id DESC")
    List<ReturnRequest> byBuyerSince(@Param("buyerId") Long buyerId, @Param("since") LocalDateTime since);

    long countByOrderShopIdAndCreatedAtAfter(Long shopId, LocalDateTime since);

    @Query("SELECT COUNT(DISTINCT o.buyer.id) FROM ReturnRequest r JOIN r.order o WHERE o.recipientPhone = :phone AND o.buyer.id <> :buyerId AND r.createdAt >= :since")
    long otherBuyersWithPhone(@Param("phone") String phone, @Param("buyerId") Long buyerId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(DISTINCT r.order.buyer.id) FROM ReturnRequest r JOIN r.items ri, OrderItem oi "
            + "WHERE oi.id = ri.orderItemId AND oi.product.id IN :productIds AND r.order.buyer.id <> :buyerId AND r.createdAt >= :since")
    long otherBuyersReturning(@Param("productIds") Collection<Long> productIds, @Param("buyerId") Long buyerId, @Param("since") LocalDateTime since);

    // other orders only: refiling the same text for the same order after withdrawing is normal
    @Query("SELECT r FROM ReturnRequest r JOIN FETCH r.order o JOIN FETCH o.buyer WHERE r.id <> :id AND o.id <> :orderId "
            + "AND r.description IS NOT NULL AND r.createdAt >= :since ORDER BY r.id DESC")
    List<ReturnRequest> recentWithText(@Param("id") Long id, @Param("orderId") Long orderId, @Param("since") LocalDateTime since, Pageable pageable);

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<ReturnStatus> statuses);
}
