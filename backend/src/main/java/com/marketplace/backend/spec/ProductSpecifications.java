package com.marketplace.backend.spec;

import com.marketplace.backend.dto.request.ProductSearchCriteria;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.util.TextNormalizer;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Builds the WHERE + ORDER BY for GET /api/products/search from the buyer's filters.
public final class ProductSpecifications {

    private static final char ESCAPE = '\\';

    private ProductSpecifications() {}

    public static Specification<Product> search(ProductSearchCriteria c, Collection<Long> categoryIds) {
        return (root, query, cb) -> {
            Join<Product, Shop> shop = root.join("shop");
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("active")));
            where.add(cb.isTrue(shop.get("active")));

            // every word of the keyword must appear in the accent-free name
            String keyword = TextNormalizer.normalize(c.getQ());
            if (keyword != null && !keyword.isEmpty()) {
                for (String word : keyword.split(" ")) {
                    where.add(cb.like(root.get("searchText"), "%" + escapeLike(word) + "%", ESCAPE));
                }
            }
            if (categoryIds != null) where.add(root.get("category").get("id").in(categoryIds));
            if (c.getShopId() != null) where.add(cb.equal(shop.get("id"), c.getShopId()));
            if (c.getShopCategoryId() != null) {
                where.add(cb.equal(root.join("shopCategories").get("id"), c.getShopCategoryId()));
            }

            // the price the buyer pays right now: flash sale price while it runs
            LocalDateTime now = LocalDateTime.now();
            Predicate flashActive = cb.and(
                    cb.isNotNull(root.get("flashSalePrice")),
                    cb.lessThanOrEqualTo(root.get("flashSaleStart"), now),
                    cb.greaterThan(root.get("flashSaleEnd"), now),
                    cb.or(cb.isNull(root.get("flashSaleStock")),
                            cb.lessThan(root.<Integer>get("flashSaleSold"), root.<Integer>get("flashSaleStock"))));
            Expression<BigDecimal> effectivePrice = cb.<BigDecimal>selectCase()
                    .when(flashActive, root.<BigDecimal>get("flashSalePrice"))
                    .otherwise(root.<BigDecimal>get("price"));

            if (c.getMinPrice() != null) where.add(cb.greaterThanOrEqualTo(effectivePrice, c.getMinPrice()));
            if (c.getMaxPrice() != null) where.add(cb.lessThanOrEqualTo(effectivePrice, c.getMaxPrice()));
            if (c.getMinRating() != null) where.add(cb.greaterThanOrEqualTo(root.get("ratingAvg"), c.getMinRating().doubleValue()));
            if (c.getProvinces() != null && !c.getProvinces().isEmpty()) where.add(shop.get("province").in(c.getProvinces()));
            if (Boolean.TRUE.equals(c.getFreeShipping())) where.add(cb.isTrue(root.get("freeShipping")));
            if (Boolean.TRUE.equals(c.getOnSale())) {
                where.add(cb.or(flashActive, cb.and(
                        cb.isNotNull(root.get("originalPrice")),
                        cb.greaterThan(root.<BigDecimal>get("originalPrice"), effectivePrice))));
            }

            // ORDER BY only on the data query, not on Spring's COUNT query for paging
            Class<?> resultType = query.getResultType();
            if (resultType != Long.class && resultType != long.class) {
                query.orderBy(order(c.getSort(), keyword, root, cb, effectivePrice));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    private static List<Order> order(String sort, String keyword, Root<Product> root,
                                     CriteriaBuilder cb, Expression<BigDecimal> effectivePrice) {
        List<Order> orders = new ArrayList<>();
        switch (sort == null ? "relevance" : sort) {
            case "newest" -> orders.add(cb.desc(root.get("createdAt")));
            case "bestselling" -> orders.add(cb.desc(root.get("soldCount")));
            case "price_asc" -> orders.add(cb.asc(effectivePrice));
            case "price_desc" -> orders.add(cb.desc(effectivePrice));
            case "rating" -> {
                orders.add(cb.desc(root.get("ratingAvg")));
                orders.add(cb.desc(root.get("ratingCount")));
            }
            default -> {
                // relevance: names starting with the keyword first, then best sellers
                if (keyword != null && !keyword.isEmpty()) {
                    orders.add(cb.asc(cb.<Integer>selectCase()
                            .when(cb.like(root.get("searchText"), escapeLike(keyword) + "%", ESCAPE), 0)
                            .otherwise(1)));
                }
                orders.add(cb.desc(root.get("soldCount")));
            }
        }
        orders.add(cb.desc(root.get("id"))); // stable paging
        return orders;
    }

    // so a typed "%" or "_" is matched literally instead of acting as a wildcard
    public static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
