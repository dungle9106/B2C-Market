package com.marketplace.backend.entity;

public enum VerificationStatus {
    PENDING,   // waiting for an admin
    APPROVED,
    REJECTED   // the seller may fix and submit again
}
