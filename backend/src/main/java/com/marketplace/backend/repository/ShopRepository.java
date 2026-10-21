package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.entity.ShopStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShopRepository extends JpaRepository<Shop, Long> {
    Optional<Shop> findBySellerId(Long sellerId);

    // shop names are unique on the platform (case-insensitive)
    boolean existsByNameIgnoreCase(String name);

    boolean existsByIdAndSellerId(Long id, Long sellerId);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    // ad wallet: atomic, never below 0 (0 rows = not enough balance)
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Shop s SET s.adBalance = s.adBalance + :amount WHERE s.id = :id AND s.adBalance + :amount >= 0")
    int addAdBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Query("SELECT s.adBalance FROM Shop s WHERE s.id = :id")
    BigDecimal adBalance(@Param("id") Long id);

    // ---- admin: shop moderation ----

    @Query(value = "SELECT s FROM Shop s JOIN FETCH s.seller u WHERE (:status IS NULL OR s.status = :status) "
            + "AND (:q = '' OR LOWER(s.name) LIKE CONCAT('%', :q, '%') OR LOWER(u.email) LIKE CONCAT('%', :q, '%'))",
            countQuery = "SELECT COUNT(s) FROM Shop s JOIN s.seller u WHERE (:status IS NULL OR s.status = :status) "
                    + "AND (:q = '' OR LOWER(s.name) LIKE CONCAT('%', :q, '%') OR LOWER(u.email) LIKE CONCAT('%', :q, '%'))")
    Page<Shop> searchForAdmin(@Param("status") ShopStatus status, @Param("q") String q, Pageable pageable);

    long countByStatus(ShopStatus status);

    // platform analytics: when shops opened in [from, to)
    @org.springframework.data.jpa.repository.Query("SELECT s.createdAt FROM Shop s WHERE s.createdAt >= :from AND s.createdAt < :to")
    List<LocalDateTime> openedBetween(@org.springframework.data.repository.query.Param("from") LocalDateTime from,
                                      @org.springframework.data.repository.query.Param("to") LocalDateTime to);

    long countByCreatedAtBefore(LocalDateTime before);
}
