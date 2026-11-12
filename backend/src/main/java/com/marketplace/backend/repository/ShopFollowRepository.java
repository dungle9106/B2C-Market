package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ShopFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShopFollowRepository extends JpaRepository<ShopFollow, Long> {

    boolean existsByUserIdAndShopId(Long userId, Long shopId);

    long countByShopId(Long shopId);

    long countByShopIdAndCreatedAtAfter(Long shopId, LocalDateTime after);

    @Modifying
    @Query("DELETE FROM ShopFollow f WHERE f.user.id = :userId AND f.shop.id = :shopId")
    int deleteByUserAndShop(@Param("userId") Long userId, @Param("shopId") Long shopId);

    @Query(value = "SELECT f FROM ShopFollow f JOIN FETCH f.shop s WHERE f.user.id = :userId ORDER BY f.createdAt DESC",
           countQuery = "SELECT COUNT(f) FROM ShopFollow f WHERE f.user.id = :userId")
    Page<ShopFollow> findMine(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT f.shop.id FROM ShopFollow f WHERE f.user.id = :userId")
    List<Long> followedShopIds(@Param("userId") Long userId);

    // told about the shop's new vouchers
    @Query("SELECT f.user.id FROM ShopFollow f WHERE f.shop.id = :shopId AND f.user.enabled = true")
    List<Long> followerIds(@Param("shopId") Long shopId);
}
