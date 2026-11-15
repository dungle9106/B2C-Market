package com.marketplace.backend.service.returnrisk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The explainable rules of the return abuse score (AI phat hien lam dung doi tra). */
class ReturnRiskRulesTest {

    /** an ordinary buyer: one-year account, a few orders, first return, own photo */
    private static ReturnRiskRules.Features honest() {
        return new ReturnRiskRules.Features(365, 6, 6, 0, 0, 0.0, 0, 0, 0, 0, 0,
                2 * 24 * 60, 0.3, 200_000, false, 1, 0, 0, 0, false, false, 0, 0, 0.02);
    }

    /** base 30 + the rules, like the rule engine without the models */
    private static int score(List<ReturnRiskRules.Signal> s) {
        return ReturnRiskRules.clamp(30 + s.stream().mapToInt(ReturnRiskRules.Signal::points).sum());
    }

    private static List<String> codes(List<ReturnRiskRules.Signal> s) {
        return s.stream().map(ReturnRiskRules.Signal::code).toList();
    }

    @Test
    void anOrdinaryReturnRaisesNothing() {
        assertTrue(ReturnRiskRules.evaluate(honest()).isEmpty());
    }

    @Test
    void serialAbuserIsHighRisk() {
        // 6 returns in 180 days out of 8 orders, same reason, the same photo again, same text, refund only, high value
        var f = new ReturnRiskRules.Features(200, 8, 8, 4, 6, 0.75, 4_000_000, 0.8, 5, 1, 0,
                30, 1.0, 1_500_000, true, 1, 1, 0, 0, true, false, 0, 0, 0.02);
        var s = ReturnRiskRules.evaluate(f);
        assertTrue(codes(s).containsAll(List.of("EVIDENCE_REUSED", "TEXT_REPEATED", "MANY_RETURNS", "HIGH_RATE", "SAME_REASON",
                "REFUND_HEAVY", "LOST_DISPUTES", "REFUND_ONLY_HIGH")), codes(s).toString());
        assertEquals("HIGH", ReturnRiskRules.level(score(s)));
    }

    @Test
    void evidenceCopiedFromAnotherBuyerWeighsMoreThanFromOneself() {
        var self = ReturnRiskRules.evaluate(new ReturnRiskRules.Features(365, 6, 6, 0, 0, 0, 0, 0, 0, 0, 0,
                2880, 0.3, 200_000, false, 1, 1, 0, 0, false, false, 0, 0, 0.02));
        var others = ReturnRiskRules.evaluate(new ReturnRiskRules.Features(365, 6, 6, 0, 0, 0, 0, 0, 0, 0, 0,
                2880, 0.3, 200_000, false, 1, 0, 1, 0, false, false, 0, 0, 0.02));
        assertTrue(score(others) > score(self));
    }

    @Test
    void aDefectiveProductProtectsHonestBuyers() {
        // many other buyers return the same product and the shop has many returns: likely the product, not the buyer
        var f = new ReturnRiskRules.Features(365, 6, 6, 1, 3, 0.5, 600_000, 0.5, 1, 0, 0,
                2880, 1.0, 300_000, false, 1, 0, 0, 0, false, false, 0, 5, 0.3);
        var s = ReturnRiskRules.evaluate(f);
        assertTrue(codes(s).containsAll(List.of("PRODUCT_WIDE", "SHOP_HIGH_RATE")));
        assertNotEquals("HIGH", ReturnRiskRules.level(score(s)));
    }

    @Test
    void aRateNeedsEnoughOrders() {
        // 2 orders, 1 return = 50 %, but two orders say nothing about a habit
        var f = new ReturnRiskRules.Features(365, 2, 2, 1, 1, 0.5, 0, 0, 0, 0, 0,
                2880, 0.3, 200_000, false, 1, 0, 0, 0, false, false, 0, 0, 0.02);
        assertFalse(codes(ReturnRiskRules.evaluate(f)).contains("HIGH_RATE"));
    }

    @Test
    void levelsAndClamp() {
        assertEquals("LOW", ReturnRiskRules.level(39));
        assertEquals("MEDIUM", ReturnRiskRules.level(40));
        assertEquals("HIGH", ReturnRiskRules.level(70));
        assertEquals(100, ReturnRiskRules.clamp(180));
        assertEquals(0, ReturnRiskRules.clamp(-20));
    }
}
