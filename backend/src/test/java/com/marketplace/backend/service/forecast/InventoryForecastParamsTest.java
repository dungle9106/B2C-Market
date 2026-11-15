package com.marketplace.backend.service.forecast;

import com.marketplace.backend.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InventoryForecastParamsTest {

    @Test
    void defaultsAndLimits() {
        assertEquals(new InventoryForecastService.Params(7, 30, 95), InventoryForecastService.Params.of(null, null, null));
        assertThrows(ApiException.class, () -> InventoryForecastService.Params.of(0, 30, 95));
        assertThrows(ApiException.class, () -> InventoryForecastService.Params.of(7, 200, 95));
        assertThrows(ApiException.class, () -> InventoryForecastService.Params.of(7, 30, 80));
    }

    @Test
    void ruleAdviceNamesWhatToBuyFirst() {
        List<Map<String, Object>> items = List.of(
                Map.of("name", "Tai nghe", "suggestQty", 120L, "status", "OUT", "stock", 0, "reason", "Đã hết hàng"),
                Map.of("name", "Túi tote", "suggestQty", 0L, "status", "OVERSTOCK", "stock", 160, "reason", "Tồn nhiều"));
        String advice = InventoryForecastService.ruleAdvice(items, InventoryForecastService.Params.of(null, null, null));
        assertTrue(advice.contains("Cần nhập 1 mã hàng, tổng khoảng 120"), advice);
        assertTrue(advice.contains("Tai nghe: nhập 120"), advice);
        assertTrue(advice.contains("1 mã đang tồn nhiều"), advice);
    }
}
