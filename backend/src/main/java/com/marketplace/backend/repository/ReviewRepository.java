package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    Page<Review> findByProductIdAndRatingOrderByCreatedAtDesc(Long productId, Integer rating, Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.product.id = :productId AND (SIZE(r.imageUrls) > 0 OR r.videoUrl IS NOT NULL) ORDER BY r.createdAt DESC")
    Page<Review> findWithMedia(@Param("productId") Long productId, Pageable pageable);

    // rows of [rating, count]
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.product.id = :productId GROUP BY r.rating")
    List<Object[]> countByRating(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.product.id = :productId AND (SIZE(r.imageUrls) > 0 OR r.videoUrl IS NOT NULL)")
    long countWithMedia(@Param("productId") Long productId);

    boolean existsByOrderItemId(Long orderItemId);

    @Query("SELECT r.orderItem.id FROM Review r WHERE r.orderItem.id IN :ids")
    List<Long> findReviewedOrderItemIds(@Param("ids") Collection<Long> ids);

    // ---- seller side ----

    // [id, productId, rating, createdAt, sellerReply] of the shop's reviews written in [from, to)
    @Query("SELECT r.id, r.product.id, r.rating, r.createdAt, r.sellerReply FROM Review r "
            + "WHERE r.product.shop.id = :shopId AND r.createdAt >= :from AND r.createdAt < :to")
    List<Object[]> analyticsRows(@Param("shopId") Long shopId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.product.shop.id = :shopId AND r.createdAt >= :from AND r.createdAt < :to "
            + "AND (SIZE(r.imageUrls) > 0 OR r.videoUrl IS NOT NULL)")
    long countWithMediaForShop(@Param("shopId") Long shopId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // filter: rating 1-5 or null, maxRating (e.g. 2 = negative) or null; replied true/false or null
    @Query("SELECT r FROM Review r WHERE r.product.shop.id = :shopId "
            + "AND (:rating IS NULL OR r.rating = :rating) "
            + "AND (:maxRating IS NULL OR r.rating <= :maxRating) "
            + "AND (:productId IS NULL OR r.product.id = :productId) "
            + "AND (:replied IS NULL OR (:replied = TRUE AND r.sellerReply IS NOT NULL) OR (:replied = FALSE AND r.sellerReply IS NULL)) "
            + "ORDER BY r.createdAt DESC")
    Page<Review> findForShop(@Param("shopId") Long shopId, @Param("rating") Integer rating, @Param("maxRating") Integer maxRating,
                             @Param("productId") Long productId,
                             @Param("replied") Boolean replied, Pageable pageable);

    long countByProductShopIdAndSellerReplyIsNull(Long shopId);

    long countByProductShopIdAndSellerReplyIsNullAndRatingLessThanEqual(Long shopId, Integer rating);

    // a buyer's reviews of one shop (customer panel in chat): [count, avg]
    @Query("SELECT COUNT(r), AVG(r.rating) FROM Review r WHERE r.buyer.id = :buyerId AND r.product.shop.id = :shopId")
    List<Object[]> buyerStatsForShop(@Param("buyerId") Long buyerId, @Param("shopId") Long shopId);

    // AI review insights: [id, productId, productName, rating, comment, createdAt, buyerName] of the shop, newest first
    @Query("SELECT r.id, r.product.id, r.product.name, r.rating, r.comment, r.createdAt, r.buyer.fullName FROM Review r "
            + "WHERE r.product.shop.id = :shopId AND r.createdAt >= :from AND (:productId IS NULL OR r.product.id = :productId) "
            + "ORDER BY r.createdAt DESC")
    List<Object[]> textsForShop(@Param("shopId") Long shopId, @Param("productId") Long productId, @Param("from") LocalDateTime from);
}
