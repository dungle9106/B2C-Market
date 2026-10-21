package com.marketplace.backend.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

// Query params of GET /api/products/search (bound by Spring from ?q=...&minPrice=...).
@Data
public class ProductSearchCriteria {
    private String q;
    private Long categoryId;          // includes its sub-categories
    private Long shopId;
    private Long shopCategoryId;      // "Danh muc cua Shop" tab on the shop page
    private BigDecimal minPrice;      // on the price the buyer pays now
    private BigDecimal maxPrice;
    private Integer minRating;        // 1-5: "from N stars"
    private List<String> provinces;   // shop location
    private Boolean freeShipping;
    private Boolean onSale;           // discounted or in a running flash sale
    // relevance | newest | bestselling | price_asc | price_desc | rating
    private String sort = "relevance";
    private int page = 0;
    private int size = 30;
}
