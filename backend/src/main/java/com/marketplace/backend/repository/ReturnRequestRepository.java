package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ReturnRequest;
import com.marketplace.backend.entity.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<ReturnStatus> statuses);
}
