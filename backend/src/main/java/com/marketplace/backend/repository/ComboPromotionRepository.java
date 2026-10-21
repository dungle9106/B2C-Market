package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ComboPromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ComboPromotionRepository extends JpaRepository<ComboPromotion, Long> {

    List<ComboPromotion> findByShopIdOrderByCreatedAtDesc(Long shopId);

    @Query("SELECT c FROM ComboPromotion c WHERE c.shop.id = :shopId AND c.active = true AND c.startAt <= :now AND c.endAt > :now")
    List<ComboPromotion> runningForShop(@Param("shopId") Long shopId, @Param("now") LocalDateTime now);

    // running or upcoming combos containing the product
    @Query("SELECT DISTINCT c FROM ComboPromotion c JOIN c.products p WHERE p.id = :productId AND c.active = true AND c.endAt > :now")
    List<ComboPromotion> liveForProduct(@Param("productId") Long productId, @Param("now") LocalDateTime now);
}
