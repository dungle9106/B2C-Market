package com.marketplace.backend.service;

import com.marketplace.backend.entity.Banner;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.BannerRepository;
import com.marketplace.backend.repository.PromoCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Home page banners: the marketing team uploads them, schedules them, orders them, and sees how often each
 * one is shown and clicked (CTR).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BannerService {

    static final int MAX_SHOWING = 8;   // carousel slides shown at once

    private final BannerRepository bannerRepository;
    private final PromoCampaignRepository campaignRepository;
    private final AdminAuditService auditService;

    // ==================== admin ====================

    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> m = new LinkedHashMap<>();
        for (Banner.Placement p : Banner.Placement.values()) {
            m.put(p.name(), bannerRepository.findByPlacementOrderBySortOrderAscIdAsc(p).stream().map(b -> view(b, now)).toList());
        }
        return m;
    }

    /** body: { title, imageUrl, linkUrl?, placement, startAt?, endAt?, campaignId?, active? } */
    public Map<String, Object> create(Map<String, Object> body) {
        Banner b = Banner.builder().build();
        fill(b, body);
        b.setSortOrder(bannerRepository.maxSort(b.getPlacement()) + 1);
        bannerRepository.save(b);
        auditService.log("CREATE_BANNER", "BANNER", b.getId(), b.getTitle(), label(b.getPlacement()));
        return view(b, LocalDateTime.now());
    }

    public Map<String, Object> update(Long id, Map<String, Object> body) {
        Banner b = find(id);
        Banner.Placement before = b.getPlacement();
        fill(b, body);
        if (before != b.getPlacement()) b.setSortOrder(bannerRepository.maxSort(b.getPlacement()) + 1);
        auditService.log("UPDATE_BANNER", "BANNER", b.getId(), b.getTitle(), null);
        return view(b, LocalDateTime.now());
    }

    public Map<String, Object> toggle(Long id) {
        Banner b = find(id);
        b.setActive(!b.isActive());
        auditService.log(b.isActive() ? "SHOW_BANNER" : "HIDE_BANNER", "BANNER", b.getId(), b.getTitle(), null);
        return view(b, LocalDateTime.now());
    }

    public void delete(Long id) {
        Banner b = find(id);
        bannerRepository.delete(b);
        auditService.log("DELETE_BANNER", "BANNER", id, b.getTitle(), null);
    }

    /** ids of one placement in display order */
    public void reorder(String placement, List<Long> ids) {
        Banner.Placement p = placement(placement);
        List<Banner> banners = bannerRepository.findByPlacementOrderBySortOrderAscIdAsc(p);
        if (ids == null || ids.size() != banners.size() || !new HashSet<>(ids).equals(banners.stream().map(Banner::getId).collect(java.util.stream.Collectors.toSet()))) {
            throw bad("Danh sách sắp xếp không khớp với các banner hiện có");
        }
        Map<Long, Banner> byId = new HashMap<>();
        banners.forEach(b -> byId.put(b.getId(), b));
        for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).setSortOrder(i + 1);
    }

    private void fill(Banner b, Map<String, Object> body) {
        String title = str(body.get("title"));
        if (title == null || title.length() > 120) throw bad("Tiêu đề banner từ 1 đến 120 ký tự");
        String image = str(body.get("imageUrl"));
        if (image == null || !image.startsWith("/uploads/") && !image.startsWith("https://")) throw bad("Vui lòng tải ảnh banner lên");
        String link = str(body.get("linkUrl"));
        // only in-site links: a banner must not send buyers to another website
        if (link != null && (!link.startsWith("/") || link.startsWith("//"))) throw bad("Liên kết phải là đường dẫn trong Shoppi, bắt đầu bằng /");
        LocalDateTime start = PlatformFlashSaleService.time(body.get("startAt"));
        LocalDateTime end = PlatformFlashSaleService.time(body.get("endAt"));
        if (start != null && end != null && !end.isAfter(start)) throw bad("Thời gian kết thúc phải sau thời gian bắt đầu");
        Object cid = body.get("campaignId");
        b.setTitle(title);
        b.setImageUrl(image);
        b.setLinkUrl(link);
        b.setPlacement(placement(str(body.get("placement"))));
        b.setStartAt(start);
        b.setEndAt(end);
        b.setCampaign(cid instanceof Number n ? campaignRepository.findById(n.longValue()).orElseThrow(() -> bad("Chương trình không tồn tại")) : null);
        // a campaign banner without its own link opens the campaign page
        if (link == null && b.getCampaign() != null) b.setLinkUrl("/campaigns/" + b.getCampaign().getSlug());
        if (body.containsKey("active")) b.setActive(!Boolean.FALSE.equals(body.get("active")));
    }

    // ==================== buyer ====================

    /** Banners showing now, per placement; each listing counts as one view. */
    public Map<String, Object> showing() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> m = new LinkedHashMap<>();
        List<Long> shown = new ArrayList<>();
        for (Banner.Placement p : Banner.Placement.values()) {
            List<Banner> list = bannerRepository.showing(p, now).stream().limit(p == Banner.Placement.HOME_SIDE ? 2 : MAX_SHOWING).toList();
            list.forEach(b -> shown.add(b.getId()));
            m.put(p.name(), list.stream().map(b -> AdminUserService.map("id", b.getId(), "title", b.getTitle(), "imageUrl", b.getImageUrl(),
                    "linkUrl", b.getLinkUrl())).toList());
        }
        if (!shown.isEmpty()) bannerRepository.addView(shown);
        return m;
    }

    /** Counts a click; returns where to go. */
    public Map<String, Object> click(Long id) {
        Banner b = find(id);
        bannerRepository.addClick(id);
        return AdminUserService.map("linkUrl", b.getLinkUrl());
    }

    // ==================== helpers ====================

    static Map<String, Object> view(Banner b, LocalDateTime now) {
        double ctr = b.getViews() == 0 ? 0 : Math.round(b.getClicks() * 1000.0 / b.getViews()) / 10.0;
        return AdminUserService.map("id", b.getId(), "title", b.getTitle(), "imageUrl", b.getImageUrl(), "linkUrl", b.getLinkUrl(),
                "placement", b.getPlacement().name(), "sortOrder", b.getSortOrder(), "startAt", b.getStartAt(), "endAt", b.getEndAt(),
                "active", b.isActive(), "status", b.status(now), "views", b.getViews(), "clicks", b.getClicks(), "ctr", ctr,
                "campaignId", b.getCampaign() == null ? null : b.getCampaign().getId(),
                "campaignName", b.getCampaign() == null ? null : b.getCampaign().getName(), "createdAt", b.getCreatedAt());
    }

    private static String label(Banner.Placement p) {
        return p == Banner.Placement.HOME_CAROUSEL ? "Banner chính trang chủ" : "Banner phụ trang chủ";
    }

    private static Banner.Placement placement(String s) {
        try {
            return Banner.Placement.valueOf(s == null ? "" : s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw bad("Vị trí hiển thị không hợp lệ");
        }
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private Banner find(Long id) {
        return bannerRepository.findById(id).orElseThrow(() -> new ApiException("Banner không tồn tại", HttpStatus.NOT_FOUND));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
