package com.marketplace.backend.entity;

public enum NotificationType {
    ORDER,      // order / return status updates
    PROMOTION,  // vouchers, campaigns broadcast by the platform or a shop
    MESSAGE,    // chat messages, answers to product questions
    SUPPORT,    // support ticket replies and status changes
    SYSTEM      // anything else from Shoppi
}
