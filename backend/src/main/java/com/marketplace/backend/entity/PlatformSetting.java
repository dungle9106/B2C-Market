package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Platform-wide switches changed by admins at runtime, e.g. "shop.requireApproval".
@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", length = 1000)
    private String value;

    private LocalDateTime updatedAt;

    private Long updatedBy;
}
