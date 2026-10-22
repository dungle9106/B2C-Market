package com.marketplace.backend.service.reviewhub;

import com.marketplace.backend.util.TextNormalizer;
import org.apache.poi.ss.usermodel.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Reads a review export (CSV or Excel) from a marketplace's seller centre into rows. Columns are recognised by
 * their header in Vietnamese or English ("Nội dung đánh giá", "Số sao", "Rating", "Tên sản phẩm", "Username"...),
 * so the files exported by Shopee, Lazada, Tiki or TikTok Shop and our own template all work without mapping.
 */
public final class ReviewFileParser {

    private ReviewFileParser() {
    }

    public static final int MAX_ROWS = 5000;

    public record Row(int line, Map<String, String> values) {
    }

    // field -> accepted headers (accent-free, lower case); exact match first, then "contains"
    static final Map<String, List<String>> HEADERS = new LinkedHashMap<>();

    static {
        HEADERS.put("rating", List.of("so sao", "rating", "sao", "diem", "star", "stars", "diem danh gia", "danh gia sao", "rating star", "xep hang"));
        HEADERS.put("content", List.of("noi dung", "noi dung danh gia", "binh luan", "comment", "review", "review content", "content",
                "nhan xet", "danh gia", "review text"));
        HEADERS.put("product", List.of("ten san pham", "san pham", "product", "product name", "item name", "ten hang", "item"));
        HEADERS.put("reviewer", List.of("nguoi mua", "ten nguoi mua", "khach hang", "username", "buyer", "ten dang nhap", "nguoi danh gia",
                "reviewer", "user", "buyer name"));
        HEADERS.put("time", List.of("thoi gian", "ngay", "ngay danh gia", "thoi gian danh gia", "time", "date", "created", "create time",
                "review time", "created at"));
        HEADERS.put("order", List.of("ma don", "ma don hang", "order", "order id", "order sn", "order number", "so don hang"));
        HEADERS.put("externalId", List.of("ma danh gia", "review id", "id danh gia", "comment id", "id"));
        HEADERS.put("media", List.of("so anh", "hinh anh", "anh", "images", "image", "media", "so hinh"));
        HEADERS.put("reply", List.of("phan hoi cua shop", "phan hoi", "seller reply", "reply", "tra loi"));
    }

    public static List<Row> parse(String filename, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        List<List<String>> cells = name.endsWith(".xlsx") || name.endsWith(".xls") ? excel(bytes) : csv(bytes);
        if (cells.isEmpty()) throw new IllegalArgumentException("File không có dữ liệu");
        Map<String, Integer> columns = columns(cells.get(0));
        if (!columns.containsKey("rating")) throw new IllegalArgumentException("Không tìm thấy cột số sao (ví dụ: \"Số sao\", \"Rating\")");
        if (!columns.containsKey("content") && !columns.containsKey("product")) {
            throw new IllegalArgumentException("Không tìm thấy cột nội dung đánh giá hoặc tên sản phẩm");
        }
        if (cells.size() - 1 > MAX_ROWS) throw new IllegalArgumentException("Mỗi lần nhập tối đa " + MAX_ROWS + " dòng");
        List<Row> rows = new ArrayList<>();
        for (int r = 1; r < cells.size(); r++) {
            List<String> line = cells.get(r);
            if (line.stream().allMatch(c -> c == null || c.isBlank())) continue;
            Map<String, String> v = new HashMap<>();
            columns.forEach((field, col) -> v.put(field, col < line.size() ? Objects.requireNonNullElse(line.get(col), "").trim() : ""));
            rows.add(new Row(r + 1, v));
        }
        return rows;
    }

    /** Which column holds each field (-1 when absent). The rating column is matched before "content" ("Đánh giá" is ambiguous). */
    static Map<String, Integer> columns(List<String> header) {
        List<String> h = header.stream().map(x -> TextNormalizer.normalize(Objects.requireNonNullElse(x, ""))
                .replaceAll("[^a-z0-9 ]", " ").replaceAll(" +", " ").trim()).toList();
        Map<String, Integer> out = new LinkedHashMap<>();
        Set<Integer> used = new HashSet<>();
        for (boolean exact : new boolean[]{true, false}) {
            for (Map.Entry<String, List<String>> e : HEADERS.entrySet()) {
                if (out.containsKey(e.getKey())) continue;
                for (int c = 0; c < h.size() && !out.containsKey(e.getKey()); c++) {
                    if (used.contains(c) || h.get(c).isEmpty()) continue;
                    String col = h.get(c);
                    boolean hit = exact ? e.getValue().contains(col)
                            : e.getValue().stream().anyMatch(a -> a.length() >= 3 && (" " + col + " ").contains(" " + a + " "));
                    if (hit) {
                        out.put(e.getKey(), c);
                        used.add(c);
                    }
                }
            }
        }
        return out;
    }

    // ---- values ----

    /** "5", "5.0", "5 sao", "★★★★☆" -> 1..5, or null */
    public static Integer rating(String v) {
        if (v == null || v.isBlank()) return null;
        long stars = v.chars().filter(ch -> ch == '★').count();
        if (stars > 0) return (int) Math.max(1, Math.min(5, stars));
        String digits = v.replace(',', '.').replaceAll("[^0-9.]", "");
        if (digits.isEmpty()) return null;
        try {
            double d = Double.parseDouble(digits);
            int r = (int) Math.round(d);
            return r >= 1 && r <= 5 ? r : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final List<DateTimeFormatter> DATE_TIMES = List.of(
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss"), DateTimeFormatter.ofPattern("d/M/yyyy H:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss"), DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm"),
            DateTimeFormatter.ofPattern("d-M-yyyy H:mm"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    private static final List<DateTimeFormatter> DATES = List.of(DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"), DateTimeFormatter.ofPattern("d-M-yyyy"));

    public static LocalDateTime time(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim().replace('T', ' ').replaceAll("\\.\\d+$", "").replaceAll("Z$", "");
        for (DateTimeFormatter f : DATE_TIMES) {
            try {
                return LocalDateTime.parse(s.replace(' ', f == DateTimeFormatter.ISO_LOCAL_DATE_TIME ? 'T' : ' '), f);
            } catch (Exception ignored) {
                // next format
            }
        }
        for (DateTimeFormatter f : DATES) {
            try {
                return LocalDate.parse(s, f).atTime(12, 0);
            } catch (Exception ignored) {
                // next format
            }
        }
        return null;
    }

    public static int media(String v) {
        if (v == null || v.isBlank()) return 0;
        String t = v.trim();
        if (t.matches("\\d+")) return Math.min(20, Integer.parseInt(t));
        // a list of image links
        return (int) Math.min(20, Arrays.stream(t.split("[,;\\s]+")).filter(x -> x.startsWith("http")).count());
    }

    // ---- file formats ----

    static List<List<String>> excel(byte[] bytes) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            List<List<String>> out = new ArrayList<>();
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                List<String> line = new ArrayList<>();
                for (int c = 0; c < Math.max(0, row.getLastCellNum()); c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) {
                        line.add("");
                    } else if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                        line.add(cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString());
                    } else {
                        line.add(fmt.formatCellValue(cell));
                    }
                }
                out.add(line);
                if (out.size() > MAX_ROWS + 1) break;
            }
            return out;
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được file Excel: " + e.getMessage());
        }
    }

    /** CSV with quotes, comma or semicolon separated, UTF-8 (with or without BOM). */
    static List<List<String>> csv(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        String firstLine = text.lines().findFirst().orElse("");
        char sep = firstLine.chars().filter(ch -> ch == ';').count() > firstLine.chars().filter(ch -> ch == ',').count() ? ';' : ',';
        List<List<String>> out = new ArrayList<>();
        List<String> line = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
            int ch;
            while ((ch = r.read()) != -1) {
                char c = (char) ch;
                if (quoted) {
                    if (c == '"') {
                        r.mark(1);
                        int next = r.read();
                        if (next == '"') cell.append('"');
                        else {
                            quoted = false;
                            if (next != -1) r.reset();
                        }
                    } else {
                        cell.append(c);
                    }
                } else if (c == '"') {
                    quoted = true;
                } else if (c == sep) {
                    line.add(cell.toString());
                    cell.setLength(0);
                } else if (c == '\n') {
                    line.add(cell.toString());
                    cell.setLength(0);
                    out.add(line);
                    line = new ArrayList<>();
                    if (out.size() > MAX_ROWS + 1) break;
                } else if (c != '\r') {
                    cell.append(c);
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được file CSV: " + e.getMessage());
        }
        if (cell.length() > 0 || !line.isEmpty()) {
            line.add(cell.toString());
            out.add(line);
        }
        return out;
    }
}
