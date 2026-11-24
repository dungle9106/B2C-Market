package com.marketplace.backend.service.forecast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StockForecasterTest {

    @Test
    void steadyDemandIsForecastAtItsLevel() {
        double[] y = new double[60];
        java.util.Arrays.fill(y, 10);
        StockForecaster.Fit f = StockForecaster.fit(y, 0);
        assertEquals(10, f.forecast(1), 0.5);
        assertEquals(300, f.total(30), 15);
    }

    @Test
    void growingDemandIsForecastHigherThanItsAverage() {
        double[] y = new double[60];
        for (int i = 0; i < y.length; i++) y[i] = 2 + i * 0.3;   // 2 -> ~20 a day
        StockForecaster.Fit f = StockForecaster.fit(y, 0);
        double average = java.util.Arrays.stream(y).average().orElse(0);
        assertTrue(f.forecast(1) > average, "trend must lift the forecast above the plain average");
        assertTrue(f.forecast(1) > 16, "and close to the recent level (~20), not the 28-day average (~16)");
    }

    @Test
    void weekendPeaksAreLearned() {
        double[] y = new double[84];   // 12 weeks, Monday first; Saturday and Sunday sell 3x
        for (int i = 0; i < y.length; i++) y[i] = (i % 7 >= 5) ? 30 : 10;
        StockForecaster.Fit f = StockForecaster.fit(y, 0);
        assertTrue(f.seasonal());
        // the next day after 12 full weeks is a Monday: h = 6 is Saturday
        assertTrue(f.forecast(6) > 2 * f.forecast(1), "Saturday forecast should be well above Monday");
    }

    @Test
    void noSalesForecastsZeroAndShortHistoryWorks() {
        assertEquals(0, StockForecaster.fit(new double[30], 3).total(30), 1e-9);
        assertEquals(0, StockForecaster.fit(new double[0], 0).total(7), 1e-9);
        assertTrue(StockForecaster.fit(new double[]{1, 0, 2}, 0).forecast(1) >= 0);
    }

    @Test
    void serviceLevelZ() {
        assertEquals(1.6449, StockForecaster.z(95), 1e-4);
        assertTrue(StockForecaster.z(99) > StockForecaster.z(90));
    }
}
