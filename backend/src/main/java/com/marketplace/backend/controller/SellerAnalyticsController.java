package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.service.ReviewService;
import com.marketplace.backend.service.SellerAnalyticsService;
import com.marketplace.backend.service.SellerReportExcelService;
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

// "Thong ke & Bao cao": ?from=yyyy-MM-dd&to=yyyy-MM-dd (inclusive, max 366 days)&granularity=day|week|month
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerAnalyticsController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SellerAnalyticsService analytics;
    private final SellerReportExcelService excel;
    private final ReviewService reviewService;

    @GetMapping("/analytics/revenue")
    public ResponseEntity<Map<String, Object>> revenue(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                       @RequestParam(defaultValue = "day") String granularity) {
        return ResponseEntity.ok(analytics.revenue(from, to, granularity));
    }

    @GetMapping("/analytics/products")
    public ResponseEntity<Map<String, Object>> products(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(analytics.products(from, to));
    }

    @GetMapping("/analytics/traffic")
    public ResponseEntity<Map<String, Object>> traffic(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                       @RequestParam(defaultValue = "day") String granularity) {
        return ResponseEntity.ok(analytics.traffic(from, to, granularity));
    }

    @GetMapping("/analytics/reviews")
    public ResponseEntity<Map<String, Object>> reviews(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                       @RequestParam(defaultValue = "day") String granularity) {
        return ResponseEntity.ok(analytics.reviews(from, to, granularity));
    }

    @GetMapping("/analytics/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "day") String granularity) {
        byte[] body = excel.export(from, to, granularity);
        String name = "Shoppi_bao_cao_" + (from != null ? from : "") + (to != null ? "_" + to : LocalDate.now()) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    // ---- the shop's reviews: list + reply ----

    @GetMapping("/reviews")
    public ResponseEntity<PageResponse<Map<String, Object>>> shopReviews(@RequestParam(required = false) Integer rating,
                                                                         @RequestParam(required = false) Integer maxRating,
                                                                         @RequestParam(required = false) Long productId,
                                                                         @RequestParam(required = false) Boolean replied,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(reviewService.shopReviews(rating, maxRating, productId, replied, page, size));
    }

    @PutMapping("/reviews/{id}/reply")
    public ResponseEntity<Map<String, Object>> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(reviewService.reply(id, body.get("reply")));
    }
}
