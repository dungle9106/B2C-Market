package com.marketplace.backend.util;

import java.text.Normalizer;
import java.util.Locale;

// "Áo Thun ĐỎ" -> "ao thun do": lets search match with or without Vietnamese accents.
public final class TextNormalizer {

    private TextNormalizer() {}

    public static String normalize(String text) {
        if (text == null) return null;
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")      // drop combining accent marks
                .replace('đ', 'd').replace('Đ', 'D');
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
