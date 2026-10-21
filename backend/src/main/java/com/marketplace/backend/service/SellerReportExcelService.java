package com.marketplace.backend.service;

import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

// "Xuat bao cao": the four analytics pages of one period in a single .xlsx
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SellerReportExcelService {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> STATUS = Map.of(
            OrderStatus.AWAITING_PAYMENT.name(), "Chờ thanh toán", OrderStatus.PENDING.name(), "Chờ xác nhận",
            OrderStatus.CONFIRMED.name(), "Chờ lấy hàng", OrderStatus.SHIPPING.name(), "Đang giao",
            OrderStatus.DELIVERED.name(), "Đã giao", OrderStatus.CANCELLED.name(), "Đã hủy",
            OrderStatus.RETURN_REQUESTED.name(), "Yêu cầu trả hàng", OrderStatus.REFUNDED.name(), "Đã hoàn tiền");

    private final SellerAnalyticsService analytics;

    @SuppressWarnings("unchecked")
    public byte[] export(LocalDate from, LocalDate to, String granularity) {
        Shop shop = analytics.myShop();
        SellerAnalyticsService.Period p = analytics.period(from, to, granularity);
        Map<String, Object> revenue = analytics.revenue(shop.getId(), p);
        Map<String, Object> products = analytics.products(shop.getId(), p);
        Map<String, Object> traffic = analytics.traffic(shop.getId(), p);
        Map<String, Object> reviews = analytics.reviews(shop.getId(), p);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);

            // ---- overview: this period vs the previous one ----
            Sheet s = wb.createSheet("Tổng quan");
            Row title = s.createRow(0);
            cell(title, 0, "Báo cáo bán hàng - " + shop.getName(), st.title);
            cell(s.createRow(1), 0, "Kỳ báo cáo: " + p.from().format(D) + " - " + p.to().format(D)
                    + "   (so với " + p.previous().from().format(D) + " - " + p.previous().to().format(D) + ")", null);
            cell(s.createRow(2), 0, "Xuất lúc " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")), null);
            header(s, 4, st, "Chỉ số", "Kỳ này", "Kỳ trước", "Thay đổi");
            Map<String, Object> rs = (Map<String, Object>) revenue.get("summary"), rp = (Map<String, Object>) revenue.get("previous");
            Map<String, Object> ts = (Map<String, Object>) traffic.get("summary"), tp = (Map<String, Object>) traffic.get("previous");
            Map<String, Object> vs = (Map<String, Object>) reviews.get("summary"), vp = (Map<String, Object>) reviews.get("previous");
            Object[][] rows = {
                    {"Doanh số (đ)", rs.get("sales"), rp.get("sales"), st.money},
                    {"Số đơn hàng", rs.get("orders"), rp.get("orders"), null},
                    {"Sản phẩm đã bán", rs.get("items"), rp.get("items"), null},
                    {"Người mua", rs.get("buyers"), rp.get("buyers"), null},
                    {"Giá trị trung bình mỗi đơn (đ)", rs.get("avgOrderValue"), rp.get("avgOrderValue"), st.money},
                    {"Đơn hủy / hoàn tiền", rs.get("cancelledOrders"), rp.get("cancelledOrders"), null},
                    {"Lượt xem sản phẩm", ts.get("views"), tp.get("views"), null},
                    {"Lượt truy cập (khách)", ts.get("visitors"), tp.get("visitors"), null},
                    {"Tỷ lệ thêm vào giỏ (%)", ts.get("cartRate"), tp.get("cartRate"), null},
                    {"Tỷ lệ chuyển đổi (%)", ts.get("conversion"), tp.get("conversion"), null},
                    {"Số đánh giá mới", vs.get("count"), vp.get("count"), null},
                    {"Điểm đánh giá trung bình", vs.get("average"), vp.get("average"), null},
                    {"Tỷ lệ phản hồi đánh giá (%)", vs.get("replyRate"), vp.get("replyRate"), null}};
            int r = 5;
            for (Object[] row : rows) {
                Row x = s.createRow(r++);
                cell(x, 0, (String) row[0], null);
                num(x, 1, row[1], (CellStyle) row[3]);
                num(x, 2, row[2], (CellStyle) row[3]);
                double a = number(row[1]), b = number(row[2]);
                if (b != 0) num(x, 3, (a - b) / b, st.percent);
                else cell(x, 3, a == 0 ? "—" : "Mới", null);
            }
            s.setColumnWidth(0, 34 * 256);
            for (int c = 1; c <= 3; c++) s.setColumnWidth(c, 18 * 256);

            // ---- revenue by day/week/month + by status ----
            s = wb.createSheet("Doanh thu");
            header(s, 0, st, "Kỳ", "Doanh số (đ)", "Số đơn", "Sản phẩm đã bán", "Người mua");
            r = 1;
            for (Map<String, Object> b : (List<Map<String, Object>>) revenue.get("series")) {
                Row x = s.createRow(r++);
                cell(x, 0, (String) b.get("label"), null);
                num(x, 1, b.get("sales"), st.money);
                num(x, 2, b.get("orders"), null);
                num(x, 3, b.get("items"), null);
                num(x, 4, b.get("buyers"), null);
            }
            Row total = s.createRow(r++);
            cell(total, 0, "Tổng", st.bold);
            num(total, 1, rs.get("sales"), st.money);
            num(total, 2, rs.get("orders"), null);
            num(total, 3, rs.get("items"), null);
            num(total, 4, rs.get("buyers"), null);
            header(s, ++r, st, "Trạng thái đơn", "Giá trị (đ)", "Số đơn");
            for (Map<String, Object> b : (List<Map<String, Object>>) revenue.get("statuses")) {
                Row x = s.createRow(++r);
                cell(x, 0, STATUS.getOrDefault((String) b.get("status"), (String) b.get("status")), null);
                num(x, 1, b.get("value"), st.money);
                num(x, 2, b.get("orders"), null);
            }
            widths(s, 20, 18, 12, 16, 12);

            // ---- products: sales, traffic, stock ----
            s = wb.createSheet("Sản phẩm");
            header(s, 0, st, "ID", "Sản phẩm", "Trạng thái", "Giá (đ)", "Tồn kho", "Đủ bán (ngày)", "Lượt xem", "Lượt truy cập",
                    "Thêm vào giỏ", "Người mua", "Đã bán (kỳ)", "Doanh số (đ)", "Chuyển đổi (%)", "Đánh giá TB", "Số đánh giá");
            r = 1;
            for (Map<String, Object> pr : (List<Map<String, Object>>) products.get("products")) {
                Row x = s.createRow(r++);
                num(x, 0, pr.get("id"), null);
                cell(x, 1, (String) pr.get("name"), null);
                int stock = ((Number) pr.get("stock")).intValue();
                cell(x, 2, !(Boolean) pr.get("active") ? "Đã ẩn" : stock <= 0 ? "Hết hàng"
                        : stock <= SellerAnalyticsService.LOW_STOCK ? "Sắp hết" : "Đang bán", null);
                num(x, 3, pr.get("price"), st.money);
                num(x, 4, stock, null);
                num(x, 5, pr.get("daysOfStock"), null);
                num(x, 6, pr.get("views"), null);
                num(x, 7, pr.get("visitors"), null);
                num(x, 8, pr.get("cartVisitors"), null);
                num(x, 9, pr.get("buyers"), null);
                num(x, 10, pr.get("units"), null);
                num(x, 11, pr.get("sales"), st.money);
                num(x, 12, pr.get("conversion"), null);
                num(x, 13, pr.get("ratingAvg"), null);
                num(x, 14, pr.get("ratingCount"), null);
            }
            widths(s, 8, 40, 12, 14, 10, 12, 10, 12, 12, 11, 11, 16, 13, 11, 11);
            s.createFreezePane(2, 1);

            // ---- traffic by day/week/month ----
            s = wb.createSheet("Lượt truy cập");
            header(s, 0, st, "Kỳ", "Lượt xem", "Lượt truy cập", "Thêm vào giỏ", "Người mua", "Chuyển đổi (%)");
            r = 1;
            for (Map<String, Object> b : (List<Map<String, Object>>) traffic.get("series")) {
                Row x = s.createRow(r++);
                cell(x, 0, (String) b.get("label"), null);
                num(x, 1, b.get("views"), null);
                num(x, 2, b.get("visitors"), null);
                num(x, 3, b.get("cartVisitors"), null);
                num(x, 4, b.get("buyers"), null);
                num(x, 5, b.get("conversion"), null);
            }
            widths(s, 20, 12, 14, 14, 12, 14);

            // ---- reviews ----
            s = wb.createSheet("Đánh giá");
            header(s, 0, st, "Số sao", "Số đánh giá");
            r = 1;
            for (Map.Entry<Integer, Long> e : ((Map<Integer, Long>) reviews.get("distribution")).entrySet()) {
                Row x = s.createRow(r++);
                cell(x, 0, e.getKey() + " sao", null);
                num(x, 1, e.getValue(), null);
            }
            header(s, ++r, st, "Sản phẩm", "Số đánh giá", "Điểm TB", "Đánh giá tiêu cực (1-2 sao)", "Chưa phản hồi");
            for (Map<String, Object> pr : (List<Map<String, Object>>) reviews.get("products")) {
                Row x = s.createRow(++r);
                cell(x, 0, (String) pr.get("name"), null);
                num(x, 1, pr.get("count"), null);
                num(x, 2, pr.get("average"), null);
                num(x, 3, pr.get("negative"), null);
                num(x, 4, pr.get("unreplied"), null);
            }
            widths(s, 40, 13, 10, 24, 14);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException("Không xuất được báo cáo", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ==================== helpers ====================

    static final class Styles {
        final CellStyle header, bold, title, money, percent;

        Styles(Workbook wb) {
            Font white = wb.createFont();
            white.setBold(true);
            white.setColor(IndexedColors.WHITE.getIndex());
            header = wb.createCellStyle();
            header.setFont(white);
            header.setFillForegroundColor(IndexedColors.CORAL.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setWrapText(true);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            Font b = wb.createFont();
            b.setBold(true);
            bold = wb.createCellStyle();
            bold.setFont(b);
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            title = wb.createCellStyle();
            title.setFont(big);
            DataFormat fmt = wb.createDataFormat();
            money = wb.createCellStyle();
            money.setDataFormat(fmt.getFormat("#,##0"));
            percent = wb.createCellStyle();
            percent.setDataFormat(fmt.getFormat("+0.0%;-0.0%;0.0%"));
        }
    }

    static void header(Sheet s, int row, Styles st, String... names) {
        Row h = s.createRow(row);
        h.setHeightInPoints(30);
        for (int i = 0; i < names.length; i++) cell(h, i, names[i], st.header);
    }

    static void widths(Sheet s, int... chars) {
        for (int i = 0; i < chars.length; i++) s.setColumnWidth(i, chars[i] * 256);
    }

    static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        if (style != null) c.setCellStyle(style);
    }

    static void num(Row row, int col, Object value, CellStyle style) {
        if (value == null) return;
        Cell c = row.createCell(col);
        c.setCellValue(number(value));
        if (style != null) c.setCellStyle(style);
    }

    private static double number(Object o) {
        if (o instanceof BigDecimal b) return b.doubleValue();
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
