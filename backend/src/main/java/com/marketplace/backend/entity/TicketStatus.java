package com.marketplace.backend.entity;

public enum TicketStatus {
    OPEN,              // new, waiting for support staff
    IN_PROGRESS,       // staff is handling it
    WAITING_CUSTOMER,  // staff asked the requester for more information
    RESOLVED,          // staff considers it solved; auto-closes after a few days
    CLOSED             // final
}
