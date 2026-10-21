package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ProductEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductEventRepository extends JpaRepository<ProductEvent, Long> {

    boolean existsByProductIdAndVisitorKeyAndTypeAndCreatedAtAfter(Long productId, String visitorKey,
                                                                 ProductEvent.Type type, LocalDateTime after);

    // rows of [productId, type, visitorKey, createdAt]
    @Query("SELECT e.productId, e.type, e.visitorKey, e.createdAt FROM ProductEvent e "
            + "WHERE e.shopId = :shopId AND e.createdAt >= :from AND e.createdAt < :to")
    List<Object[]> rowsForShop(@Param("shopId") Long shopId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT e.productId, e.type, e.visitorKey, e.createdAt FROM ProductEvent e WHERE e.createdAt >= :from AND e.createdAt < :to")
    List<Object[]> rowsAll(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
