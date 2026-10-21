package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// "Chat voi Shop": one thread per (buyer, shop) pair, reused for every product/order.
@Entity
@Table(name = "conversations", uniqueConstraints = @UniqueConstraint(columnNames = {"buyer_id", "shop_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    // denormalised preview for the conversation list
    @Column(length = 300)
    private String lastMessage;

    private LocalDateTime lastMessageAt;

    @Builder.Default
    private int buyerUnread = 0;

    @Builder.Default
    private int shopUnread = 0;

    // the buyer wrote last and no person from the shop has answered yet ("Chua tra loi");
    // automatic replies don't clear it
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean awaitingReply = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.lastMessageAt == null) this.lastMessageAt = this.createdAt;
    }
}
