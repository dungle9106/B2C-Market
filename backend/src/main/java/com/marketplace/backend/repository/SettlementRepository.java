package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Settlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    @Query(value = "SELECT s FROM Settlement s JOIN FETCH s.shop WHERE (:status IS NULL OR s.status = :status) "
            + "AND (:shopId IS NULL OR s.shop.id = :shopId)",
            countQuery = "SELECT COUNT(s) FROM Settlement s WHERE (:status IS NULL OR s.status = :status) AND (:shopId IS NULL OR s.shop.id = :shopId)")
    Page<Settlement> search(@Param("status") Settlement.Status status, @Param("shopId") Long shopId, Pageable pageable);

    List<Settlement> findByShopIdOrderByIdDesc(Long shopId);

    @Query("SELECT COALESCE(SUM(s.netPayout), 0) FROM Settlement s WHERE s.status = :status")
    BigDecimal sumNet(@Param("status") Settlement.Status status);

    @Query("SELECT COALESCE(SUM(s.netPayout), 0) FROM Settlement s WHERE s.shop.id = :shopId AND s.status = :status")
    BigDecimal sumNetForShop(@Param("shopId") Long shopId, @Param("status") Settlement.Status status);

    long countByStatus(Settlement.Status status);
}
