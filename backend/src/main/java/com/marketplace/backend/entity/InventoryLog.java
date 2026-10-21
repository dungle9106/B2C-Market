package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// One stock movement ("The kho"): every change of a product's / variant's stock and why.
@Entity
@Table(name = "inventory_logs", indexes = {
        @Index(name = "idx_inventory_logs_shop", columnList = "shop_id, id"),
        @Index(name = "idx_inventory_logs_product", columnList = "product_id, id")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    // plain ids + name snapshots: the history stays readable after a product or variant is deleted
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    private Long variantId;

    @Column(length = 100)
    private String variantName;

    @Column(name = "change_qty", nullable = false)
    private int change;          // + in, - out

    private int stockAfter;      // the product's (or variant's) stock right after this movement

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private InventoryReason reason;

    @Column(length = 40)
    private String orderCode;

    @Column(length = 300)
    private String note;

    private Long createdBy;      // user id; null for the system

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
