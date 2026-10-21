package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ticket_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    // null for automatic lines ("Trang thai da chuyen sang ...")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    private boolean staff;     // written by Shoppi support (an admin)
    private boolean systemLine; // automatic status-change line

    @Column(nullable = false, length = 4000)
    private String content;

    @ElementCollection
    @CollectionTable(name = "ticket_message_attachments", joinColumns = @JoinColumn(name = "ticket_message_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", length = 1000)
    @Builder.Default
    private List<String> attachmentUrls = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
