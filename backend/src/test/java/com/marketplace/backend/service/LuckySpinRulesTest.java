package com.marketplace.backend.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Prize table, weighted draw and daily budget of "Vong quay may man". */
class LuckySpinRulesTest {

    @Test
    void weightsAddUpTo100Percent() {
        assertEquals(10_000, LuckySpinService.PRIZES.stream().mapToInt(LuckySpinService.Prize::weight).sum());
    }

    @Test
    void drawFollowsTheWeights() {
        // every number 0..9999 once = exactly the configured odds
        Map<String, Integer> count = new HashMap<>();
        for (int r = 0; r < 10_000; r++) count.merge(LuckySpinService.draw(r).code(), 1, Integer::sum);
        for (LuckySpinService.Prize p : LuckySpinService.PRIZES) assertEquals(p.weight(), count.get(p.code()), p.code());
    }

    @Test
    void averageCostOfATurnIsSmall() {
        double avg = LuckySpinService.PRIZES.stream().mapToDouble(p -> p.coins() * p.weight() / 10_000.0).sum();
        assertTrue(avg < 50, "a turn costs on average " + avg + " Xu");
    }

    @Test
    void budgetDowngradesBigPrizes() {
        var big = LuckySpinService.BY_CODE.get("X2000");
        assertEquals("X2000", LuckySpinService.fitBudget(big, 5_000).code());
        assertEquals("X500", LuckySpinService.fitBudget(big, 1_999).code(), "the biggest that still fits");
        assertEquals("X10", LuckySpinService.fitBudget(big, 15).code());
        assertEquals("NONE", LuckySpinService.fitBudget(big, 5).code(), "budget used up");
        // non-coin prizes do not use the budget
        assertEquals("FREESHIP", LuckySpinService.fitBudget(LuckySpinService.BY_CODE.get("FREESHIP"), 0).code());
    }

    @Test
    void winnerNamesAreMasked() {
        assertEquals("Ng***h", LuckySpinService.mask("Nguyễn Minh Anh"));
        assertEquals("A***", LuckySpinService.mask("An"));
        assertEquals("***", LuckySpinService.mask(""));
    }
}
