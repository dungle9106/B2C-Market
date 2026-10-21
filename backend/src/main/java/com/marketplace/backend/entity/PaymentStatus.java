package com.marketplace.backend.entity;

public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    CANCELLED,
    REFUNDED   // paid online, then the order was cancelled
}
