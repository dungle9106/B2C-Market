package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // snapshot fields so the order still displays correctly even if the
    // product is edited or deleted later
    private String productNameSnapshot;
    private String variantNameSnapshot;

    // plain id, not a FK: the seller may delete the variant later; used to restock on cancel
    private Long variantId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtPurchase;

    @Column(nullable = false)
    private Integer quantity;

    // bought at the flash sale price: counts against the flash quota / per-buyer limit
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean flashSale = false;
}
