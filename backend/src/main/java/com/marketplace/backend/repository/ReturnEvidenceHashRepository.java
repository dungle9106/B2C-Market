package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ReturnEvidenceHash;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReturnEvidenceHashRepository extends JpaRepository<ReturnEvidenceHash, Long> {

    List<ReturnEvidenceHash> findByReturnRequestId(Long returnRequestId);

    // other orders only: refiling the same photo for the same order after withdrawing is normal
    @Query("SELECT h FROM ReturnEvidenceHash h WHERE h.orderId <> :orderId AND h.createdAt >= :since ORDER BY h.id DESC")
    List<ReturnEvidenceHash> otherOrders(@Param("orderId") Long orderId, @Param("since") LocalDateTime since, Pageable pageable);
}
