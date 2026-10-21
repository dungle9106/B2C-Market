package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// One published version of a platform policy ("Chinh sach chung"); the newest version of a key is in force,
// older ones stay for the record.
@Entity
@Table(name = "policy_versions",
        uniqueConstraints = @UniqueConstraint(name = "uk_policy_version", columnNames = {"policy_key", "version"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_key", nullable = false, length = 30)
    private String policyKey;       // TERMS | PRIVACY | RETURN | SELLER | PROHIBITED

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 500)
    private String changeNote;      // what changed, shown to readers

    private Long publishedBy;
    private String publishedByName;

    @Column(nullable = false)
    private LocalDateTime publishedAt;
}
