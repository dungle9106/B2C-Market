package com.marketplace.backend.entity;

public enum VoucherType {
    PERCENT,  // value = % off merchandise, capped by maxDiscountAmount
    FIXED,    // value = VND off merchandise
    SHIPPING  // value = max VND off the shipping fee
}
