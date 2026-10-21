package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ProductReportRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * "Duyet san pham moi dang" + "Go san pham vi pham".
 *
 * Every product a seller creates or edits goes through an automatic content check (banned keywords,
 * contact details to trade off-platform, an abnormally low price for its category, ...). What happens next:
 *  - platform setting product.requireApproval on: every new product waits (PENDING) for an admin;
 *  - otherwise a new product is live at once, unless a HIGH-severity flag holds it for review;
 *  - an approved product edited into a HIGH-severity violation goes back to PENDING.
 * Admins approve / reject / remove (with the violated policy) / restore, and handle buyers' reports.
 * A product that is not APPROVED is never visible: `active` is forced off, and the seller can't switch it on.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ProductModerationService {

    /** Policies a product can break (used for removals and buyers' reports). */
    public static final Map<String, String> VIOLATIONS = new LinkedHashMap<>();

    static {
        VIOLATIONS.put("COUNTERFEIT", "Hàng giả, hàng nhái");
        VIOLATIONS.put("PROHIBITED", "Hàng cấm kinh doanh");
        VIOLATIONS.put("MISLEADING", "Thông tin sai sự thật, không đúng mô tả");
        VIOLATIONS.put("OFF_PLATFORM", "Lôi kéo giao dịch ngoài sàn");
        VIOLATIONS.put("PRICE", "Giá bất thường, nghi lừa đảo");
        VIOLATIONS.put("SPAM", "Đăng trùng lặp, spam");
        VIOLATIONS.put("INAPPROPRIATE", "Hình ảnh, nội dung phản cảm");
        VIOLATIONS.put("OTHER", "Vi phạm khác");
    }

    public static final List<String> DEFAULT_BANNED = List.of("hàng fake", "super fake", "replica", "rep 1:1", "hàng nhái", "ma túy", "cần sa",
            "súng", "đạn", "vũ khí", "thuốc lá điện tử", "vape", "tiền giả", "bằng giả", "thuốc phá thai", "thuốc kích dục", "pháo nổ", "sim rác");

    record Flag(String code, String severity, String label) {
        int score() {
            return switch (severity) { case "HIGH" -> 50; case "MEDIUM" -> 20; default -> 5; };
        }
    }

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:0|\\+?84)(?:3|5|7|8|9)\\d{8}(?!\\d)");
    private static final List<String> CONTACT_WORDS = List.of("zalo", "facebook com", "fb com", "m me", "t me", "inbox", "ib minh", "lien he ngoai",
            "chuyen khoan truoc", "telegram", "messenger");
    private static final int REPORTS_TO_ALERT = 3;

    private final ProductRepository productRepository;
    private final ProductReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PlatformSettingService settings;
    private final AdminAuditService auditService;
    private final NotificationService notificationService;

    // ==================== automatic content check ====================

    List<Flag> check(Product p) {
        List<Flag> flags = new ArrayList<>();
        String raw = (p.getName() == null ? "" : p.getName()) + " " + (p.getDescription() == null ? "" : p.getDescription());
        String words = " " + TextNormalizer.normalize(raw).replaceAll("[^a-z0-9]+", " ").trim() + " ";

        List<String> hits = settings.bannedKeywords().stream()
                .filter(k -> {
                    String n = TextNormalizer.normalize(k).replaceAll("[^a-z0-9]+", " ").trim();
                    return !n.isEmpty() && words.contains(" " + n + " ");
                }).toList();
        if (!hits.isEmpty()) {
            flags.add(new Flag("BANNED", "HIGH", "Chứa từ khóa cấm: " + hits.stream().map(h -> "“" + h + "”").collect(Collectors.joining(", "))));
        }
        // a phone number written with spaces or dots ("0912 345 678") still counts
        String digits = raw.replaceAll("(?<=\\d)[ .\\-](?=\\d)", "");
        if (PHONE.matcher(digits).find() || CONTACT_WORDS.stream().anyMatch(w -> words.contains(" " + w + " "))) {
            flags.add(new Flag("CONTACT", "MEDIUM", "Có số điện thoại / Zalo / link liên hệ ngoài sàn"));
        }
        if (p.getCategory() != null && p.getPrice() != null) {
            List<Double> prices = productRepository.livePricesInCategory(p.getCategory().getId(), p.getId() == null ? -1L : p.getId(),
                    PageRequest.of(0, 500)).stream().map(BigDecimal::doubleValue).sorted().toList();
            if (prices.size() >= 5) {
                double median = prices.get(prices.size() / 2);
                if (p.getPrice().doubleValue() < median * 0.2) {
                    flags.add(new Flag("LOW_PRICE", "MEDIUM", "Giá thấp bất thường: " + NotificationService.vnd(p.getPrice())
                            + " so với giá phổ biến " + NotificationService.vnd(BigDecimal.valueOf(Math.round(median))) + " của ngành hàng"));
                }
            }
        }
        String name = p.getName() == null ? "" : p.getName();
        long letters = name.chars().filter(Character::isLetter).count();
        long upper = name.chars().filter(Character::isUpperCase).count();
        if (letters >= 10 && upper >= letters * 0.8) flags.add(new Flag("CAPS", "LOW", "Tên viết hoa toàn bộ"));
        if (name.chars().filter(ch -> "!$%*#@~^".indexOf(ch) >= 0).count() >= 3) flags.add(new Flag("SYMBOLS", "LOW", "Tên có nhiều ký tự đặc biệt"));
        if (p.getImageUrls() == null || p.getImageUrls().isEmpty()) flags.add(new Flag("NO_IMAGE", "LOW", "Chưa có ảnh sản phẩm"));
        if (p.getDescription() == null || p.getDescription().trim().length() < 30) flags.add(new Flag("SHORT_DESC", "LOW", "Mô tả quá ngắn"));
        return flags;
    }

    /** After a seller creates (isNew) or edits a product: run the checks and decide whether it can stay live. */
    public void onSellerSave(Product p, boolean isNew) {
        List<Flag> flags = check(p);
        p.setModerationFlags(flags.isEmpty() ? null : flags.stream().map(f -> f.code() + "|" + f.severity() + "|" + f.label())
                .collect(Collectors.joining(";")));
        p.setRiskScore(Math.min(100, flags.stream().mapToInt(Flag::score).sum()));
        List<Flag> high = flags.stream().filter(f -> f.severity().equals("HIGH")).toList();
        String held = high.isEmpty() ? null : "Tự động chuyển kiểm duyệt: " + high.stream().map(Flag::label).collect(Collectors.joining("; "));
        if (isNew) {
            if (settings.requireProductApproval() || held != null) setStatus(p, ProductModeration.PENDING, held);
            else setStatus(p, ProductModeration.APPROVED, null);
        } else if (p.getModerationStatus() == ProductModeration.APPROVED && held != null) {
            setStatus(p, ProductModeration.PENDING, held);
        }
        if (p.getModerationStatus() != ProductModeration.APPROVED) p.setActive(false);
        if (held != null) {
            auditService.notifyAdmins(AdminPermission.PRODUCTS, "Sản phẩm bị giữ lại kiểm duyệt",
                    "\"" + p.getName() + "\" – " + high.get(0).label(), "/admin/products?status=PENDING");
        }
    }

    /** The seller tries to show a product: only approved ones can be visible. */
    public void ensureCanShow(Product p) {
        switch (p.getModerationStatus()) {
            case PENDING -> throw bad("Sản phẩm đang chờ Shoppi duyệt, sẽ hiển thị sau khi được duyệt");
            case REJECTED -> throw bad("Sản phẩm chưa được duyệt, hãy sửa theo góp ý rồi gửi duyệt lại");
            case REMOVED -> throw bad("Sản phẩm đã bị gỡ do vi phạm chính sách, hãy sửa rồi gửi duyệt lại");
            default -> { }
        }
    }

    /** "Gui duyet lai" after fixing a rejected / removed product. */
    public void resubmit(Product p) {
        if (p.getModerationStatus() != ProductModeration.REJECTED && p.getModerationStatus() != ProductModeration.REMOVED) {
            throw bad(p.getModerationStatus() == ProductModeration.PENDING ? "Sản phẩm đang chờ duyệt" : "Sản phẩm đã được duyệt");
        }
        onSellerSave(p, false);
        setStatus(p, ProductModeration.PENDING, null);
        p.setActive(false);
        auditService.notifyAdmins(AdminPermission.PRODUCTS, "Sản phẩm gửi duyệt lại", "\"" + p.getName() + "\" đã được người bán sửa và gửi duyệt lại",
                "/admin/products?status=PENDING");
    }

    // ==================== buyers' reports ====================

    public Map<String, Object> report(Long productId, String reason, String detail) {
        Product p = productRepository.findById(productId).filter(Product::isActive)
                .orElseThrow(() -> new ApiException("Sản phẩm không tồn tại", HttpStatus.NOT_FOUND));
        Long me = CurrentUser.id();
        if (p.getShop().getSeller().getId().equals(me)) throw bad("Bạn không thể báo cáo sản phẩm của chính mình");
        if (!VIOLATIONS.containsKey(reason)) throw bad("Vui lòng chọn lý do báo cáo");
        String d = detail == null ? null : detail.trim();
        if (d != null && d.length() > 1000) throw bad("Mô tả tối đa 1000 ký tự");
        if (reportRepository.existsByProductIdAndReporterIdAndStatus(productId, me, ProductReport.Status.OPEN)) {
            throw bad("Bạn đã báo cáo sản phẩm này, Shoppi đang xem xét");
        }
        reportRepository.save(ProductReport.builder().product(p).reporter(userRepository.getReferenceById(me)).reason(reason)
                .detail(d == null || d.isEmpty() ? null : d).build());
        long open = reportRepository.countOpenByProduct(ProductReport.Status.OPEN, List.of(productId)).stream()
                .findFirst().map(r -> ((Number) r[1]).longValue()).orElse(0L);
        if (open == REPORTS_TO_ALERT) {
            auditService.notifyAdmins(AdminPermission.PRODUCTS, "Sản phẩm bị báo cáo nhiều lần",
                    "\"" + p.getName() + "\" đã bị " + open + " người dùng báo cáo", "/admin/products?status=REPORTED");
        }
        return Map.of("message", "Cảm ơn bạn đã báo cáo, Shoppi sẽ xem xét trong thời gian sớm nhất");
    }

    // ==================== admin ====================

    /** status: PENDING | APPROVED | REJECTED | REMOVED | REPORTED (open reports) | ALL */
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(String status, Long categoryId, String q, int page, int size) {
        String st = status == null || status.isBlank() ? "ALL" : status.toUpperCase();
        ProductModeration ms = null;
        boolean reported = st.equals("REPORTED");
        if (!reported && !st.equals("ALL")) {
            try {
                ms = ProductModeration.valueOf(st);
            } catch (IllegalArgumentException e) {
                throw bad("Trạng thái không hợp lệ");
            }
        }
        // the review queue: riskiest first, then oldest; otherwise newest first
        Sort sort = ms == ProductModeration.PENDING || reported ? Sort.by(Sort.Order.desc("riskScore"), Sort.Order.asc("createdAt"))
                : Sort.by(Sort.Direction.DESC, "id");
        var result = productRepository.searchForAdmin(ms, reported, ProductReport.Status.OPEN, categoryId,
                q == null ? "" : TextNormalizer.normalize(q.trim()), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort));
        Map<Long, Long> reports = new HashMap<>();
        if (!result.isEmpty()) {
            reportRepository.countOpenByProduct(ProductReport.Status.OPEN, result.getContent().stream().map(Product::getId).toList())
                    .forEach(r -> reports.put((Long) r[0], ((Number) r[1]).longValue()));
        }
        return PageResponse.of(result, p -> row(p, reports.getOrDefault(p.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> counts() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (ProductModeration s : ProductModeration.values()) m.put(s.name(), productRepository.countByModerationStatus(s));
        m.put("REPORTED", productRepository.searchForAdmin(null, true, ProductReport.Status.OPEN, null, "", PageRequest.of(0, 1)).getTotalElements());
        m.put("openReports", reportRepository.countByStatus(ProductReport.Status.OPEN));
        m.put("requireApproval", settings.requireProductApproval());
        m.put("violations", VIOLATIONS.entrySet().stream().map(e -> AdminUserService.map("key", e.getKey(), "label", e.getValue())).toList());
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        Product p = find(id);
        List<ProductReport> reports = reportRepository.findByProductIdOrderByIdDesc(id);
        Map<String, Object> m = row(p, reports.stream().filter(r -> r.getStatus() == ProductReport.Status.OPEN).count());
        m.put("description", p.getDescription());
        m.put("imageUrls", List.copyOf(p.getImageUrls()));
        m.put("videoUrl", p.getVideoUrl());
        m.put("originalPrice", p.getOriginalPrice());
        m.put("variants", p.getVariants().stream().map(v -> AdminUserService.map("name", v.getName(), "stock", v.getStock(),
                "extraPrice", v.getExtraPrice())).toList());
        m.put("shopRemovedCount", productRepository.countByShopIdAndModerationStatus(p.getShop().getId(), ProductModeration.REMOVED));
        m.put("reports", reports.stream().map(r -> AdminUserService.map("id", r.getId(), "reason", r.getReason(),
                "reasonLabel", VIOLATIONS.get(r.getReason()), "detail", r.getDetail(), "status", r.getStatus().name(),
                "reporter", r.getReporter().getFullName(), "createdAt", r.getCreatedAt())).toList());
        m.put("history", auditService.forTarget("PRODUCT", id, 0, 20).getItems());
        return m;
    }

    public Map<String, Object> approve(Long id) {
        Product p = find(id);
        if (p.getModerationStatus() == ProductModeration.APPROVED) throw bad("Sản phẩm đã được duyệt");
        if (p.getModerationStatus() == ProductModeration.REMOVED) throw bad("Sản phẩm đã bị gỡ, hãy dùng \"Khôi phục\"");
        setStatus(p, ProductModeration.APPROVED, null);
        p.setActive(true);
        notifySeller(p, "Sản phẩm đã được duyệt", "\"" + p.getName() + "\" đã hiển thị với người mua.");
        auditService.log("APPROVE_PRODUCT", "PRODUCT", p.getId(), p.getName(), "Duyệt sản phẩm");
        return row(p, 0);
    }

    /** Approves the pending ones among ids; returns { approved, skipped }. */
    public Map<String, Object> bulkApprove(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw bad("Chưa chọn sản phẩm nào");
        if (ids.size() > 100) throw bad("Mỗi lần duyệt tối đa 100 sản phẩm");
        int ok = 0;
        for (Product p : productRepository.findAllById(ids)) {
            if (p.getModerationStatus() != ProductModeration.PENDING) continue;
            approve(p.getId());
            ok++;
        }
        return Map.of("approved", ok, "skipped", ids.size() - ok);
    }

    public Map<String, Object> reject(Long id, String reason) {
        Product p = find(id);
        if (p.getModerationStatus() != ProductModeration.PENDING) throw bad("Chỉ từ chối được sản phẩm đang chờ duyệt");
        String r = reason(reason);
        setStatus(p, ProductModeration.REJECTED, r);
        p.setActive(false);
        notifySeller(p, "Sản phẩm chưa được duyệt", "\"" + p.getName() + "\": " + r + ". Hãy sửa sản phẩm rồi gửi duyệt lại.");
        auditService.log("REJECT_PRODUCT", "PRODUCT", p.getId(), p.getName(), r);
        return row(p, 0);
    }

    /** "Go san pham vi pham": hidden at once, the seller is told which policy was broken; open reports are resolved. */
    public Map<String, Object> remove(Long id, String violation, String reason) {
        Product p = find(id);
        if (p.getModerationStatus() == ProductModeration.REMOVED) throw bad("Sản phẩm đã bị gỡ");
        if (!VIOLATIONS.containsKey(violation)) throw bad("Vui lòng chọn loại vi phạm");
        String r = reason(reason);
        setStatus(p, ProductModeration.REMOVED, r);
        p.setViolationType(violation);
        p.setActive(false);
        reportRepository.closeOpen(p.getId(), ProductReport.Status.OPEN, ProductReport.Status.RESOLVED, LocalDateTime.now());
        notifySeller(p, "Sản phẩm bị gỡ do vi phạm chính sách", "\"" + p.getName() + "\" – " + VIOLATIONS.get(violation) + ": " + r
                + ". Sản phẩm đã bị ẩn khỏi sàn; bạn có thể sửa và gửi duyệt lại.");
        auditService.log("REMOVE_PRODUCT", "PRODUCT", p.getId(), p.getName(), VIOLATIONS.get(violation) + " – " + r);
        return row(p, 0);
    }

    /** Undo a removal (e.g. a mistaken report). */
    public Map<String, Object> restore(Long id) {
        Product p = find(id);
        if (p.getModerationStatus() != ProductModeration.REMOVED) throw bad("Sản phẩm không ở trạng thái bị gỡ");
        setStatus(p, ProductModeration.APPROVED, null);
        p.setViolationType(null);
        p.setActive(true);
        notifySeller(p, "Sản phẩm đã được khôi phục", "\"" + p.getName() + "\" đã hiển thị trở lại với người mua.");
        auditService.log("RESTORE_PRODUCT", "PRODUCT", p.getId(), p.getName(), "Khôi phục sản phẩm");
        return row(p, 0);
    }

    /** The reports were unfounded: close them, the product stays as it is. */
    public Map<String, Object> dismissReports(Long id) {
        Product p = find(id);
        int n = reportRepository.closeOpen(p.getId(), ProductReport.Status.OPEN, ProductReport.Status.DISMISSED, LocalDateTime.now());
        if (n == 0) throw bad("Sản phẩm không có báo cáo nào đang chờ xử lý");
        auditService.log("DISMISS_REPORTS", "PRODUCT", p.getId(), p.getName(), "Bỏ qua " + n + " báo cáo");
        return row(p, 0);
    }

    // ==================== helpers ====================

    private static void setStatus(Product p, ProductModeration status, String reason) {
        p.setModerationStatus(status);
        p.setModerationReason(reason);
        p.setModeratedAt(LocalDateTime.now());
    }

    private void notifySeller(Product p, String title, String content) {
        notificationService.notify(p.getShop().getSeller().getId(), NotificationType.SYSTEM, title, content,
                "/seller/products?tab=moderation", p.getThumbnailUrl());
    }

    /** "CODE|SEVERITY|label;..." -> [{ code, severity, label }] */
    public static List<Map<String, Object>> flags(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        return Arrays.stream(stored.split(";")).map(s -> s.split("\\|", 3)).filter(a -> a.length == 3)
                .map(a -> AdminUserService.map("code", a[0], "severity", a[1], "label", a[2])).toList();
    }

    private Map<String, Object> row(Product p, long openReports) {
        Shop s = p.getShop();
        return AdminUserService.map("id", p.getId(), "name", p.getName(), "thumbnailUrl", p.getThumbnailUrl(), "price", p.getPrice(),
                "stock", p.getStock(), "active", p.isActive(), "status", p.getModerationStatus().name(), "reason", p.getModerationReason(),
                "violationType", p.getViolationType(), "violationLabel", p.getViolationType() == null ? null : VIOLATIONS.get(p.getViolationType()),
                "flags", flags(p.getModerationFlags()), "riskScore", p.getRiskScore(), "moderatedAt", p.getModeratedAt(), "createdAt", p.getCreatedAt(),
                "category", p.getCategory() == null ? null : p.getCategory().getName(), "soldCount", p.getSoldCount(), "openReports", openReports,
                "shop", AdminUserService.map("id", s.getId(), "name", s.getName(), "sellerId", s.getSeller().getId()));
    }

    private Product find(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ApiException("Sản phẩm không tồn tại", HttpStatus.NOT_FOUND));
    }

    private static String reason(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.length() < 5) throw bad("Vui lòng nhập lý do (ít nhất 5 ký tự)");
        if (r.length() > 500) throw bad("Lý do tối đa 500 ký tự");
        return r;
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
