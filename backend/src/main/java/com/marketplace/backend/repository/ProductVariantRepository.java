package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    // ---- atomic stock moves (StockService): a single UPDATE, so concurrent orders can't both
    // take the last item. 0 rows updated = not enough stock.

    @Modifying(flushAutomatically = true)
    @Query("UPDATE ProductVariant v SET v.stock = v.stock - :qty WHERE v.id = :id AND v.stock >= :qty")
    int takeStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE ProductVariant v SET v.stock = v.stock + :qty WHERE v.id = :id AND v.stock + :qty >= 0")
    int addStock(@Param("id") Long id, @Param("qty") int qty);

    @Query("SELECT v.stock FROM ProductVariant v WHERE v.id = :id")
    Integer currentStock(@Param("id") Long id);
}
