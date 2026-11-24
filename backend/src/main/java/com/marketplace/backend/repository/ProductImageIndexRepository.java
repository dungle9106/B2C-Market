package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.ProductImageIndex;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ProductImageIndexRepository extends JpaRepository<ProductImageIndex, Long> {

    // everything that can be compared (a readable image)
    @Query("SELECT i FROM ProductImageIndex i WHERE i.histogram IS NOT NULL")
    List<ProductImageIndex> findComparable();

    long countByHistogramIsNotNull();

    // MobileNetV2 vectors ("" marks an image the AI service could not read)
    @Query("SELECT COUNT(i) FROM ProductImageIndex i WHERE i.embedding IS NOT NULL AND i.embedding <> ''")
    long countEmbedded();

    @Query("SELECT i FROM ProductImageIndex i WHERE i.embedding IS NOT NULL AND i.embedding <> ''")
    List<ProductImageIndex> findEmbedded();

    // images the AI service has not seen yet (it was off, or the image is new)
    @Query("SELECT i FROM ProductImageIndex i WHERE i.embedding IS NULL ORDER BY i.productId")
    List<ProductImageIndex> needingEmbedding(Pageable pageable);

    // [productId, categoryId], for favouring the categories the photo looks like
    @Query("SELECT p.id, p.category.id FROM Product p WHERE p.id IN :ids")
    List<Object[]> categoriesOf(@org.springframework.data.repository.query.Param("ids") Collection<Long> ids);

    // products whose cover image is not indexed yet, or changed since
    @Query("SELECT p FROM Product p WHERE p.thumbnailUrl IS NOT NULL AND NOT EXISTS "
            + "(SELECT 1 FROM ProductImageIndex i WHERE i.productId = p.id AND i.imageUrl = p.thumbnailUrl) ORDER BY p.id")
    List<Product> needingIndex(Pageable pageable);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.thumbnailUrl IS NOT NULL AND p.active = true")
    long countProductsWithImage();
}
