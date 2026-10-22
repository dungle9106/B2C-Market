package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ReviewHubItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewHubItemRepository extends JpaRepository<ReviewHubItem, Long>, JpaSpecificationExecutor<ReviewHubItem> {

    Optional<ReviewHubItem> findByReviewId(Long reviewId);

    Optional<ReviewHubItem> findByShopIdAndSourceAndExternalId(Long shopId, ReviewHubItem.Source source, String externalId);

    @Query("SELECT i.reviewId FROM ReviewHubItem i WHERE i.shopId = :shopId AND i.reviewId IS NOT NULL")
    List<Long> syncedReviewIds(@Param("shopId") Long shopId);

    @Query("SELECT i FROM ReviewHubItem i WHERE i.shopId = :shopId AND i.reviewedAt >= :from ORDER BY i.reviewedAt")
    List<ReviewHubItem> since(@Param("shopId") Long shopId, @Param("from") LocalDateTime from);

    @Query("SELECT i FROM ReviewHubItem i WHERE i.aiPending = true ORDER BY i.priority DESC, i.id")
    List<ReviewHubItem> aiPending(Pageable pageable);

    Page<ReviewHubItem> findByShopIdAndAnomalyStatusOrderByAnomalyScoreDescReviewedAtDesc(Long shopId, ReviewHubItem.AnomalyStatus status, Pageable pageable);

    List<ReviewHubItem> findByShopIdAndIdIn(Long shopId, Collection<Long> ids);

    @Query("SELECT i FROM ReviewHubItem i WHERE i.issuesManual = true OR i.analyzedBy = 'AI'")
    List<ReviewHubItem> labeled();
}
