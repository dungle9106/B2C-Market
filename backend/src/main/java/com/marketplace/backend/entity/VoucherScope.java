package com.marketplace.backend.entity;

public enum VoucherScope {
    SHOP,     // created by a seller, only valid on that shop's products
    PLATFORM  // created by admin, valid platform-wide
}
