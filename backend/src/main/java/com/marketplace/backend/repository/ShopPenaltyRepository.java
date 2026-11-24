package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ShopPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShopPenaltyRepository extends JpaRepository<ShopPenalty, Long> {

    boolean existsByShopIdAndRefKey(Long shopId, String refKey);

    List<ShopPenalty> findByShopIdOrderByCreatedAtDesc(Long shopId);

    @Query("SELECT COALESCE(SUM(p.points), 0) FROM ShopPenalty p WHERE p.shop.id = :shopId AND p.revoked = false AND p.createdAt >= :from")
    long pointsSince(@Param("shopId") Long shopId, @Param("from") LocalDateTime from);

    // [shopId, points] for many shops
    @Query("SELECT p.shop.id, SUM(p.points) FROM ShopPenalty p WHERE p.shop.id IN :shopIds AND p.revoked = false AND p.createdAt >= :from GROUP BY p.shop.id")
    List<Object[]> pointsSince(@Param("shopIds") Collection<Long> shopIds, @Param("from") LocalDateTime from);

    // the latest, still counted, penalty for a taken-down product (undone when the product is restored)
    Optional<ShopPenalty> findFirstByShopIdAndCodeAndRefKeyStartingWithAndRevokedFalseOrderByCreatedAtDesc(
            Long shopId, ShopPenalty.Code code, String refKeyPrefix);
}
