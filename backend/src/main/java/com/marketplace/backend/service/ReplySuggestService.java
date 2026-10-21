package com.marketplace.backend.service;

import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.ProductVariant;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.entity.Voucher;
import com.marketplace.backend.entity.VoucherType;
import com.marketplace.backend.repository.VoucherRepository;
import com.marketplace.backend.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

// "Goi y tra loi" for the seller (product question or a buyer's chat message).
// The draft is built only from the shop's real data (stock, variants, price, vouchers, policies):
// with an LLM configured it is phrased by the model, otherwise assembled from rules.
// The seller always reviews it before sending.
@Service
@RequiredArgsConstructor
public class ReplySuggestService {

    private final AIService aiService;
    private final VoucherRepository voucherRepository;

    /** { answer, source: "ai" | "rule" } */
    public Map<String, String> suggest(String question, Product product, Shop shop, String customerName) {
        List<String> facts = facts(product, shop);
        if (aiService.isConfigured()) {
            String system = "Bạn là nhân viên chăm sóc khách hàng của shop \"" + shop.getName() + "\" trên sàn thương mại điện tử. "
                    + "Trả lời câu hỏi của khách bằng tiếng Việt, lịch sự, ngắn gọn (tối đa 60 từ), xưng \"shop\" và gọi khách là \"bạn\". "
                    + "Chỉ dùng các thông tin được cung cấp, không bịa thêm. Nếu thiếu thông tin, mời khách nhắn tin để shop kiểm tra. "
                    + "Chỉ trả về câu trả lời.";
            String user = "Thông tin:\n- " + String.join("\n- ", facts) + "\n\nCâu hỏi của khách: " + question;
            String answer = aiService.complete(system, user);
            // the provider answers with an apology text when the call fails: fall back to the rules then
            if (answer != null && !answer.isBlank() && !answer.startsWith("[AI") && !answer.startsWith("Xin lỗi, trợ lý AI")) {
                return Map.of("answer", answer.trim(), "source", "ai");
            }
        }
        return Map.of("answer", rules(question, product, shop, customerName), "source", "rule");
    }

    // ==================== rule-based draft ====================

    String rules(String question, Product product, Shop shop, String customerName) {
        // accent-free words separated by single spaces, so keys match whole words ("khong" must not match "hong")
        String q = " " + TextNormalizer.normalize(question == null ? "" : question).replaceAll("[^a-z0-9]+", " ").trim() + " ";
        List<String> parts = new ArrayList<>();
        String name = product != null ? product.getName() : "sản phẩm";

        if (has(q, "con hang", "het hang", "con khong", "con ko", "con k", "con size", "con mau", "co san", "con nhieu")) {
            if (product == null) parts.add("Bạn gửi giúp shop sản phẩm bạn quan tâm để shop kiểm tra tồn kho nhé");
            else if (product.getStock() == null || product.getStock() <= 0) parts.add(name + " hiện đang tạm hết hàng, shop sẽ sớm nhập thêm ạ");
            else {
                String inStock = variantsInStock(product);
                parts.add(name + " hiện vẫn còn hàng" + (inStock.isEmpty() ? " (" + product.getStock() + " sản phẩm)" : ", các phân loại còn: " + inStock));
            }
        }
        if (has(q, "size", "kich thuoc", "kich co", "mac vua", "can nang", "chieu cao", "form", "mau nao", "mau gi", "co mau", "phan loai")) {
            String options = product == null ? "" : product.getVariants().stream().map(ProductVariant::getName).distinct()
                    .collect(Collectors.joining(", "));
            parts.add((options.isEmpty() ? "Bạn" : "Sản phẩm có các phân loại: " + options + ". Bạn")
                    + " cho shop xin chiều cao, cân nặng hoặc nhu cầu sử dụng để shop tư vấn chính xác nhé");
        }
        if (has(q, "gia", "giam", "sale", "voucher", "ma giam", "khuyen mai", "mua nhieu", "re hon", "bot", "bao nhieu tien")) {
            if (product != null) parts.add("Giá hiện tại của " + name + " là " + vnd(product.getEffectivePrice()));
            String vouchers = vouchers(shop);
            parts.add(vouchers.isEmpty() ? "Bạn theo dõi shop để nhận mã giảm giá trong các chương trình sắp tới nhé"
                    : "Shop đang có mã giảm giá " + vouchers + ", bạn lưu mã và áp dụng khi thanh toán nhé");
        }
        if (has(q, "giao", "ship", "van chuyen", "bao lau", "may ngay", "nhan hang", "freeship", "phi van chuyen", "phi ship")) {
            String s = "Shop chuẩn bị hàng trong " + (shop.getHandlingDays() == null ? 1 : shop.getHandlingDays()) + " ngày"
                    + (shop.getProvince() != null ? " và gửi từ " + shop.getProvince() : "")
                    + ", thời gian giao tùy khu vực của bạn (hiển thị ở bước thanh toán)";
            if (product != null && product.isFreeShipping()) s += ". Sản phẩm được miễn phí vận chuyển";
            else if (shop.getFreeShipMinOrder() != null) s += ". Đơn từ " + vnd(shop.getFreeShipMinOrder()) + " được miễn phí vận chuyển";
            parts.add(s);
        }
        if (has(q, "doi tra", "doi hang", "doi size", "doi mau", "duoc doi", "tra hang", "hoan tien", "bao hanh", "bi loi", "hang loi",
                "bi hong", "hu hong", "bi rach")) {
            int days = product != null && product.getReturnDays() != null ? product.getReturnDays()
                    : shop.getDefaultReturnDays() == null ? 7 : shop.getDefaultReturnDays();
            parts.add(days > 0 ? "Sản phẩm được hỗ trợ đổi trả trong " + days + " ngày kể từ khi nhận hàng"
                    + (shop.getReturnConditions() != null ? " (" + shop.getReturnConditions().trim() + ")" : "")
                    : "Sản phẩm này không áp dụng đổi trả, bạn kiểm tra kỹ thông tin trước khi đặt giúp shop nhé");
        }
        if (has(q, "chat lieu", "chat vai", "loai vai", "vai gi", "chat luong", "lam bang", "xuat xu", "hang that", "chinh hang")) {
            String desc = product == null ? null : product.getDescription();
            parts.add(desc == null || desc.isBlank() ? "Thông tin chi tiết shop ghi trong phần mô tả sản phẩm"
                    : "Theo mô tả sản phẩm: \"" + firstSentence(desc) + "\"");
        }
        String greeting = "Dạ " + (customerName == null || customerName.isBlank() ? "bạn" : customerName.trim()) + " ơi, ";
        if (parts.isEmpty()) {
            return greeting + "cảm ơn bạn đã quan tâm " + name + ". Shop đã nhận được câu hỏi và sẽ kiểm tra giúp bạn ngay, "
                    + "bạn cần hỗ trợ thêm cứ nhắn tin cho shop nhé!";
        }
        String body = String.join(". ", parts);
        // "Dạ bạn ơi, giá hiện tại..." but keep a product name as written
        if (!body.startsWith(name)) body = lowerFirst(body);
        return greeting + body + ". Bạn cần hỗ trợ thêm cứ nhắn tin cho shop nhé!";
    }

    // facts given to the LLM: the same data the rules use
    private List<String> facts(Product p, Shop shop) {
        List<String> f = new ArrayList<>();
        if (p != null) {
            f.add("Sản phẩm: " + p.getName() + ", giá " + vnd(p.getEffectivePrice()) + ", tồn kho " + p.getStock());
            String inStock = variantsInStock(p);
            if (!inStock.isEmpty()) f.add("Phân loại còn hàng: " + inStock);
            if (p.getDescription() != null) f.add("Mô tả: " + cut(p.getDescription(), 600));
            f.add("Đổi trả: " + (p.getReturnDays() == null || p.getReturnDays() == 0 ? "không áp dụng" : p.getReturnDays() + " ngày"));
            if (p.isFreeShipping()) f.add("Miễn phí vận chuyển");
        }
        f.add("Shop chuẩn bị hàng trong " + shop.getHandlingDays() + " ngày, gửi từ " + shop.getProvince());
        if (shop.getReturnConditions() != null) f.add("Điều kiện đổi trả: " + shop.getReturnConditions());
        String v = vouchers(shop);
        if (!v.isEmpty()) f.add("Mã giảm giá đang chạy: " + v);
        return f;
    }

    private String vouchers(Shop shop) {
        List<Voucher> list = voucherRepository.runningPublicOfShop(shop.getId(), LocalDateTime.now());
        return list.stream().limit(2).map(v -> v.getCode() + " (" + voucherText(v) + ")").collect(Collectors.joining(", "));
    }

    private static String voucherText(Voucher v) {
        String value = v.getType() == VoucherType.PERCENT ? "giảm " + v.getDiscountValue().stripTrailingZeros().toPlainString() + "%"
                : v.getType() == VoucherType.SHIPPING ? "giảm " + vnd(v.getDiscountValue()) + " phí vận chuyển"
                : "giảm " + vnd(v.getDiscountValue());
        return v.getMinOrderAmount() != null && v.getMinOrderAmount().signum() > 0
                ? value + " cho đơn từ " + vnd(v.getMinOrderAmount()) : value;
    }

    private static String variantsInStock(Product p) {
        return p.getVariants().stream().filter(v -> v.getStock() != null && v.getStock() > 0)
                .map(ProductVariant::getName).collect(Collectors.joining(", "));
    }

    private static boolean has(String words, String... keys) {
        for (String k : keys) if (words.contains(" " + k + " ")) return true;
        return false;
    }

    private static String firstSentence(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        int dot = t.indexOf(". ");
        return cut(dot > 20 ? t.substring(0, dot) : t, 160);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3).trim() + "...";
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    static String vnd(BigDecimal v) {
        return "₫" + NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(v == null ? 0 : v.setScale(0, java.math.RoundingMode.HALF_UP));
    }
}
