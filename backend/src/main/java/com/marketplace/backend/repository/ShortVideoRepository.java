package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ShortVideo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ShortVideoRepository extends JpaRepository<ShortVideo, Long> {

    // the public feed: active videos of shops buyers can see; Pageable carries the order (hot or new)
    @Query(value = "SELECT v FROM ShortVideo v WHERE v.status = 'ACTIVE' AND v.shopId IN (SELECT s.id FROM Shop s WHERE s.active = true)",
            countQuery = "SELECT COUNT(v) FROM ShortVideo v WHERE v.status = 'ACTIVE' AND v.shopId IN (SELECT s.id FROM Shop s WHERE s.active = true)")
    Page<ShortVideo> feed(Pageable pageable);

    @Query(value = "SELECT v FROM ShortVideo v WHERE v.status = 'ACTIVE' AND :productId MEMBER OF v.productIds "
            + "AND v.shopId IN (SELECT s.id FROM Shop s WHERE s.active = true)",
            countQuery = "SELECT COUNT(v) FROM ShortVideo v WHERE v.status = 'ACTIVE' AND :productId MEMBER OF v.productIds "
                    + "AND v.shopId IN (SELECT s.id FROM Shop s WHERE s.active = true)")
    Page<ShortVideo> feedForProduct(@Param("productId") Long productId, Pageable pageable);

    @Query(value = "SELECT v FROM ShortVideo v WHERE v.status = 'ACTIVE' AND v.shopId = :shopId",
            countQuery = "SELECT COUNT(v) FROM ShortVideo v WHERE v.status = 'ACTIVE' AND v.shopId = :shopId")
    Page<ShortVideo> feedForShop(@Param("shopId") Long shopId, Pageable pageable);

    List<ShortVideo> findByShopIdOrderByIdDesc(Long shopId);

    long countByShopIdAndStatus(Long shopId, ShortVideo.Status status);

    Page<ShortVideo> findByStatus(ShortVideo.Status status, Pageable pageable);

    @Modifying
    @Query("UPDATE ShortVideo v SET v.viewCount = v.viewCount + 1 WHERE v.id = :id AND v.status = 'ACTIVE'")
    int addView(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ShortVideo v SET v.likeCount = v.likeCount + :delta WHERE v.id = :id AND v.likeCount + :delta >= 0")
    int addLikes(@Param("id") Long id, @Param("delta") long delta);

    // ---- likes: one row per (video, user), written with plain SQL so a double click can't count twice ----

    @Modifying
    @Query(value = "INSERT INTO short_video_likes (video_id, user_id, created_at) VALUES (:videoId, :userId, now()) "
            + "ON CONFLICT DO NOTHING", nativeQuery = true)
    int like(@Param("videoId") Long videoId, @Param("userId") Long userId);

    @Modifying
    @Query(value = "DELETE FROM short_video_likes WHERE video_id = :videoId AND user_id = :userId", nativeQuery = true)
    int unlike(@Param("videoId") Long videoId, @Param("userId") Long userId);

    @Query(value = "SELECT video_id FROM short_video_likes WHERE user_id = :userId AND video_id IN (:videoIds)", nativeQuery = true)
    List<Long> likedAmong(@Param("userId") Long userId, @Param("videoIds") Collection<Long> videoIds);

    @Modifying
    @Query(value = "DELETE FROM short_video_likes WHERE video_id = :videoId", nativeQuery = true)
    void deleteLikes(@Param("videoId") Long videoId);
}
