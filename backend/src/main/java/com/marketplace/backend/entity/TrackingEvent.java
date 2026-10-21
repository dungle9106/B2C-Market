package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// One line of an order's timeline: order placed, paid, confirmed, carrier
// scans (simulated), delivered, cancelled, return/refund steps...
@Entity
@Table(name = "tracking_events", indexes = @Index(columnList = "order_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // machine-readable step, e.g. PLACED, PICKED_UP, IN_TRANSIT; the simulator uses it to know what's done
    @Column(nullable = false, length = 30)
    private String stage;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 100)
    private String location;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
