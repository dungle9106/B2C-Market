package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Category;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.ProductModeration;
import com.marketplace.backend.entity.ProductReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    List<Product> findByShopId(Long shopId);

    long countByShopIdAndActiveTrue(Long shopId);

    // seller's product list, newest first
    List<Product> findByShopIdOrderByCreatedAtDesc(Long shopId);

    List<Product> findByShopIdAndIdIn(Long shopId, Collection<Long> ids);

    // SKUs are unique within a shop
    boolean existsByShopIdAndSkuIgnoreCase(Long shopId, String sku);

    boolean existsByShopIdAndSkuIgnoreCaseAndIdNot(Long shopId, String sku, Long id);

    @Query("SELECT LOWER(p.sku) FROM Product p WHERE p.shop.id = :shopId AND p.sku IS NOT NULL")
    List<String> skusOfShop(@Param("shopId") Long shopId);

    long countByShopId(Long shopId);

    // ---- atomic stock moves (StockService), see ProductVariantRepository ----

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock - :qty, p.soldCount = p.soldCount + :qty WHERE p.id = :id AND p.stock >= :qty")
    int takeStock(@Param("id") Long id, @Param("qty") int qty);

    // returned items: back in stock and no longer counted as sold
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock + :qty, "
            + "p.soldCount = CASE WHEN p.soldCount >= :qty THEN p.soldCount - :qty ELSE 0 END WHERE p.id = :id")
    int putBackStock(@Param("id") Long id, @Param("qty") int qty);

    // manual adjustments (not a sale)
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id = :id AND p.stock + :qty >= 0")
    int addStock(@Param("id") Long id, @Param("qty") int qty);

    @Query("SELECT p.stock FROM Product p WHERE p.id = :id")
    Integer currentStock(@Param("id") Long id);

    // flash sale quota: take units at the flash price, or 0 rows when the quota is gone
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.flashSaleSold = p.flashSaleSold + :qty WHERE p.id = :id "
            + "AND (p.flashSaleStock IS NULL OR p.flashSaleSold + :qty <= p.flashSaleStock)")
    int takeFlashSale(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.flashSaleSold = CASE WHEN p.flashSaleSold >= :qty THEN p.flashSaleSold - :qty ELSE 0 END WHERE p.id = :id")
    int releaseFlashSale(@Param("id") Long id, @Param("qty") int qty);

    // shop policy "apply to all products"
    @Modifying
    @Query("UPDATE Product p SET p.returnDays = :days WHERE p.shop.id = :shopId")
    int updateReturnDaysOfShop(@Param("shopId") Long shopId, @Param("days") int days);

    List<Product> findByCategoryIdAndActiveTrue(Long categoryId);

    // JOIN FETCH shop: the chatbot builds its prompt from p.getShop() outside a transaction
    @Query("SELECT p FROM Product p JOIN FETCH p.shop WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Product> searchByKeyword(@Param("keyword") String keyword);

    // simple rule-based recommendation: other active products in same category
    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId " +
           "AND p.active = true AND p.id <> :excludeProductId ORDER BY p.soldCount DESC")
    List<Product> findSimilarByCategory(@Param("categoryId") Long categoryId,
                                         @Param("excludeProductId") Long excludeProductId);

    List<Product> findTop10ByActiveTrueOrderBySoldCountDesc();

    // flash sales running right now, ending soonest first
    @Query("SELECT p FROM Product p JOIN p.shop s WHERE p.active = true AND s.active = true " +
           "AND p.flashSalePrice IS NOT NULL AND p.flashSaleStart <= :now AND p.flashSaleEnd > :now " +
           "AND (p.flashSaleStock IS NULL OR p.flashSaleSold < p.flashSaleStock) " +
           "ORDER BY p.flashSaleEnd ASC, p.soldCount DESC")
    List<Product> findActiveFlashSales(@Param("now") LocalDateTime now, Pageable pageable);

    // autocomplete: names whose accent-free form matches the pattern, best sellers first
    @Query("SELECT p.name FROM Product p JOIN p.shop s WHERE p.active = true AND s.active = true " +
           "AND p.searchText LIKE :pattern ORDER BY p.soldCount DESC")
    List<String> suggestNames(@Param("pattern") String pattern, Pageable pageable);

    List<Product> findBySearchTextIsNull();

    // chatbot retrieval: products whose accent-free name contains one keyword (the service scores them)
    @Query("SELECT p FROM Product p JOIN FETCH p.shop s WHERE p.active = true AND s.active = true " +
           "AND p.searchText LIKE :pattern ORDER BY p.soldCount DESC")
    List<Product> findForChatbot(@Param("pattern") String pattern, Pageable pageable);

    // ---- AI price suggestion: comparable products of other shops ----

    @Query("SELECT p FROM Product p JOIN FETCH p.shop s LEFT JOIN FETCH p.category WHERE p.active = true AND s.active = true "
            + "AND s.id <> :shopId AND p.category.id IN :categoryIds")
    List<Product> comparablesInCategories(@Param("shopId") Long shopId, @Param("categoryIds") Collection<Long> categoryIds);

    @Query("SELECT p FROM Product p JOIN FETCH p.shop s LEFT JOIN FETCH p.category WHERE p.active = true AND s.active = true "
            + "AND s.id <> :shopId AND p.searchText LIKE :pattern ORDER BY p.soldCount DESC")
    List<Product> comparablesByWord(@Param("shopId") Long shopId, @Param("pattern") String pattern, Pageable pageable);

    // ---- admin: content moderation ----

    // status null = all; reported = only products with an open buyer report; q = accent-free name or shop name
    @Query(value = "SELECT p FROM Product p JOIN FETCH p.shop s LEFT JOIN FETCH p.category c "
            + "WHERE (:status IS NULL OR p.moderationStatus = :status) "
            + "AND (:reported = FALSE OR p.id IN (SELECT r.product.id FROM ProductReport r WHERE r.status = :open)) "
            + "AND (:categoryId IS NULL OR c.id = :categoryId OR c.parent.id = :categoryId) "
            + "AND (:q = '' OR p.searchText LIKE CONCAT('%', :q, '%') OR LOWER(s.name) LIKE CONCAT('%', :q, '%'))",
            countQuery = "SELECT COUNT(p) FROM Product p JOIN p.shop s LEFT JOIN p.category c "
            + "WHERE (:status IS NULL OR p.moderationStatus = :status) "
            + "AND (:reported = FALSE OR p.id IN (SELECT r.product.id FROM ProductReport r WHERE r.status = :open)) "
            + "AND (:categoryId IS NULL OR c.id = :categoryId OR c.parent.id = :categoryId) "
            + "AND (:q = '' OR p.searchText LIKE CONCAT('%', :q, '%') OR LOWER(s.name) LIKE CONCAT('%', :q, '%'))")
    Page<Product> searchForAdmin(@Param("status") ProductModeration status, @Param("reported") boolean reported,
                                 @Param("open") ProductReport.Status open, @Param("categoryId") Long categoryId,
                                 @Param("q") String q, Pageable pageable);

    long countByModerationStatus(ProductModeration status);

    long countByShopIdAndModerationStatus(Long shopId, ProductModeration status);

    // prices of live products in a category, to spot an abnormally low price
    @Query("SELECT p.price FROM Product p WHERE p.category.id = :categoryId AND p.active = true AND p.id <> :excludeId")
    List<BigDecimal> livePricesInCategory(@Param("categoryId") Long categoryId, @Param("excludeId") Long excludeId, Pageable pageable);

    // ---- admin: categories ----

    // [categoryId, number of products]
    @Query("SELECT p.category.id, COUNT(p) FROM Product p WHERE p.category IS NOT NULL GROUP BY p.category.id")
    List<Object[]> countPerCategory();

    @Modifying
    @Query("UPDATE Product p SET p.category = :to WHERE p.category = :from")
    int moveCategory(@Param("from") Category from, @Param("to") Category to);
}
