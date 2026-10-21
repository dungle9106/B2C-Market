package com.marketplace.backend.repository;

import com.marketplace.backend.entity.AdClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AdClickRepository extends JpaRepository<AdClick, Long> {

    boolean existsByCampaignIdAndVisitorKeyAndCreatedAtAfter(Long campaignId, String visitorKey, LocalDateTime after);

    // [campaignId, charged amount] since a time (today's spend vs the daily budget)
    @Query("SELECT c.campaignId, SUM(c.cost) FROM AdClick c WHERE c.charged = true AND c.createdAt >= :since "
            + "AND c.campaignId IN :ids GROUP BY c.campaignId")
    List<Object[]> spendSince(@Param("ids") Collection<Long> ids, @Param("since") LocalDateTime since);

    // logged-in clickers, for attributing orders to the ad
    List<AdClick> findByCampaignIdAndUserIdIsNotNull(Long campaignId);
}
