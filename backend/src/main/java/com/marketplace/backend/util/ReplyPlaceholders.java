package com.marketplace.backend.util;

// Placeholders a seller can put in a reply template; filled per customer when the reply is sent.
public final class ReplyPlaceholders {

    private ReplyPlaceholders() {
    }

    public static String fill(String text, String customer, String shop, String product) {
        if (text == null) return null;
        return text.replace("{ten_khach}", orDefault(customer, "bạn"))
                .replace("{ten_shop}", orDefault(shop, "Shop"))
                .replace("{ten_san_pham}", orDefault(product, "sản phẩm"));
    }

    private static String orDefault(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }
}
