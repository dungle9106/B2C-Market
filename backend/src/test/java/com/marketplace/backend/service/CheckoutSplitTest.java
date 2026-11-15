package com.marketplace.backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A platform voucher / Shoppi Xu discount is shared between the shops of one checkout. */
class CheckoutSplitTest {

    private static List<BigDecimal> bd(long... v) {
        return java.util.Arrays.stream(v).mapToObj(BigDecimal::valueOf).toList();
    }

    @Test
    void sharesFollowTheWeightsAndAddUpExactly() {
        List<BigDecimal> s = CheckoutService.split(BigDecimal.valueOf(30_000), bd(100_000, 200_000));
        assertEquals(bd(10_000, 20_000), s);
    }

    @Test
    void roundingLeftoverGoesToTheLargestShop() {
        // 10.000 over three equal-ish shops: 3.333 + 3.333 + 3.334, never a missing or extra dong
        List<BigDecimal> s = CheckoutService.split(BigDecimal.valueOf(10_000), bd(50_000, 50_001, 50_000));
        assertEquals(BigDecimal.valueOf(10_000), s.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(BigDecimal.valueOf(3_334), s.get(1));
        s.forEach(x -> assertEquals(0, x.scale(), "whole dong only"));
    }

    @Test
    void nothingToSplitGivesZeros() {
        assertEquals(bd(0, 0), CheckoutService.split(BigDecimal.ZERO, bd(1, 2)));
        assertEquals(bd(0, 0), CheckoutService.split(BigDecimal.TEN, bd(0, 0)));
    }
}
