package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.ProductImageIndex;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductImageIndexRepository extends JpaRepository<ProductImageIndex, Long> {

    // everything that can be compared (a readable image)
    @Query("SELECT i FROM ProductImageIndex i WHERE i.histogram IS NOT NULL")
    List<ProductImageIndex> findComparable();

    long countByHistogramIsNotNull();

    // products whose cover image is not indexed yet, or changed since
    @Query("SELECT p FROM Product p WHERE p.thumbnailUrl IS NOT NULL AND NOT EXISTS "
            + "(SELECT 1 FROM ProductImageIndex i WHERE i.productId = p.id AND i.imageUrl = p.thumbnailUrl) ORDER BY p.id")
    List<Product> needingIndex(Pageable pageable);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.thumbnailUrl IS NOT NULL AND p.active = true")
    long countProductsWithImage();
}
