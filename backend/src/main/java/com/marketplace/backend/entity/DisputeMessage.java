package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// A message in a dispute (an escalated return request): buyer, seller and Shoppi add explanations and evidence.
@Entity
@Table(name = "dispute_messages", indexes = @Index(name = "idx_dispute_messages_return", columnList = "return_request_id, id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeMessage {

    public enum Side { BUYER, SELLER, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_request_id", nullable = false)
    private Long returnRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Side side;

    private Long authorId;

    @Column(length = 200)
    private String authorName;

    @Column(length = 2000)
    private String content;

    @ElementCollection
    @CollectionTable(name = "dispute_message_attachments", joinColumns = @JoinColumn(name = "message_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", length = 1000)
    @Builder.Default
    private List<String> attachments = new ArrayList<>();

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
