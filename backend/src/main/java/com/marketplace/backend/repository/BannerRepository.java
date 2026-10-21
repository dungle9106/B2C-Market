package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findByPlacementOrderBySortOrderAscIdAsc(Banner.Placement placement);

    @Query("SELECT b FROM Banner b WHERE b.placement = :placement AND b.active = true "
            + "AND (b.startAt IS NULL OR b.startAt <= :now) AND (b.endAt IS NULL OR b.endAt > :now) ORDER BY b.sortOrder, b.id")
    List<Banner> showing(@Param("placement") Banner.Placement placement, @Param("now") LocalDateTime now);

    long countByCampaignId(Long campaignId);

    @Modifying
    @Query("UPDATE Banner b SET b.views = b.views + 1 WHERE b.id IN :ids")
    void addView(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("UPDATE Banner b SET b.clicks = b.clicks + 1 WHERE b.id = :id")
    int addClick(@Param("id") Long id);

    @Query("SELECT COALESCE(MAX(b.sortOrder), 0) FROM Banner b WHERE b.placement = :placement")
    int maxSort(@Param("placement") Banner.Placement placement);
}
