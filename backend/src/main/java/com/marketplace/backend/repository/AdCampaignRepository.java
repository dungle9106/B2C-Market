package com.marketplace.backend.repository;

import com.marketplace.backend.entity.AdCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface AdCampaignRepository extends JpaRepository<AdCampaign, Long> {

    List<AdCampaign> findByShopIdOrderByCreatedAtDesc(Long shopId);

    boolean existsByProductIdAndStatusNot(Long productId, AdCampaign.Status status);

    // campaigns that may be shown now: active, product on sale and in stock, shop can pay one click
    @Query("SELECT c FROM AdCampaign c JOIN FETCH c.product p JOIN FETCH c.shop s "
            + "WHERE c.status = com.marketplace.backend.entity.AdCampaign.Status.ACTIVE AND p.active = true "
            + "AND s.active = true AND p.stock > 0 AND s.adBalance >= c.bidPerClick")
    List<AdCampaign> showable();

    @Modifying
    @Query("UPDATE AdCampaign c SET c.impressions = c.impressions + 1 WHERE c.id IN :ids")
    void countImpressions(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("UPDATE AdCampaign c SET c.clicks = c.clicks + 1, c.spend = c.spend + :cost WHERE c.id = :id")
    void countClick(@Param("id") Long id, @Param("cost") BigDecimal cost);
}
