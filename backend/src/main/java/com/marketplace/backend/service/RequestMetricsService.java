package com.marketplace.backend.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;

/**
 * Request metrics kept in memory since the server started (no database writes on the hot path):
 * per-minute counters for the last 24 hours, per-endpoint statistics, and the latest slow requests.
 * Filled by RequestMetricsFilter.
 */
@Component
public class RequestMetricsService {

    static final long SLOW_MS = 1000;
    private static final int KEEP_MINUTES = 24 * 60;
    private static final int KEEP_SLOW = 50;

    static final class Stat {
        final AtomicLong count = new AtomicLong();
        final AtomicLong errors4 = new AtomicLong();
        final AtomicLong errors5 = new AtomicLong();
        final AtomicLong totalMs = new AtomicLong();
        final LongAccumulator maxMs = new LongAccumulator(Long::max, 0);
        volatile long lastAt;

        void add(int status, long ms) {
            count.incrementAndGet();
            if (status >= 500) errors5.incrementAndGet();
            else if (status >= 400) errors4.incrementAndGet();
            totalMs.addAndGet(ms);
            maxMs.accumulate(ms);
            lastAt = System.currentTimeMillis();
        }
    }

    record Slow(long at, String method, String path, int status, long ms) {
    }

    private final long startedAt = System.currentTimeMillis();
    private final Map<Long, Stat> minutes = new ConcurrentHashMap<>();
    private final Map<String, Stat> endpoints = new ConcurrentHashMap<>();
    private final Deque<Slow> slow = new ConcurrentLinkedDeque<>();

    public void record(String method, String endpoint, String path, int status, long ms) {
        long minute = System.currentTimeMillis() / 60_000;
        minutes.computeIfAbsent(minute, k -> {
            // a new minute: drop what is older than 24 hours
            minutes.keySet().removeIf(m -> m < k - KEEP_MINUTES);
            return new Stat();
        }).add(status, ms);
        endpoints.computeIfAbsent(method + " " + endpoint, k -> new Stat()).add(status, ms);
        if (ms >= SLOW_MS) {
            slow.addFirst(new Slow(System.currentTimeMillis(), method, path, status, ms));
            while (slow.size() > KEEP_SLOW) slow.pollLast();
        }
    }

    public long startedAt() {
        return startedAt;
    }

    /** The last `window` minutes: totals, a series (one point per `step` minutes), endpoints and slow requests. */
    public Map<String, Object> snapshot(int window) {
        return snapshot(window, null);
    }

    /** q: only the endpoints containing this text (e.g. "/api/products") in the endpoint lists. */
    public Map<String, Object> snapshot(int window, String q) {
        int w = Math.min(Math.max(window, 5), KEEP_MINUTES);
        int step = w <= 60 ? 1 : w <= 360 ? 5 : 15;
        long now = System.currentTimeMillis() / 60_000;
        long count = 0, e4 = 0, e5 = 0, total = 0, max = 0;
        List<Map<String, Object>> series = new ArrayList<>();
        for (long start = now - w + 1; start <= now; start += step) {
            long c = 0, err = 0, t = 0, mx = 0;
            for (long m = start; m < start + step && m <= now; m++) {
                Stat s = minutes.get(m);
                if (s == null) continue;
                c += s.count.get();
                err += s.errors5.get();
                t += s.totalMs.get();
                mx = Math.max(mx, s.maxMs.get());
                e4 += s.errors4.get();
            }
            count += c;
            e5 += err;
            total += t;
            max = Math.max(max, mx);
            series.add(AdminUserService.map("label", time(start * 60_000), "requests", c, "errors", err,
                    "avgMs", c == 0 ? null : t / c));
        }
        List<Map<String, Object>> eps = new ArrayList<>();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        endpoints.forEach((k, s) -> {
            if (!needle.isEmpty() && !k.toLowerCase(Locale.ROOT).contains(needle)) return;
            long c = s.count.get();
            eps.add(AdminUserService.map("endpoint", k, "count", c, "errors4", s.errors4.get(), "errors5", s.errors5.get(),
                    "avgMs", c == 0 ? 0 : s.totalMs.get() / c, "maxMs", s.maxMs.get(), "lastAt", time(s.lastAt)));
        });
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("windowMinutes", w);
        m.put("stepMinutes", step);
        m.put("requests", count);
        m.put("perMinute", Math.round(count * 10.0 / w) / 10.0);
        m.put("errors4xx", e4);
        m.put("errors5xx", e5);
        m.put("errorRate", count == 0 ? 0 : Math.round(e5 * 1000.0 / count) / 10.0);
        m.put("avgMs", count == 0 ? null : total / count);
        m.put("maxMs", max);
        m.put("series", series);
        m.put("busiest", eps.stream().sorted(Comparator.comparing((Map<String, Object> x) -> (Long) x.get("count")).reversed()).limit(10).toList());
        m.put("slowest", eps.stream().filter(x -> (Long) x.get("count") >= 3)
                .sorted(Comparator.comparing((Map<String, Object> x) -> (Long) x.get("avgMs")).reversed()).limit(10).toList());
        m.put("failing", eps.stream().filter(x -> (Long) x.get("errors5") > 0)
                .sorted(Comparator.comparing((Map<String, Object> x) -> (Long) x.get("errors5")).reversed()).limit(10).toList());
        m.put("slowRequests", slow.stream().limit(20).map(s -> AdminUserService.map("at", time(s.at()), "method", s.method(), "path", s.path(),
                "status", s.status(), "ms", s.ms())).toList());
        m.put("slowThresholdMs", SLOW_MS);
        return m;
    }

    private static LocalDateTime time(long epochMs) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }
}
