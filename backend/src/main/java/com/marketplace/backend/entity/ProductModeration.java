package com.marketplace.backend.entity;

// Moderation state of a product. Only APPROVED products can be visible to buyers.
public enum ProductModeration {
    PENDING,   // waiting for an admin: approval required, or held by the automatic content check
    APPROVED,
    REJECTED,  // the seller edits it and submits again
    REMOVED    // taken down for a policy violation ("Go san pham vi pham")
}
