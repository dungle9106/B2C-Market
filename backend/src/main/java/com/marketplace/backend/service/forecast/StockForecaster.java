package com.marketplace.backend.service.forecast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Demand forecast for one SKU from its daily sales (pure maths, no Spring: easy to test).
 *
 * Candidates, all multiplied by a day-of-week index (weekly season, when there is enough history):
 *  - Holt's exponential smoothing with a damped trend, for a small grid of smoothing weights (alpha, beta);
 *  - moving averages of the last 28 and 56 days (no trend) - hard to beat on small, noisy sales.
 * Model selection: each candidate is refitted at a few past cut-off dates ("rolling origin") and scored on how well
 * it predicted the next VALIDATION_DAYS, which is the horizon that matters for stock. The final forecast combines
 * the two best candidates (an average of forecasts is usually more accurate than either one).
 * The spread of the one-step errors gives the safety stock (demand varies around the forecast).
 */
public final class StockForecaster {

    private static final double[] ALPHAS = {0.05, 0.1, 0.2, 0.3};
    private static final double[] BETAS = {0.0, 0.02, 0.08};
    private static final double PHI = 0.9;          // trend damping: a trend fades instead of running forever
    static final int MIN_DAYS_FOR_SEASON = 28;
    static final int MIN_UNITS_FOR_SEASON = 20;
    static final int VALIDATION_DAYS = 14;

    private StockForecaster() {}

    /** fitted model: forecast(h) for h = 1.. days ahead */
    public record Fit(double level, double trend, double[] season, int nextWeekday, double sigma,
                      double alpha, double beta, boolean seasonal, int days, String method) {
        public double forecast(int h) {
            double damp = 0;
            double f = PHI;
            for (int i = 0; i < h; i++) { damp += f; f *= PHI; }
            double base = Math.max(0, level + trend * damp);
            return base * season[(nextWeekday + h - 1) % 7];
        }

        public double total(int days) {
            double s = 0;
            for (int h = 1; h <= days; h++) s += forecast(h);
            return s;
        }
    }

    /** one candidate model: its settings (alpha < 0 = moving average over `window` days) */
    private record Candidate(double alpha, double beta, int window) {
        String label() {
            return alpha < 0 ? "Trung bình " + window + " ngày" : "Holt (α=" + alpha + ", β=" + beta + ")";
        }
    }

    /**
     * @param sales        units sold per day, oldest first, the last entry is yesterday
     * @param firstWeekday day of week of sales[0], 0 = Monday
     */
    public static Fit fit(double[] sales, int firstWeekday) {
        int n = sales.length;
        double total = Arrays.stream(sales).sum();
        boolean seasonal = n >= MIN_DAYS_FOR_SEASON && total >= MIN_UNITS_FOR_SEASON;
        double[] season = seasonal ? weekdayIndex(sales, firstWeekday) : new double[]{1, 1, 1, 1, 1, 1, 1};
        if (n == 0) return new Fit(0, 0, season, firstWeekday, 0, 0, 0, false, 0, "Chưa có dữ liệu");

        List<Candidate> candidates = new ArrayList<>();
        for (double a : ALPHAS) for (double b : BETAS) candidates.add(new Candidate(a, b, 0));
        candidates.add(new Candidate(-1, 0, 28));
        candidates.add(new Candidate(-1, 0, 56));

        // score every candidate on the last few windows it did not see
        List<Integer> origins = new ArrayList<>();
        for (int o = n - VALIDATION_DAYS; o >= 21 && origins.size() < 3; o -= VALIDATION_DAYS) origins.add(o);
        double[] score = new double[candidates.size()];
        for (int c = 0; c < candidates.size(); c++) {
            for (int o : origins) {
                Fit f = run(Arrays.copyOf(sales, o), firstWeekday, season, seasonal, candidates.get(c));
                double actual = 0;
                for (int t = o; t < Math.min(n, o + VALIDATION_DAYS); t++) actual += sales[t];
                score[c] += Math.abs(actual - f.total(Math.min(VALIDATION_DAYS, n - o)));
            }
        }
        Integer[] order = new Integer[candidates.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(i -> score[i]));

        // the 28-day average is the anchor (index 12): the winner is combined with it, or, when the average itself
        // wins, with the runner-up - selection on a few windows is noisy, combining keeps the forecast steady
        int anchor = candidates.size() - 2;
        int first = order[0];
        int other = first == anchor ? order[1] : anchor;
        Fit best = run(sales, firstWeekday, season, seasonal, candidates.get(first));
        if (origins.isEmpty()) return run(sales, firstWeekday, season, seasonal, candidates.get(anchor));
        Fit second = run(sales, firstWeekday, season, seasonal, candidates.get(other));
        // combination of the two best: average level and trend (same season)
        return new Fit((best.level() + second.level()) / 2, (best.trend() + second.trend()) / 2, season,
                best.nextWeekday(), (best.sigma() + second.sigma()) / 2, best.alpha(), best.beta(), seasonal, n,
                "Kết hợp " + candidates.get(first).label() + " + " + candidates.get(other).label());
    }

    private static Fit run(double[] raw, int firstWeekday, double[] season, boolean seasonal, Candidate c) {
        int n = raw.length;
        int nextWeekday = (firstWeekday + n) % 7;
        double[] y = new double[n];
        for (int t = 0; t < n; t++) y[t] = raw[t] / season[(firstWeekday + t) % 7];

        if (c.alpha() < 0) {   // moving average of the de-seasonalised series
            int w = Math.min(c.window(), n);
            double mean = 0;
            for (int t = n - w; t < n; t++) mean += y[t];
            mean /= Math.max(1, w);
            double var = 0;
            for (int t = n - w; t < n; t++) {
                double e = raw[t] - mean * season[(firstWeekday + t) % 7];
                var += e * e;
            }
            double sigma = w > 1 ? Math.sqrt(var / (w - 1)) : Math.sqrt(mean);
            return new Fit(mean, 0, season, nextWeekday, sigma, 0, 0, seasonal, n, c.label());
        }

        double a = c.alpha(), b = c.beta();
        int init = Math.min(7, n);
        double level = 0;
        for (int i = 0; i < init; i++) level += y[i];
        level /= init;
        double trend = 0;
        double sse = 0;
        int count = 0;
        for (int t = 0; t < n; t++) {
            double predicted = Math.max(0, level + PHI * trend) * season[(firstWeekday + t) % 7];
            if (t >= init) {   // the one-step errors after the warm-up week give the spread
                double e = raw[t] - predicted;
                sse += e * e;
                count++;
            }
            double prevLevel = level;
            level = a * y[t] + (1 - a) * (level + PHI * trend);
            trend = b * (level - prevLevel) + (1 - b) * PHI * trend;
        }
        double sigma = count > 1 ? Math.sqrt(sse / (count - 1)) : Math.sqrt(Math.max(level, 0));  // Poisson-like guess when too short
        return new Fit(Math.max(0, level), trend, season, nextWeekday, sigma, a, b, seasonal, n, c.label());
    }

    /** day-of-week index, shrunk toward 1 when a weekday has few observations; averages 1 */
    static double[] weekdayIndex(double[] sales, int firstWeekday) {
        double mean = Arrays.stream(sales).average().orElse(0);
        if (mean <= 0) return new double[]{1, 1, 1, 1, 1, 1, 1};
        double[] sum = new double[7];
        int[] cnt = new int[7];
        for (int t = 0; t < sales.length; t++) {
            int d = (firstWeekday + t) % 7;
            sum[d] += sales[t];
            cnt[d]++;
        }
        double k = 4;   // pseudo-observations at the overall mean: a weekday needs real evidence to stand out
        double[] idx = new double[7];
        double s = 0;
        for (int d = 0; d < 7; d++) {
            idx[d] = ((sum[d] + k * mean) / (cnt[d] + k)) / mean;
            s += idx[d];
        }
        for (int d = 0; d < 7; d++) idx[d] = idx[d] * 7 / s;
        return idx;
    }

    /** z of the normal distribution for a service level (probability of not running out during the lead time) */
    public static double z(int serviceLevelPercent) {
        return switch (serviceLevelPercent) {
            case 90 -> 1.2816;
            case 99 -> 2.3263;
            default -> 1.6449;   // 95 %
        };
    }
}
