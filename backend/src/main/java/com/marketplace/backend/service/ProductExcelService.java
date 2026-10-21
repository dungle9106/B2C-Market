package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.ProductRequest;
import com.marketplace.backend.entity.Category;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.ProductVariant;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.entity.ShopCategory;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.CategoryRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ShopCategoryRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.util.TextNormalizer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

/**
 * "Dang san pham hang loat": an .xlsx template the seller fills in, and the import of it.
 *
 * One row = one product, or one variant of a product: rows sharing the same
 * "Ma SP" are the variants of one product (Shopee's mass-upload layout). Product
 * columns are read from the first row of the group. Every product then goes
 * through the same rules as the product form (ProductService.importDrafts), and
 * nothing is created unless the whole file is valid.
 */
@Service
@RequiredArgsConstructor
public class ProductExcelService {

    public static final int MAX_ROWS = 1000;
    public static final int MAX_PRODUCTS = 200;
    private static final String SHEET = "SanPham";

    // column order of the template; the import reads columns by these positions
    private static final String[] HEADERS = {
            "Mã SP (*)", "Tên sản phẩm (*)", "Ngành hàng", "Danh mục shop", "Mô tả", "Giá bán (*)", "Giá gốc",
            "Kho", "Ảnh (URL)", "Đổi trả (ngày)", "Freeship", "Nhóm phân loại 1", "Phân loại 1",
            "Nhóm phân loại 2", "Phân loại 2", "Giá phân loại", "Kho phân loại", "SKU phân loại"
    };
    private static final int SKU = 0, NAME = 1, CATEGORY = 2, SHOP_CATS = 3, DESC = 4, PRICE = 5, ORIGINAL = 6,
            STOCK = 7, IMAGES = 8, RETURN_DAYS = 9, FREESHIP = 10, TIER1 = 11, OPT1 = 12, TIER2 = 13, OPT2 = 14,
            V_PRICE = 15, V_STOCK = 16, V_SKU = 17;

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ShopCategoryRepository shopCategoryRepository;
    private final ShopRepository shopRepository;
    private final Validator validator;

    // ==================== template ====================

    public byte[] template() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            Sheet data = wb.createSheet(SHEET);
            header(data, st);
            // Freeship: a Có / Không drop-down
            DataValidationHelper dv = data.getDataValidationHelper();
            DataValidation freeship = dv.createValidation(dv.createExplicitListConstraint(new String[]{"Có", "Không"}),
                    new CellRangeAddressList(1, MAX_ROWS, FREESHIP, FREESHIP));
            freeship.setShowErrorBox(true);
            data.addValidationData(freeship);

            Sheet guide = wb.createSheet("Hướng dẫn");
            String[][] lines = {
                    {"HƯỚNG DẪN ĐĂNG SẢN PHẨM HÀNG LOẠT", ""},
                    {"", ""},
                    {"Mã SP (*)", "Mã riêng của bạn (chữ, số, . _ -), không trùng sản phẩm đã có. Các dòng cùng Mã SP là các phân loại của một sản phẩm."},
                    {"Tên sản phẩm (*)", "Tối đa 120 ký tự. Với sản phẩm có phân loại chỉ cần điền ở dòng đầu tiên."},
                    {"Ngành hàng", "Tên hoặc ID ngành hàng của Shoppi (xem sheet \"Ngành hàng\")."},
                    {"Danh mục shop", "Tên danh mục bạn đã tạo trong Kênh Người Bán, nhiều danh mục cách nhau bởi dấu ;"},
                    {"Giá bán (*)", "Đơn vị ₫, chỉ nhập số (VD 150000)."},
                    {"Giá gốc", "Giá gạch ngang, phải lớn hơn giá bán."},
                    {"Kho", "Số lượng tồn kho khi sản phẩm KHÔNG có phân loại."},
                    {"Ảnh (URL)", "Link ảnh http(s), nhiều ảnh cách nhau bởi dấu ; (ảnh đầu là ảnh bìa, tối đa 9). Có thể thêm ảnh sau trong Kênh Người Bán."},
                    {"Đổi trả (ngày)", "0, 3, 7, 15 hoặc 30. Bỏ trống = theo chính sách shop."},
                    {"Freeship", "Có / Không."},
                    {"Nhóm phân loại 1, 2", "Tên nhóm, VD \"Màu sắc\", \"Kích cỡ\" - điền ở dòng đầu của sản phẩm."},
                    {"Phân loại 1, 2", "Giá trị của nhóm trên từng dòng, VD \"Đỏ\" và \"M\"."},
                    {"Giá phân loại", "Giá bán của phân loại, không thấp hơn Giá bán. Bỏ trống = bằng Giá bán."},
                    {"Kho phân loại", "Tồn kho của từng phân loại (bắt buộc với sản phẩm có phân loại)."},
                    {"", ""},
                    {"Lưu ý", "Tối đa " + MAX_PRODUCTS + " sản phẩm / " + MAX_ROWS + " dòng mỗi lần. File chỉ được đăng khi tất cả các dòng hợp lệ - hệ thống sẽ báo lỗi theo số dòng để bạn sửa."},
            };
            for (int i = 0; i < lines.length; i++) {
                Row r = guide.createRow(i);
                r.createCell(0).setCellValue(lines[i][0]);
                r.createCell(1).setCellValue(lines[i][1]);
                r.getCell(0).setCellStyle(i == 0 ? st.title : st.bold);
            }
            guide.setColumnWidth(0, 24 * 256);
            guide.setColumnWidth(1, 120 * 256);

            Sheet example = wb.createSheet("Ví dụ");
            header(example, st);
            Object[][] rows = {
                    {"AOTHUN01", "Áo thun nam cotton basic", "Thời trang", "", "Áo thun 100% cotton", 150000, 199000, null,
                            "https://example.com/ao1.jpg;https://example.com/ao2.jpg", 7, "Có", "Màu sắc", "Đen", "Kích cỡ", "M", null, 20, "AOTHUN01-DEN-M"},
                    {"AOTHUN01", null, null, null, null, null, null, null, null, null, null, null, "Đen", null, "L", 155000, 15, "AOTHUN01-DEN-L"},
                    {"AOTHUN01", null, null, null, null, null, null, null, null, null, null, null, "Trắng", null, "M", null, 10, "AOTHUN01-TRANG-M"},
                    {"BINHNUOC01", "Bình giữ nhiệt 500ml", "Đồ gia dụng", "", "Giữ nóng 12 giờ", 89000, null, 50,
                            "", null, "Không", null, null, null, null, null, null, null},
            };
            for (int i = 0; i < rows.length; i++) {
                Row r = example.createRow(i + 1);
                for (int c = 0; c < rows[i].length; c++) {
                    Object v = rows[i][c];
                    if (v instanceof Number n) r.createCell(c).setCellValue(n.doubleValue());
                    else if (v != null) r.createCell(c).setCellValue(v.toString());
                }
            }

            Sheet cats = wb.createSheet("Ngành hàng");
            Row ch = cats.createRow(0);
            ch.createCell(0).setCellValue("ID");
            ch.createCell(1).setCellValue("Ngành hàng");
            ch.getCell(0).setCellStyle(st.header);
            ch.getCell(1).setCellStyle(st.header);
            int i = 1;
            for (Category c : categoryRepository.findAll(Sort.by("id"))) {
                Row r = cats.createRow(i++);
                r.createCell(0).setCellValue(c.getId());
                r.createCell(1).setCellValue(c.getName());
            }
            cats.setColumnWidth(1, 36 * 256);

            wb.setActiveSheet(0);
            return bytes(wb);
        } catch (IOException e) {
            throw new ApiException("Không tạo được file mẫu", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ==================== import ====================

    public Map<String, Object> importFile(MultipartFile file, boolean dryRun) {
        if (file == null || file.isEmpty()) throw bad("Vui lòng chọn file Excel (.xlsx)");
        String fname = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!fname.endsWith(".xlsx")) throw bad("Chỉ hỗ trợ file .xlsx (Excel 2007 trở lên)");
        if (file.getSize() > 5 * 1024 * 1024) throw bad("File tối đa 5MB");

        Shop shop = shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> bad("Bạn chưa có shop"));
        Map<String, Long> categories = categoryLookup();
        Map<String, Long> shopCats = new HashMap<>();
        for (ShopCategory c : shopCategoryRepository.findByShopIdOrderByPositionAscIdAsc(shop.getId())) {
            shopCats.put(TextNormalizer.normalize(c.getName()), c.getId());
        }
        Set<String> existingSkus = new HashSet<>(productRepository.skusOfShop(shop.getId()));

        List<Map<String, Object>> errors = new ArrayList<>();
        List<ProductService.ImportDraft> drafts = new ArrayList<>();
        int rowCount = 0;

        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheet(SHEET) != null ? wb.getSheet(SHEET) : wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(new Locale("vi", "VN"));

            // group rows by "Ma SP", keeping file order
            LinkedHashMap<String, List<Row>> groups = new LinkedHashMap<>();
            Map<String, String> skuSpelling = new HashMap<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, fmt)) continue;
                if (++rowCount > MAX_ROWS) throw bad("File có quá " + MAX_ROWS + " dòng, vui lòng chia nhỏ");
                String sku = text(row, SKU, fmt);
                if (sku == null) {
                    errors.add(err(r + 1, "Thiếu Mã SP"));
                    continue;
                }
                String key = sku.toLowerCase(Locale.ROOT);
                skuSpelling.putIfAbsent(key, sku);
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
            if (groups.size() > MAX_PRODUCTS) throw bad("Mỗi lần chỉ đăng tối đa " + MAX_PRODUCTS + " sản phẩm");
            if (groups.isEmpty() && errors.isEmpty()) throw bad("File không có dòng sản phẩm nào (nhập từ dòng 2 của sheet \"" + SHEET + "\")");

            for (var g : groups.entrySet()) {
                List<Row> rows = g.getValue();
                int firstRow = rows.get(0).getRowNum() + 1;
                if (existingSkus.contains(g.getKey())) {
                    errors.add(err(firstRow, "Mã SP \"" + skuSpelling.get(g.getKey()) + "\" đã tồn tại trong shop"));
                    continue;
                }
                List<String> rowErrors = new ArrayList<>();
                ProductRequest req = toRequest(skuSpelling.get(g.getKey()), rows, fmt, categories, shopCats, rowErrors);
                Set<ConstraintViolation<ProductRequest>> violations = validator.validate(req);
                violations.forEach(v -> rowErrors.add(v.getMessage()));
                rowErrors.stream().distinct().forEach(m -> errors.add(err(firstRow, m)));
                // with the required fields present, the form rules can still run and report more in the same pass
                if (violations.isEmpty()) drafts.add(new ProductService.ImportDraft(firstRow, req, !rowErrors.isEmpty()));
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw bad("Không đọc được file Excel. Hãy dùng file mẫu của Shoppi và lưu dạng .xlsx");
        }

        Map<String, Object> result = productService.importDrafts(drafts, errors, dryRun);
        result.put("rows", rowCount);
        return result;
    }

    /** Builds the form request for one product from its group of rows; problems go to errors. */
    private ProductRequest toRequest(String sku, List<Row> rows, DataFormatter fmt, Map<String, Long> categories,
                                     Map<String, Long> shopCats, List<String> errors) {
        Row first = rows.get(0);
        ProductRequest req = new ProductRequest();
        req.setSku(sku);
        req.setName(orEmpty(text(first, NAME, fmt)));
        req.setDescription(text(first, DESC, fmt));
        req.setPrice(money(first, PRICE, fmt, "Giá bán", errors));
        req.setOriginalPrice(money(first, ORIGINAL, fmt, "Giá gốc", errors));

        String cat = text(first, CATEGORY, fmt);
        if (cat != null) {
            Long id = categories.get(cat.matches("\\d+") ? "#" + cat : TextNormalizer.normalize(cat));
            if (id == null) errors.add("Không tìm thấy ngành hàng \"" + cat + "\"");
            req.setCategoryId(id);
        }
        String sc = text(first, SHOP_CATS, fmt);
        if (sc != null) {
            for (String name : sc.split(";")) {
                if (name.isBlank()) continue;
                Long id = shopCats.get(TextNormalizer.normalize(name));
                if (id == null) errors.add("Không có danh mục shop \"" + name.trim() + "\" (hãy tạo trước trong Danh mục của Shop)");
                else req.getShopCategoryIds().add(id);
            }
        }
        String images = text(first, IMAGES, fmt);
        if (images != null) {
            for (String u : images.split("[;\\n]")) if (!u.isBlank()) req.getImageUrls().add(u.trim());
        }
        Integer days = integer(first, RETURN_DAYS, fmt, "Đổi trả", errors);
        if (days != null && !Set.of(0, 3, 7, 15, 30).contains(days)) errors.add("Đổi trả chỉ nhận 0, 3, 7, 15 hoặc 30 ngày");
        req.setReturnDays(days);
        String fs = text(first, FREESHIP, fmt);
        req.setFreeShipping(fs != null && Set.of("co", "x", "yes", "true", "1").contains(TextNormalizer.normalize(fs)));

        // variants: several rows, or a variant value on the single row
        boolean hasVariants = rows.size() > 1 || text(first, OPT1, fmt) != null;
        if (!hasVariants) {
            req.setStock(Optional.ofNullable(integer(first, STOCK, fmt, "Kho", errors)).orElse(0));
            return req;
        }
        String tier1 = text(first, TIER1, fmt);
        String tier2 = text(first, TIER2, fmt);
        req.setTier1Name(tier1 != null ? tier1 : "Phân loại");
        req.setTier2Name(tier2);
        for (Row r : rows) {
            String where = rows.size() > 1 ? " (dòng " + (r.getRowNum() + 1) + ")" : "";
            ProductRequest.VariantRequest v = new ProductRequest.VariantRequest();
            v.setOption1(text(r, OPT1, fmt));
            v.setOption2(text(r, OPT2, fmt));
            v.setSku(text(r, V_SKU, fmt));
            if (v.getOption1() == null) errors.add("Thiếu Phân loại 1" + where);
            if (tier2 != null && v.getOption2() == null) errors.add("Thiếu Phân loại 2" + where);
            Integer stock = integer(r, V_STOCK, fmt, "Kho phân loại", errors);
            if (stock == null) errors.add("Thiếu Kho phân loại" + where);
            v.setStock(stock);
            BigDecimal vp = money(r, V_PRICE, fmt, "Giá phân loại", errors);
            if (vp != null && req.getPrice() != null) {
                if (vp.compareTo(req.getPrice()) < 0) errors.add("Giá phân loại không được thấp hơn giá bán" + where);
                else v.setExtraPrice(vp.subtract(req.getPrice()));
            }
            req.getVariants().add(v);
        }
        return req;
    }

    // ==================== export ====================

    @Transactional(readOnly = true)
    public byte[] export() {
        Shop shop = shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> bad("Bạn chưa có shop"));
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            Sheet sheet = wb.createSheet(SHEET);
            header(sheet, st);
            Row h = sheet.getRow(0);
            String[] extra = {"Trạng thái", "Đã bán", "ID"};
            for (int i = 0; i < extra.length; i++) {
                Cell c = h.createCell(HEADERS.length + i);
                c.setCellValue(extra[i]);
                c.setCellStyle(st.header);
            }
            int r = 1;
            for (Product p : productRepository.findByShopIdOrderByCreatedAtDesc(shop.getId())) {
                List<ProductVariant> variants = p.getVariants().isEmpty() ? Collections.singletonList(null) : p.getVariants();
                boolean first = true;
                for (ProductVariant v : variants) {
                    Row row = sheet.createRow(r++);
                    set(row, SKU, p.getSku() != null ? p.getSku() : "SP" + p.getId());
                    if (first) {
                        set(row, NAME, p.getName());
                        set(row, CATEGORY, p.getCategory() != null ? p.getCategory().getName() : null);
                        set(row, SHOP_CATS, String.join(";", p.getShopCategories().stream().map(ShopCategory::getName).sorted().toList()));
                        set(row, DESC, p.getDescription());
                        row.createCell(PRICE).setCellValue(p.getPrice().doubleValue());
                        if (p.getOriginalPrice() != null) row.createCell(ORIGINAL).setCellValue(p.getOriginalPrice().doubleValue());
                        if (v == null) row.createCell(STOCK).setCellValue(p.getStock());
                        set(row, IMAGES, String.join(";", p.getImageUrls()));
                        row.createCell(RETURN_DAYS).setCellValue(p.getReturnDays());
                        set(row, FREESHIP, p.isFreeShipping() ? "Có" : "Không");
                        set(row, TIER1, p.getTier1Name());
                        set(row, TIER2, p.getTier2Name());
                        set(row, HEADERS.length, p.isActive() ? (p.getStock() > 0 ? "Đang bán" : "Hết hàng") : "Đã ẩn");
                        row.createCell(HEADERS.length + 1).setCellValue(p.getSoldCount());
                    }
                    row.createCell(HEADERS.length + 2).setCellValue(p.getId());
                    if (v != null) {
                        // older variants have only a name: export it as the single option
                        set(row, OPT1, v.getOption1() != null ? v.getOption1() : v.getName());
                        set(row, OPT2, v.getOption2());
                        row.createCell(V_PRICE).setCellValue(p.getPrice().add(v.getExtraPrice() != null ? v.getExtraPrice() : BigDecimal.ZERO).doubleValue());
                        row.createCell(V_STOCK).setCellValue(v.getStock());
                        set(row, V_SKU, v.getSku());
                    }
                    first = false;
                }
            }
            return bytes(wb);
        } catch (IOException e) {
            throw new ApiException("Không xuất được file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ==================== helpers ====================

    private static final class Styles {
        final CellStyle header, bold, title;

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
        }
    }

    private static void header(Sheet sheet, Styles st) {
        Row h = sheet.createRow(0);
        h.setHeightInPoints(32);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(HEADERS[i]);
            c.setCellStyle(st.header);
            sheet.setColumnWidth(i, (i == NAME || i == DESC || i == IMAGES ? 32 : 16) * 256);
        }
        sheet.createFreezePane(0, 1);
    }

    // lookup by "#<id>" or by accent-free name
    private Map<String, Long> categoryLookup() {
        Map<String, Long> m = new HashMap<>();
        for (Category c : categoryRepository.findAll()) {
            m.put("#" + c.getId(), c.getId());
            m.putIfAbsent(TextNormalizer.normalize(c.getName()), c.getId());
        }
        return m;
    }

    private static String text(Row row, int col, DataFormatter fmt) {
        Cell c = row.getCell(col);
        if (c == null) return null;
        String s = fmt.formatCellValue(c).trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean isBlankRow(Row row, DataFormatter fmt) {
        for (int c = 0; c < HEADERS.length; c++) if (text(row, c, fmt) != null) return false;
        return true;
    }

    /** Whole VND: a numeric cell, or text like "150000", "150.000", "150,000 ₫". */
    private static BigDecimal money(Row row, int col, DataFormatter fmt, String label, List<String> errors) {
        Cell c = row.getCell(col);
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) {
            BigDecimal v = BigDecimal.valueOf(c.getNumericCellValue());
            return v.stripTrailingZeros().scale() > 0 ? invalid(label, errors) : v.setScale(0);
        }
        String s = text(row, col, fmt);
        if (s == null) return null;
        String digits = s.replaceAll("[\\s.,₫đvndVND]", "");
        if (!digits.matches("\\d{1,12}")) return invalid(label, errors);
        return new BigDecimal(digits);
    }

    private static Integer integer(Row row, int col, DataFormatter fmt, String label, List<String> errors) {
        BigDecimal v = money(row, col, fmt, label, errors);
        if (v == null) return null;
        if (v.compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
            errors.add(label + " quá lớn");
            return null;
        }
        return v.intValue();
    }

    private static BigDecimal invalid(String label, List<String> errors) {
        errors.add(label + " không hợp lệ (chỉ nhập số nguyên)");
        return null;
    }

    private static void set(Row row, int col, String value) {
        if (value != null && !value.isEmpty()) row.createCell(col).setCellValue(value);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Map<String, Object> err(int row, String message) {
        return Map.of("row", row, "message", message);
    }

    private static byte[] bytes(Workbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
