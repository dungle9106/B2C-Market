package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One turn of "Vong quay may man". The unique key (user, day) is what limits a buyer to one free turn a day,
 * even when two requests arrive at the same moment.
 */
@Entity
@Table(name = "lucky_spins",
        uniqueConstraints = @UniqueConstraint(name = "uk_lucky_spin_user_day", columnNames = {"user_id", "spin_date"}),
        indexes = @Index(name = "idx_lucky_spin_day", columnList = "spin_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LuckySpin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Vietnam date of the turn */
    @Column(name = "spin_date", nullable = false)
    private LocalDate spinDate;

    @Column(name = "prize_code", nullable = false, length = 20)
    private String prizeCode;

    /** Shoppi Xu given (0 for a voucher or "no luck") */
    @Column(nullable = false)
    private long coins;

    @Column(name = "voucher_code", length = 40)
    private String voucherCode;

    /** the prize first drawn, when the daily budget turned it into a smaller one */
    @Column(name = "drawn_code", length = 20)
    private String drawnCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
