package com.marketplace.backend.entity;

// Moderation state of a shop. Only APPROVED shops are visible to buyers.
public enum ShopStatus {
    PENDING,    // waiting for an admin (when the platform requires approval of new shops)
    APPROVED,
    REJECTED,   // the seller can fix the profile and submit again
    SUSPENDED   // taken down by an admin for a violation
}
