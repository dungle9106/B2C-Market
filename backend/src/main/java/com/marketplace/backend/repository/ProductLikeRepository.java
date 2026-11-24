package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ProductLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductLikeRepository extends JpaRepository<ProductLike, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    long countByProductId(Long productId);

    @Modifying
    @Query("DELETE FROM ProductLike l WHERE l.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("DELETE FROM ProductLike l WHERE l.user.id = :userId AND l.product.id = :productId")
    int deleteByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    // the buyer's favourites, newest first (hidden / deleted products are filtered by the caller)
    @Query(value = "SELECT l FROM ProductLike l JOIN FETCH l.product p WHERE l.user.id = :userId ORDER BY l.createdAt DESC",
           countQuery = "SELECT COUNT(l) FROM ProductLike l WHERE l.user.id = :userId")
    Page<ProductLike> findMine(@Param("userId") Long userId, Pageable pageable);

    // which of these products the buyer liked (hearts on product cards)
    @Query("SELECT l.product.id FROM ProductLike l WHERE l.user.id = :userId AND l.product.id IN :productIds")
    List<Long> likedAmong(@Param("userId") Long userId, @Param("productIds") Collection<Long> productIds);

    // seller analytics: likes per product of a shop
    @Query("SELECT l.product.id, COUNT(l) FROM ProductLike l WHERE l.product.shop.id = :shopId GROUP BY l.product.id")
    List<Object[]> countsForShop(@Param("shopId") Long shopId);
}
