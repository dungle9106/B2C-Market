package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ProductQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductQuestionRepository extends JpaRepository<ProductQuestion, Long> {

    // product page: hidden questions are left out
    Page<ProductQuestion> findByProductIdAndHiddenFalseOrderByCreatedAtDesc(Long productId, Pageable pageable);

    long countByProductShopIdAndAnswerIsNullAndHiddenFalse(Long shopId);

    long countByProductShopIdAndAnswerIsNotNullAndHiddenFalse(Long shopId);

    long countByProductShopIdAndHiddenTrue(Long shopId);

    // seller's inbox. status: UNANSWERED (oldest first), ANSWERED, HIDDEN; q (lower case) matches the question or the product name
    @Query("SELECT q FROM ProductQuestion q WHERE q.product.shop.id = :shopId "
            + "AND ((:status = 'HIDDEN' AND q.hidden = TRUE) "
            + "  OR (:status = 'UNANSWERED' AND q.hidden = FALSE AND q.answer IS NULL) "
            + "  OR (:status = 'ANSWERED' AND q.hidden = FALSE AND q.answer IS NOT NULL)) "
            + "AND (:productId IS NULL OR q.product.id = :productId) "
            + "AND (:q = '' OR LOWER(q.question) LIKE CONCAT('%', :q, '%') OR LOWER(q.product.name) LIKE CONCAT('%', :q, '%')) "
            + "ORDER BY CASE WHEN :status = 'UNANSWERED' THEN q.createdAt END ASC, q.createdAt DESC")
    Page<ProductQuestion> findForShop(@Param("shopId") Long shopId, @Param("status") String status, @Param("productId") Long productId,
                                      @Param("q") String q, Pageable pageable);

    void deleteByProductId(Long productId);
}
