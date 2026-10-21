package com.marketplace.backend.service;

import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.service.SellerAnalyticsService.Period;
import com.marketplace.backend.service.SellerReportExcelService.Styles;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static com.marketplace.backend.service.SellerReportExcelService.*;

/** The platform report as an .xlsx: overview, period by period, top shops, top products, categories, growth. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlatformReportExcelService {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PlatformAnalyticsService analytics;
    private final AdminAuditService auditService;

    @SuppressWarnings("unchecked")
    @Transactional // writes the audit entry
    public byte[] export(LocalDate from, LocalDate to, String granularity) {
        Period p = analytics.period(from, to, granularity);
        Map<String, Object> overview = analytics.overview(p);
        Map<String, Object> top = analytics.top(p, 50, "revenue");
        Map<String, Object> growth = analytics.growth(p);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);

            // ---- overview ----
            Sheet s = wb.createSheet("Tổng quan");
            cell(s.createRow(0), 0, "Báo cáo kinh doanh toàn sàn Shoppi", st.title);
            cell(s.createRow(1), 0, "Kỳ báo cáo: " + p.from().format(D) + " - " + p.to().format(D) + "   (so với "
                    + p.previous().from().format(D) + " - " + p.previous().to().format(D) + ")", null);
            cell(s.createRow(2), 0, "Xuất lúc " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")), null);
            header(s, 4, st, "Chỉ số", "Kỳ này", "Kỳ trước", "Thay đổi");
            Map<String, Object> cur = (Map<String, Object>) overview.get("summary"), prev = (Map<String, Object>) overview.get("previous");
            Object[][] metrics = {
                    {"Giá trị giao dịch (GMV)", "gmv"}, {"Số đơn hàng", "orders"}, {"Giá trị đơn trung bình", "avgOrderValue"},
                    {"Người mua đặt hàng", "buyers"}, {"Người mua lần đầu", "firstTimeBuyers"}, {"Shop có đơn", "shopsSelling"},
                    {"Người dùng mới", "newUsers"}, {"Shop mới", "newShops"}, {"Phí sàn (ước tính)", "commission"},
                    {"Chi phí Shoppi Voucher", "platformVoucherCost"}, {"Tỷ lệ hủy (%)", "cancelRate"}};
            int r = 5;
            for (Object[] m : metrics) {
                Row row = s.createRow(r++);
                cell(row, 0, (String) m[0], st.bold);
                num(row, 1, cur.get(m[1]), st.money);
                num(row, 2, prev.get(m[1]), st.money);
                Double g = PlatformAnalyticsService.growth(dec(cur.get(m[1])), dec(prev.get(m[1])));
                if (g != null) num(row, 3, g / 100, st.percent);
            }
            Map<String, Object> totals = (Map<String, Object>) overview.get("totals");
            Row tr = s.createRow(r + 1);
            cell(tr, 0, "Tổng người dùng / shop đến cuối kỳ", st.bold);
            num(tr, 1, totals.get("users"), st.money);
            num(tr, 2, totals.get("shops"), st.money);
            widths(s, 38, 18, 18, 12);

            // ---- by period ----
            s = wb.createSheet("Theo thời gian");
            header(s, 0, st, "Kỳ", "GMV", "Đơn hàng", "Người mua", "Người dùng mới", "Shop mới");
            r = 1;
            for (Map<String, Object> x : (List<Map<String, Object>>) overview.get("series")) {
                Row row = s.createRow(r++);
                cell(row, 0, (String) x.get("label"), null);
                num(row, 1, x.get("gmv"), st.money);
                num(row, 2, x.get("orders"), st.money);
                num(row, 3, x.get("buyers"), st.money);
                num(row, 4, x.get("newUsers"), st.money);
                num(row, 5, x.get("newShops"), st.money);
            }
            widths(s, 16, 16, 12, 12, 16, 10);

            // ---- top shops ----
            s = wb.createSheet("Top shop");
            header(s, 0, st, "#", "Shop", "GMV", "Tỷ trọng", "Đơn hàng", "Sản phẩm bán", "Người mua", "Giá trị đơn TB", "Tăng trưởng", "Đánh giá");
            r = 1;
            for (Map<String, Object> x : (List<Map<String, Object>>) top.get("shops")) {
                Row row = s.createRow(r++);
                num(row, 0, x.get("rank"), null);
                cell(row, 1, (String) x.get("name"), null);
                num(row, 2, x.get("gmv"), st.money);
                num(row, 3, ((Number) x.get("share")).doubleValue() / 100, st.percent);
                num(row, 4, x.get("orders"), st.money);
                num(row, 5, x.get("units"), st.money);
                num(row, 6, x.get("buyers"), st.money);
                num(row, 7, x.get("avgOrderValue"), st.money);
                if (x.get("growth") != null) num(row, 8, ((Number) x.get("growth")).doubleValue() / 100, st.percent);
                if (x.get("rating") != null) num(row, 9, x.get("rating"), null);
            }
            widths(s, 5, 30, 16, 10, 10, 12, 10, 14, 12, 9);

            // ---- top products ----
            s = wb.createSheet("Top sản phẩm");
            header(s, 0, st, "#", "Sản phẩm", "Shop", "Ngành hàng", "Đã bán", "Doanh thu", "Số đơn");
            r = 1;
            for (Map<String, Object> x : (List<Map<String, Object>>) top.get("products")) {
                Row row = s.createRow(r++);
                num(row, 0, x.get("rank"), null);
                cell(row, 1, (String) x.get("name"), null);
                cell(row, 2, (String) x.get("shopName"), null);
                cell(row, 3, (String) x.get("category"), null);
                num(row, 4, x.get("units"), st.money);
                num(row, 5, x.get("revenue"), st.money);
                num(row, 6, x.get("orders"), st.money);
            }
            widths(s, 5, 40, 24, 26, 10, 16, 10);

            // ---- categories ----
            s = wb.createSheet("Ngành hàng");
            header(s, 0, st, "Ngành hàng", "Doanh thu", "Tỷ trọng", "Đã bán");
            r = 1;
            for (Map<String, Object> x : (List<Map<String, Object>>) top.get("categories")) {
                Row row = s.createRow(r++);
                cell(row, 0, (String) x.get("name"), null);
                num(row, 1, x.get("revenue"), st.money);
                num(row, 2, ((Number) x.get("share")).doubleValue() / 100, st.percent);
                num(row, 3, x.get("units"), st.money);
            }
            widths(s, 28, 16, 10, 10);

            // ---- growth ----
            s = wb.createSheet("Tăng trưởng");
            header(s, 0, st, "Kỳ", "GMV", "+/- GMV", "Đơn hàng", "+/- Đơn", "Người mua", "Mua lần đầu", "Mua lại",
                    "Người dùng mới", "Shop mới", "Tổng người dùng", "Tổng shop");
            r = 1;
            for (Map<String, Object> x : (List<Map<String, Object>>) growth.get("rows")) {
                Row row = s.createRow(r++);
                cell(row, 0, (String) x.get("label"), null);
                num(row, 1, x.get("gmv"), st.money);
                if (x.get("gmvGrowth") != null) num(row, 2, ((Number) x.get("gmvGrowth")).doubleValue() / 100, st.percent);
                num(row, 3, x.get("orders"), st.money);
                if (x.get("ordersGrowth") != null) num(row, 4, ((Number) x.get("ordersGrowth")).doubleValue() / 100, st.percent);
                num(row, 5, x.get("activeBuyers"), st.money);
                num(row, 6, x.get("newBuyers"), st.money);
                num(row, 7, x.get("returningBuyers"), st.money);
                num(row, 8, x.get("newUsers"), st.money);
                num(row, 9, x.get("newShops"), st.money);
                num(row, 10, x.get("totalUsers"), st.money);
                num(row, 11, x.get("totalShops"), st.money);
            }
            widths(s, 16, 16, 10, 10, 10, 11, 12, 10, 14, 10, 15, 10);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            auditService.log("EXPORT_REPORT", "REPORT", null, "Báo cáo toàn sàn", p.from().format(D) + " - " + p.to().format(D));
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException("Không tạo được file báo cáo", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static java.math.BigDecimal dec(Object o) {
        return o == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(o.toString());
    }
}
