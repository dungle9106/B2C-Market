package com.marketplace.backend.service;

import com.marketplace.backend.entity.PriceWatch;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** "Bao khi giam gia": when a price change is worth a notification. */
class PriceWatchServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 12, 0);

    private static PriceWatch watch(long last, Long target, Long notifiedPrice, LocalDateTime notifiedAt) {
        return PriceWatch.builder().lastPrice(BigDecimal.valueOf(last)).watchedPrice(BigDecimal.valueOf(last))
                .targetPrice(target == null ? null : BigDecimal.valueOf(target))
                .notifiedPrice(notifiedPrice == null ? null : BigDecimal.valueOf(notifiedPrice)).notifiedAt(notifiedAt).build();
    }

    @Test
    void dropPercent() {
        assertEquals(new BigDecimal("20.0"), PriceWatchService.dropPercent(BigDecimal.valueOf(100_000), BigDecimal.valueOf(80_000)));
        assertTrue(PriceWatchService.dropPercent(BigDecimal.valueOf(100_000), BigDecimal.valueOf(110_000)).signum() < 0);
        assertEquals(BigDecimal.ZERO, PriceWatchService.dropPercent(BigDecimal.ZERO, BigDecimal.ONE));
    }

    @Test
    void aRealDropIsAnnounced() {
        assertTrue(PriceWatchService.shouldNotify(watch(350_000, null, null, null), BigDecimal.valueOf(280_000), NOW));
    }

    @Test
    void tinyChangesAreIgnored() {
        // less than 1 %: rounding of a price, not a deal
        assertFalse(PriceWatchService.shouldNotify(watch(350_000, null, null, null), BigDecimal.valueOf(348_000), NOW));
        assertFalse(PriceWatchService.shouldNotify(watch(350_000, null, null, null), BigDecimal.valueOf(360_000), NOW));
    }

    @Test
    void aTargetPriceMustBeReached() {
        assertFalse(PriceWatchService.shouldNotify(watch(790_000, 650_000L, null, null), BigDecimal.valueOf(700_000), NOW));
        assertTrue(PriceWatchService.shouldNotify(watch(790_000, 650_000L, null, null), BigDecimal.valueOf(650_000), NOW));
    }

    @Test
    void theSameDealIsNotAnnouncedTwiceWithinTheCooldown() {
        // flash sale ended (price went back up) and started again 3 hours later at the same price
        PriceWatch w = watch(350_000, null, 280_000L, NOW.minusHours(3));
        assertFalse(PriceWatchService.shouldNotify(w, BigDecimal.valueOf(280_000), NOW));
        // ...but a deeper drop is news
        assertTrue(PriceWatchService.shouldNotify(w, BigDecimal.valueOf(250_000), NOW));
        // ...and after the cooldown the same deal is announced again
        assertTrue(PriceWatchService.shouldNotify(watch(350_000, null, 280_000L, NOW.minusHours(13)), BigDecimal.valueOf(280_000), NOW));
    }
}
