package com.marketplace.backend.entity;

public enum OrderStatus {
    AWAITING_PAYMENT, // online payment not completed yet (auto-cancelled after a timeout)
    PENDING,        // cho xac nhan
    CONFIRMED,      // seller da xac nhan
    SHIPPING,       // dang giao
    DELIVERED,      // da giao
    CANCELLED,      // da huy
    RETURN_REQUESTED,
    REFUNDED
}
