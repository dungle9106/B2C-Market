package com.marketplace.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// e.g. size = "M", color = "Do" for a t-shirt product
// DynamicUpdate: stock is also moved by atomic UPDATE statements (StockService); an entity
// update must only write the columns it changed, never an old stock value
@Entity
@DynamicUpdate
@Table(name = "product_variants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String name; // e.g. "Do / M": option1 + " / " + option2 when the product has tiers

    // values of the product's tier1Name / tier2Name, e.g. "Đỏ" and "M"
    @Column(length = 40)
    private String option1;

    @Column(length = 40)
    private String option2;

    @Column(length = 50)
    private String sku;

    // picture shown when the buyer picks this variant
    @Column(length = 1000)
    private String imageUrl;

    private BigDecimal extraPrice; // added on top of base product price, can be 0

    @Builder.Default
    private Integer stock = 0;
}
