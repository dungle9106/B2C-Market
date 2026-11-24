package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Fingerprint of a photo / video sent as return evidence, to catch the same file used on several cases. */
@Entity
@Table(name = "return_evidence_hashes", indexes = {
        @Index(name = "idx_evidence_hash_return", columnList = "return_request_id"),
        @Index(name = "idx_evidence_hash_sha", columnList = "sha256")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnEvidenceHash {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_request_id", nullable = false)
    private Long returnRequestId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(nullable = false, length = 64)
    private String sha256;

    private Long dhash;            // images only

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
