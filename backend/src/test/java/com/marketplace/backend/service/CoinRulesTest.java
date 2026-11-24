package com.marketplace.backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Shoppi Xu earned on an order: 1 % of what was paid, times the membership multiplier, rounded down. */
class CoinRulesTest {

    private final PlatformConfig config = mock(PlatformConfig.class);
    private final CoinService coins = new CoinService(null, null, null, null, null, config, null);

    @Test
    void onePercentOfThePaidAmount() {
        when(config.coinEarnPercent()).thenReturn(1);
        assertEquals(1_590, coins.earnedFor(BigDecimal.valueOf(159_000), 100));
        assertEquals(1_599, coins.earnedFor(BigDecimal.valueOf(159_999), 100), "rounded down, never more than earned");
    }

    @Test
    void membershipMultiplier() {
        when(config.coinEarnPercent()).thenReturn(1);
        assertEquals(2_385, coins.earnedFor(BigDecimal.valueOf(159_000), 150));   // Vang x1,5
        assertEquals(3_180, coins.earnedFor(BigDecimal.valueOf(159_000), 200));   // Kim Cuong x2
    }

    @Test
    void nothingForNothing() {
        when(config.coinEarnPercent()).thenReturn(1);
        assertEquals(0, coins.earnedFor(BigDecimal.ZERO, 200));
        assertEquals(0, coins.earnedFor(BigDecimal.valueOf(-5_000), 100));
        when(config.coinEarnPercent()).thenReturn(0);
        assertEquals(0, coins.earnedFor(BigDecimal.valueOf(1_000_000), 200), "earning switched off by the admin");
    }
}
