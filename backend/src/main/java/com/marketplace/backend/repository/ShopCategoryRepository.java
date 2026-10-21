package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ShopCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShopCategoryRepository extends JpaRepository<ShopCategory, Long> {

    List<ShopCategory> findByShopIdOrderByPositionAscIdAsc(Long shopId);

    boolean existsByShopIdAndNameIgnoreCase(Long shopId, String name);

    boolean existsByShopIdAndNameIgnoreCaseAndIdNot(Long shopId, String name, Long id);

    long countByShopId(Long shopId);

    // [categoryId, number of products]; activeOnly for the public shop page
    @Query("SELECT c.id, COUNT(p) FROM Product p JOIN p.shopCategories c WHERE c.shop.id = :shopId "
            + "AND (:activeOnly = false OR p.active = true) GROUP BY c.id")
    List<Object[]> productCounts(@Param("shopId") Long shopId, @Param("activeOnly") boolean activeOnly);

    @Modifying
    @Query(value = "DELETE FROM product_shop_categories WHERE shop_category_id = :id", nativeQuery = true)
    void detachProducts(@Param("id") Long id);
}
