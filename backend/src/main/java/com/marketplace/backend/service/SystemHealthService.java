package com.marketplace.backend.service;

import com.marketplace.backend.entity.ErrorEvent;
import com.marketplace.backend.repository.ErrorEventRepository;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** "Giam sat he thong": is everything healthy right now - application, database, disk, AI - with the reasons if not. */
@Service
@RequiredArgsConstructor
public class SystemHealthService {

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final RequestMetricsService metrics;
    private final ErrorEventRepository errorRepository;
    private final AiOpsService aiOps;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public Map<String, Object> health() {
        List<Map<String, Object>> checks = new ArrayList<>();

        // ---- application / JVM ----
        Runtime rt = Runtime.getRuntime();
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long max = heap.getMax() > 0 ? heap.getMax() : rt.maxMemory();
        double heapPct = Math.round(heap.getUsed() * 1000.0 / max) / 10.0;
        double cpu = -1;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            cpu = Math.round(os.getProcessCpuLoad() * 1000) / 10.0;
        }
        long uptimeMs = System.currentTimeMillis() - metrics.startedAt();
        Map<String, Object> app = AdminUserService.map("startedAt", LocalDateTime.now().minus(Duration.ofMillis(uptimeMs)),
                "uptime", human(uptimeMs), "javaVersion", System.getProperty("java.version"), "springBootVersion", SpringBootVersion.getVersion(),
                "heapUsedMb", heap.getUsed() / 1048576, "heapMaxMb", max / 1048576, "heapPercent", heapPct,
                "threads", ManagementFactory.getThreadMXBean().getThreadCount(), "processors", rt.availableProcessors(),
                "cpuPercent", cpu < 0 ? null : cpu);
        checks.add(check("Bộ nhớ ứng dụng", heapPct < 85 ? "UP" : heapPct < 95 ? "WARN" : "DOWN", heapPct + "% heap đang dùng"));

        // ---- database ----
        Map<String, Object> db = new LinkedHashMap<>();
        try {
            long t = System.nanoTime();
            jdbc.queryForObject("SELECT 1", Integer.class);
            long ms = (System.nanoTime() - t) / 1_000_000;
            db.put("pingMs", ms);
            db.put("sizeMb", jdbc.queryForObject("SELECT pg_database_size(current_database()) / 1048576", Long.class));
            db.put("version", jdbc.queryForObject("SHOW server_version", String.class));
            if (dataSource instanceof HikariDataSource h && h.getHikariPoolMXBean() != null) {
                db.put("poolActive", h.getHikariPoolMXBean().getActiveConnections());
                db.put("poolIdle", h.getHikariPoolMXBean().getIdleConnections());
                db.put("poolMax", h.getMaximumPoolSize());
            }
            checks.add(check("Cơ sở dữ liệu", ms < 200 ? "UP" : "WARN", "Phản hồi trong " + ms + " ms"));
        } catch (Exception e) {
            db.put("error", e.getMessage());
            checks.add(check("Cơ sở dữ liệu", "DOWN", "Không kết nối được: " + e.getClass().getSimpleName()));
        }

        // ---- disk (uploaded files) ----
        File dir = new File(uploadDir).getAbsoluteFile();
        File probe = dir.exists() ? dir : dir.getParentFile() != null ? dir.getParentFile() : new File(".");
        long free = probe.getUsableSpace(), total = probe.getTotalSpace();
        double freePct = total == 0 ? 100 : Math.round(free * 1000.0 / total) / 10.0;
        Map<String, Object> disk = AdminUserService.map("path", dir.getPath(), "freeGb", Math.round(free / 1e8) / 10.0,
                "totalGb", Math.round(total / 1e8) / 10.0, "freePercent", freePct, "writable", probe.canWrite());
        checks.add(check("Ổ đĩa lưu tệp tải lên", !probe.canWrite() ? "DOWN" : freePct > 10 ? "UP" : "WARN",
                freePct + "% còn trống" + (probe.canWrite() ? "" : ", không ghi được")));

        // ---- traffic & errors (last hour) ----
        Map<String, Object> hour = metrics.snapshot(60);
        double errRate = ((Number) hour.get("errorRate")).doubleValue();
        checks.add(check("Tỷ lệ lỗi 5xx (60 phút)", errRate < 1 ? "UP" : errRate < 5 ? "WARN" : "DOWN", errRate + "% trên " + hour.get("requests") + " yêu cầu"));
        long open = errorRepository.countByStatus(ErrorEvent.Status.OPEN);
        checks.add(check("Lỗi chưa xử lý", open == 0 ? "UP" : "WARN", open + " nhóm lỗi đang mở"));

        // ---- AI ----
        var ai = aiOps.settings();
        String block = aiOps.blockReason(ai);
        checks.add(check("Trợ lý AI", block == null ? "UP" : "WARN", block == null ? "Sẵn sàng (" + ai.model() + ")" : block));

        String overall = checks.stream().anyMatch(c -> "DOWN".equals(c.get("status"))) ? "DOWN"
                : checks.stream().anyMatch(c -> "WARN".equals(c.get("status"))) ? "WARN" : "UP";
        return AdminUserService.map("status", overall, "checkedAt", LocalDateTime.now(), "checks", checks, "app", app, "database", db, "disk", disk,
                "traffic", AdminUserService.map("requests", hour.get("requests"), "perMinute", hour.get("perMinute"), "errorRate", errRate,
                        "avgMs", hour.get("avgMs")));
    }

    private static Map<String, Object> check(String name, String status, String detail) {
        return AdminUserService.map("name", name, "status", status, "detail", detail);
    }

    private static String human(long ms) {
        long s = ms / 1000, d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60;
        return (d > 0 ? d + " ngày " : "") + (h > 0 ? h + " giờ " : "") + m + " phút";
    }
}
