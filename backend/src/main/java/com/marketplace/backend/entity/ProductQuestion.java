package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Public Q&A under a product: anyone logged in asks, the shop's seller answers.
@Entity
@Table(name = "product_questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asker_id", nullable = false)
    private User asker;

    @Column(nullable = false, length = 1000)
    private String question;

    @Column(length = 2000)
    private String answer;

    private LocalDateTime answeredAt;

    private LocalDateTime answerUpdatedAt;

    // hidden by the seller (spam, off-topic): not shown on the product page
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean hidden = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
