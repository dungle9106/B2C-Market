package com.marketplace.backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * "Log he thong": keeps the latest application log lines in memory (our own INFO and up, everybody's WARN and up)
 * so admins can read them without server access. Hooked into Logback at startup.
 */
@Component
public class LogBufferService {

    private static final int KEEP = 2000;

    record Entry(long seq, long at, String level, String logger, String thread, String message, String error) {
    }

    private final Deque<Entry> entries = new ConcurrentLinkedDeque<>();
    private final AtomicLong seq = new AtomicLong();
    private final Map<String, AtomicLong> totals = new LinkedHashMap<>();
    private Appender appender;

    final class Appender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent e) {
            boolean ours = e.getLoggerName().startsWith("com.marketplace");
            if (!e.getLevel().isGreaterOrEqual(ours ? Level.INFO : Level.WARN)) return;
            entries.addFirst(new Entry(seq.incrementAndGet(), e.getTimeStamp(), e.getLevel().toString(), shortName(e.getLoggerName()),
                    e.getThreadName(), cut(e.getFormattedMessage(), 2000), error(e.getThrowableProxy())));
            while (entries.size() > KEEP) entries.pollLast();
            synchronized (totals) {
                totals.computeIfAbsent(e.getLevel().toString(), k -> new AtomicLong()).incrementAndGet();
            }
        }
    }

    @PostConstruct
    void attach() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx)) return;
        appender = new Appender();
        appender.setContext(ctx);
        appender.setName("ADMIN_LOG_BUFFER");
        appender.start();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    @PreDestroy
    void detach() {
        if (appender != null && LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx) {
            ctx.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
            appender.stop();
        }
    }

    /** Newest first. level: the minimum level (INFO / WARN / ERROR); q: text in the message or logger. */
    public Map<String, Object> query(String level, String q, int limit) {
        Level min = Level.toLevel(level == null ? "INFO" : level, Level.INFO);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        int n = Math.min(Math.max(limit, 1), 500);
        List<Map<String, Object>> rows = entries.stream()
                .filter(e -> Level.toLevel(e.level()).isGreaterOrEqual(min))
                .filter(e -> needle.isEmpty() || e.message().toLowerCase(Locale.ROOT).contains(needle) || e.logger().toLowerCase(Locale.ROOT).contains(needle)
                        || (e.error() != null && e.error().toLowerCase(Locale.ROOT).contains(needle)))
                .limit(n)
                .map(e -> AdminUserService.map("seq", e.seq(), "time", LocalDateTime.ofInstant(Instant.ofEpochMilli(e.at()), ZoneId.systemDefault()),
                        "level", e.level(), "logger", e.logger(), "thread", e.thread(), "message", e.message(), "error", e.error()))
                .toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        synchronized (totals) {
            totals.forEach((k, v) -> counts.put(k, v.get()));
        }
        return AdminUserService.map("entries", rows, "buffered", entries.size(), "capacity", KEEP, "totalsSinceStart", counts);
    }

    private static String error(IThrowableProxy t) {
        if (t == null) return null;
        StringBuilder sb = new StringBuilder(t.getClassName()).append(": ").append(t.getMessage());
        StackTraceElementProxy[] frames = t.getStackTraceElementProxyArray();
        for (int i = 0; i < Math.min(5, frames.length); i++) sb.append("\n    at ").append(frames[i].getSTEAsString());
        return cut(sb.toString(), 3000);
    }

    // com.marketplace.backend.service.PaymentService -> c.m.b.s.PaymentService
    private static String shortName(String logger) {
        String[] parts = logger.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) sb.append(parts[i].isEmpty() ? "" : parts[i].charAt(0)).append('.');
        return sb.append(parts[parts.length - 1]).toString();
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
