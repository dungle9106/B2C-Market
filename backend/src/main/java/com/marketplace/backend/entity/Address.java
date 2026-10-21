package com.marketplace.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "addresses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // never serialized: the controller returns this entity as JSON, and the
    // lazy User proxy would fail outside the session (and leak the password hash)
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String recipientName;
    private String phone;
    private String addressLine;
    private String ward;
    private String district;
    private String province;

    @Builder.Default
    private boolean isDefault = false;
}
