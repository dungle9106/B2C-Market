package com.marketplace.backend.entity;

public enum ReturnStatus {
    REQUESTED,  // waiting for the seller
    REJECTED,   // seller said no; the buyer may still escalate (khieu nai)
    ESCALATED,  // complaint sent to the platform admin
    REFUNDED,   // money (and items, for RETURN_AND_REFUND) went back: final
    CLOSED      // ended without refund (withdrawn by buyer or rejected by admin): final
}
