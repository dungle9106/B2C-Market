package com.marketplace.backend.service.risk;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FraudRulesTest {

    private static Map<String, Double> usualOrder() {
        Map<String, Double> f = new HashMap<>();
        f.put("amount", 350.0);
        f.put("amountRatio", 1.1);
        f.put("accountAgeDays", 400.0);
        f.put("priorOrders", 12.0);
        f.put("ordersLastHour", 1.0);
        f.put("cod", 1.0);
        f.put("priorFailRatio", 0.0);
        f.put("phoneSharedAccounts", 0.0);
        f.put("discountRatio", 0.1);
        f.put("quantity", 2.0);
        return f;
    }

    @Test
    void aUsualOrderOfALoyalBuyerIsLowRisk() {
        FraudRules.Result r = FraudRules.evaluate(usualOrder());
        assertEquals(0, r.score());
        assertTrue(r.reasons().isEmpty());
        assertEquals("LOW", FraudRules.level(r.score()));
    }

    @Test
    void aTypicalRefusedCodPatternIsHighRiskWithReadableReasons() {
        Map<String, Double> f = usualOrder();
        f.put("accountAgeDays", 0.2);
        f.put("phoneSharedAccounts", 3.0);
        f.put("priorOrders", 4.0);
        f.put("priorFailRatio", 0.75);
        FraudRules.Result r = FraudRules.evaluate(f);
        assertEquals(25 + 20 + 25, r.score());
        assertEquals("HIGH", FraudRules.level(r.score()));
        assertTrue(r.reasons().contains("Tài khoản tạo chưa đến 1 ngày"));
        assertTrue(r.reasons().contains("SĐT nhận hàng được 3 tài khoản khác sử dụng"));
        assertTrue(r.reasons().contains("75% đơn trước đó bị hủy hoặc bom hàng"));
    }

    @Test
    void aBigBasketOnlyCountsAgainstTheBuyersOwnHabit() {
        Map<String, Double> f = usualOrder();
        f.put("amountRatio", 8.0);
        assertEquals(20, FraudRules.evaluate(f).score());
        f.put("priorOrders", 0.0); // first order: no habit to compare with
        assertEquals(0, FraudRules.evaluate(f).score());
    }

    @Test
    void theScoreIsCappedAt100() {
        Map<String, Double> f = usualOrder();
        f.putAll(Map.of("accountAgeDays", 0.1, "ordersLastHour", 5.0, "amountRatio", 9.0, "amount", 9000.0, "phoneSharedAccounts", 5.0,
                "priorFailRatio", 1.0, "discountRatio", 0.8, "newDeviceLogin", 1.0, "failedLogins24h", 6.0, "quantity", 40.0));
        assertEquals(100, FraudRules.evaluate(f).score());
    }

    @Test
    void theTrainedModelDecidesOnceThereIsOne() {
        assertEquals(30, FraudRules.combine(30, null, null));          // rules alone
        assertEquals(30, FraudRules.combine(30, null, 0.5));           // a usual order: not moved
        assertEquals(57, FraudRules.combine(30, null, 0.99));          // very unusual: pushed up
        assertEquals(85, FraudRules.combine(30, 0.85, 0.99));          // classifier probability
        assertEquals(35, FraudRules.combine(70, 0.05, null));          // the model clears it, the rules keep half
    }
}
