package com.marketplace.backend.entity;

/** Who may use a shop voucher (Shopee's "voucher theo doi tuong"). */
public enum VoucherAudience {
    ALL("Mọi khách hàng"),
    FOLLOWERS("Người theo dõi shop"),
    NEW_CUSTOMERS("Khách mua lần đầu tại shop"),
    RETURNING_CUSTOMERS("Khách đã từng mua tại shop");

    private final String label;

    VoucherAudience(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
