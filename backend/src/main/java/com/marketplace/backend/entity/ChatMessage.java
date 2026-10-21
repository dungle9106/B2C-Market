package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// A message in a buyer <-> shop conversation: text, an image, and/or a product or order card.
@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_conversation", columnList = "conversation_id, id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatSide senderSide;

    @Column(length = 2000)
    private String content;

    @Column(length = 1000)
    private String imageUrl;

    // "Dang hoi ve san pham nay" card
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // "Hoi ve don hang nay" card
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // sent by the shop's auto-reply, not by a person (not counted in the response rate)
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean autoReply = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
