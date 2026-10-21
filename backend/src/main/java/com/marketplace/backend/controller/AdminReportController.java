package com.marketplace.backend.controller;

import com.marketplace.backend.service.PlatformAnalyticsService;
import com.marketplace.backend.service.PlatformReportExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

// "Thong ke & Bao cao tong" (PERM_REPORTS): ?from=yyyy-MM-dd&to=yyyy-MM-dd (inclusive, max 366 days)&granularity=day|week|month
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
public class AdminReportController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PlatformAnalyticsService analytics;
    private final PlatformReportExcelService excel;

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                        @RequestParam(defaultValue = "day") String granularity) {
        return ResponseEntity.ok(analytics.overview(from, to, granularity));
    }

    // ?limit=10&productSort=units|revenue
    @GetMapping("/top")
    public ResponseEntity<Map<String, Object>> top(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(defaultValue = "10") int limit,
                                                   @RequestParam(defaultValue = "units") String productSort) {
        return ResponseEntity.ok(analytics.top(from, to, limit, productSort));
    }

    // default: the last 12 months, by month
    @GetMapping("/growth")
    public ResponseEntity<Map<String, Object>> growth(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                      @RequestParam(required = false) String granularity) {
        return ResponseEntity.ok(analytics.growth(from, to, granularity));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "day") String granularity) {
        byte[] body = excel.export(from, to, granularity);
        String name = "Shoppi_bao_cao_toan_san_" + (from != null ? from : "") + (to != null ? "_" + to : LocalDate.now()) + ".xlsx";
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
