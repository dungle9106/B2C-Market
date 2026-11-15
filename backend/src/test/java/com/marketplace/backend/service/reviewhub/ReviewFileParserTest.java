package com.marketplace.backend.service.reviewhub;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Reading review exports from other marketplaces (CSV / Excel cells). */
class ReviewFileParserTest {

    @Test
    void ratingInManyShapes() {
        assertEquals(5, ReviewFileParser.rating("5"));
        assertEquals(4, ReviewFileParser.rating("4,0"));
        assertEquals(3, ReviewFileParser.rating("3 sao"));
        assertEquals(4, ReviewFileParser.rating("★★★★☆"));
        assertNull(ReviewFileParser.rating("9"));
        assertNull(ReviewFileParser.rating("tốt"));
        assertNull(ReviewFileParser.rating(" "));
    }

    @Test
    void timeInManyShapes() {
        assertEquals(LocalDateTime.of(2026, 3, 5, 14, 30), ReviewFileParser.time("5/3/2026 14:30"));
        assertEquals(LocalDateTime.of(2026, 3, 5, 14, 30, 15), ReviewFileParser.time("2026-03-05T14:30:15.123Z"));
        assertEquals(LocalDateTime.of(2026, 3, 5, 12, 0), ReviewFileParser.time("2026-03-05"));
        assertNull(ReviewFileParser.time("hôm qua"));
    }

    @Test
    void mediaCount() {
        assertEquals(2, ReviewFileParser.media("2"));
        assertEquals(2, ReviewFileParser.media("https://a/1.jpg, https://a/2.jpg"));
        assertEquals(0, ReviewFileParser.media(""));
        assertEquals(20, ReviewFileParser.media("500"), "capped");
    }
}
