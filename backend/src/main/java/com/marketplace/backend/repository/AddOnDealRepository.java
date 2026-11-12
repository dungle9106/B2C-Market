package com.marketplace.backend.repository;

import com.marketplace.backend.entity.AddOnDeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AddOnDealRepository extends JpaRepository<AddOnDeal, Long> {

    List<AddOnDeal> findByShopIdOrderByCreatedAtDesc(Long shopId);

    // a deleted product leaves the deals it was in
    @Modifying
    @Query(value = "DELETE FROM addon_deal_main_products WHERE product_id = :productId", nativeQuery = true)
    void removeMainProduct(@Param("productId") Long productId);

    @Query("SELECT d FROM AddOnDeal d WHERE d.shop.id = :shopId AND d.active = true AND d.startAt <= :now AND d.endAt > :now")
    List<AddOnDeal> runningForShop(@Param("shopId") Long shopId, @Param("now") LocalDateTime now);

    // deals unlocked by buying this product (shown on its page)
    @Query("SELECT DISTINCT d FROM AddOnDeal d JOIN d.mainProducts p WHERE p.id = :productId AND d.active = true "
            + "AND d.startAt <= :now AND d.endAt > :now")
    List<AddOnDeal> runningForMainProduct(@Param("productId") Long productId, @Param("now") LocalDateTime now);
}
