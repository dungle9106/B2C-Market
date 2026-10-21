package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ProductReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ProductReportRepository extends JpaRepository<ProductReport, Long> {

    boolean existsByProductIdAndReporterIdAndStatus(Long productId, Long reporterId, ProductReport.Status status);

    long countByStatus(ProductReport.Status status);

    List<ProductReport> findByProductIdOrderByIdDesc(Long productId);

    // [productId, open reports] for a page of products
    @Query("SELECT r.product.id, COUNT(r) FROM ProductReport r WHERE r.status = :status AND r.product.id IN :ids GROUP BY r.product.id")
    List<Object[]> countOpenByProduct(@Param("status") ProductReport.Status status, @Param("ids") Collection<Long> ids);

    // close every open report of a product (removed -> RESOLVED, dismissed -> DISMISSED)
    @Modifying
    @Query("UPDATE ProductReport r SET r.status = :to, r.resolvedAt = :now WHERE r.product.id = :productId AND r.status = :from")
    int closeOpen(@Param("productId") Long productId, @Param("from") ProductReport.Status from, @Param("to") ProductReport.Status to,
                  @Param("now") LocalDateTime now);

    void deleteByProductId(Long productId);
}
