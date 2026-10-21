package com.marketplace.backend.entity;

public enum ReturnType {
    RETURN_AND_REFUND, // send the items back, then get the money back (items are restocked)
    REFUND_ONLY        // e.g. item missing from the parcel: money back, nothing to send
}
