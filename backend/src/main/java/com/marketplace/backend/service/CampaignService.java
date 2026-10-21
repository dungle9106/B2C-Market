package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.VoucherResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.BannerRepository;
import com.marketplace.backend.repository.FlashSaleEventRepository;
import com.marketplace.backend.repository.PromoCampaignRepository;
import com.marketplace.backend.repository.VoucherRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * "Chuong trinh khuyen mai toan san": a named period (e.g. "Sieu Sale 11.11") with a landing page that shows
 * its Shoppi Vouchers and its Flash Sale slots. Vouchers, flash sale slots and banners are linked to it.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CampaignService {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final PromoCampaignRepository campaignRepository;
    private final VoucherRepository voucherRepository;
    private final FlashSaleEventRepository eventRepository;
    private final BannerRepository bannerRepository;
    private final PlatformFlashSaleService flashSaleService;
    private final AdminAuditService auditService;

    // ==================== admin ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        LocalDateTime now = LocalDateTime.now();
        return campaignRepository.findAllByOrderByStartAtDesc().stream().map(c -> {
            Map<String, Object> m = view(c, now);
            m.put("vouchers", voucherRepository.countByCampaignId(c.getId()));
            m.put("flashSales", eventRepository.countByCampaignId(c.getId()));
            m.put("banners", bannerRepository.countByCampaignId(c.getId()));
            return m;
        }).toList();
    }

    /** body: { name, slug?, tagline?, description?, bannerUrl?, themeColor?, startAt, endAt, active? } */
    public Map<String, Object> create(Map<String, Object> body) {
        PromoCampaign c = PromoCampaign.builder().createdBy(CurrentUser.id()).build();
        fill(c, body);
        campaignRepository.save(c);
        auditService.log("CREATE_CAMPAIGN", "CAMPAIGN", c.getId(), c.getName(), c.getStartAt().toLocalDate() + " → " + c.getEndAt().toLocalDate());
        return view(c, LocalDateTime.now());
    }

    public Map<String, Object> update(Long id, Map<String, Object> body) {
        PromoCampaign c = find(id);
        fill(c, body);
        auditService.log("UPDATE_CAMPAIGN", "CAMPAIGN", c.getId(), c.getName(), null);
        return view(c, LocalDateTime.now());
    }

    public Map<String, Object> toggle(Long id) {
        PromoCampaign c = find(id);
        c.setActive(!c.isActive());
        auditService.log(c.isActive() ? "SHOW_CAMPAIGN" : "HIDE_CAMPAIGN", "CAMPAIGN", c.getId(), c.getName(), null);
        return view(c, LocalDateTime.now());
    }

    /** Only a programme nothing points to yet can be deleted; otherwise hide it. */
    public void delete(Long id) {
        PromoCampaign c = find(id);
        if (voucherRepository.countByCampaignId(id) + eventRepository.countByCampaignId(id) + bannerRepository.countByCampaignId(id) > 0) {
            throw bad("Chương trình đang có voucher, Flash Sale hoặc banner gắn kèm. Hãy ẩn chương trình thay vì xóa");
        }
        campaignRepository.delete(c);
        auditService.log("DELETE_CAMPAIGN", "CAMPAIGN", id, c.getName(), null);
    }

    private void fill(PromoCampaign c, Map<String, Object> body) {
        String name = str(body.get("name"));
        if (name == null || name.length() < 3 || name.length() > 120) throw bad("Tên chương trình từ 3 đến 120 ký tự");
        String slug = str(body.get("slug"));
        slug = slug == null ? slugify(name) : slug.toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(slug).matches() || slug.length() > 80) throw bad("Đường dẫn chỉ gồm chữ thường không dấu, số và dấu gạch ngang");
        if (!slug.equals(c.getSlug()) && campaignRepository.existsBySlug(slug)) throw bad("Đường dẫn /campaigns/" + slug + " đã được dùng");
        LocalDateTime start = PlatformFlashSaleService.time(body.get("startAt"));
        LocalDateTime end = PlatformFlashSaleService.time(body.get("endAt"));
        if (start == null || end == null) throw bad("Vui lòng chọn thời gian diễn ra");
        if (!end.isAfter(start)) throw bad("Thời gian kết thúc phải sau thời gian bắt đầu");
        if (end.isAfter(start.plusDays(90))) throw bad("Một chương trình kéo dài tối đa 90 ngày");
        String color = str(body.get("themeColor"));
        if (color != null && !COLOR.matcher(color).matches()) throw bad("Màu chủ đề không hợp lệ (dạng #RRGGBB)");
        String banner = str(body.get("bannerUrl"));
        if (banner != null && !banner.startsWith("/uploads/") && !banner.startsWith("https://")) throw bad("Ảnh banner không hợp lệ");
        String tagline = str(body.get("tagline"));
        String description = str(body.get("description"));
        if (tagline != null && tagline.length() > 200) throw bad("Khẩu hiệu tối đa 200 ký tự");
        if (description != null && description.length() > 2000) throw bad("Thể lệ tối đa 2000 ký tự");

        c.setName(name);
        c.setSlug(slug);
        c.setTagline(tagline);
        c.setDescription(description);
        c.setBannerUrl(banner);
        c.setThemeColor(color == null ? "#ee4d2d" : color.toLowerCase(Locale.ROOT));
        c.setStartAt(start);
        c.setEndAt(end);
        if (body.containsKey("active")) c.setActive(Boolean.TRUE.equals(body.get("active")));
    }

    // ==================== buyer ====================

    /** Programmes running or coming soon, for the home page. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> visible() {
        LocalDateTime now = LocalDateTime.now();
        return campaignRepository.visible(now).stream().map(c -> view(c, now)).toList();
    }

    /** The landing page: the programme, its vouchers (running or upcoming) and its Flash Sale slots with products. */
    @Transactional(readOnly = true)
    public Map<String, Object> landing(String slug) {
        LocalDateTime now = LocalDateTime.now();
        PromoCampaign c = campaignRepository.findBySlug(slug).filter(PromoCampaign::isActive)
                .orElseThrow(() -> new ApiException("Chương trình không tồn tại hoặc đã kết thúc", HttpStatus.NOT_FOUND));
        Map<String, Object> m = view(c, now);
        m.put("vouchers", voucherRepository.findByCampaignIdOrderByStartAt(c.getId()).stream()
                .filter(v -> v.isActive() && v.isPublicVisible() && v.getEndAt().isAfter(now) && !v.isUsedUp())
                .map(v -> {
                    VoucherResponse r = VoucherResponse.from(v);
                    r.setUsedByMe(null);
                    return r;
                }).toList());
        m.put("flashSales", eventRepository.findByCampaignIdAndCancelledFalseOrderByStartAt(c.getId()).stream()
                .filter(e -> e.getEndAt().isAfter(now))
                .map(e -> flashSaleService.publicSlot(e, now, 24)).toList());
        return m;
    }

    // ==================== helpers ====================

    static Map<String, Object> view(PromoCampaign c, LocalDateTime now) {
        return AdminUserService.map("id", c.getId(), "name", c.getName(), "slug", c.getSlug(), "tagline", c.getTagline(),
                "description", c.getDescription(), "bannerUrl", c.getBannerUrl(), "themeColor", c.getThemeColor(),
                "startAt", c.getStartAt(), "endAt", c.getEndAt(), "active", c.isActive(), "status", c.status(now),
                "createdAt", c.getCreatedAt());
    }

    /** "Siêu Sale 11.11" -> "sieu-sale-11-11" */
    static String slugify(String s) {
        String n = Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.length() > 80 ? slug.substring(0, 80).replaceAll("-+$", "") : slug;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private PromoCampaign find(Long id) {
        return campaignRepository.findById(id).orElseThrow(() -> new ApiException("Chương trình không tồn tại", HttpStatus.NOT_FOUND));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
