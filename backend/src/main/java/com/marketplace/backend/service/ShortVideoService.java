package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.repository.ShortVideoRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Shoppi Video": shops post short videos tagged with their products; buyers scroll the feed, like, and buy from it.
 * Only a shop's own uploads and its own active products can be used, the caption goes through the same banned-word
 * and off-platform-contact check as product listings, and an admin can hide a video.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ShortVideoService {

    public static final int MAX_PRODUCTS = 6;
    public static final int MAX_ACTIVE_PER_SHOP = 50;
    private static final int CAPTION_MAX = 300;

    private final ShortVideoRepository videoRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final ProductModerationService moderationService;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;

    // ==================== buyers ====================

    /** sort: hot (most liked and watched) | new; productId / shopId narrow it down. */
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> feed(String sort, Long productId, Long shopId, int page, int size, Long viewerId) {
        Sort order = "new".equals(sort) ? Sort.by(Sort.Direction.DESC, "createdAt")
                : Sort.by(Sort.Direction.DESC, "likeCount").and(Sort.by(Sort.Direction.DESC, "viewCount")).and(Sort.by(Sort.Direction.DESC, "createdAt"));
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 20), order);
        Page<ShortVideo> videos = productId != null ? videoRepository.feedForProduct(productId, pageable)
                : shopId != null ? videoRepository.feedForShop(shopId, pageable) : videoRepository.feed(pageable);
        return PageResponse.of(videos, views(videos.getContent(), viewerId, false));
    }

    /** One view of a video in the feed (the page counts each video once per visit). */
    public void view(Long id) {
        videoRepository.addView(id);
    }

    public Map<String, Object> like(Long id) {
        ShortVideo v = activeVideo(id);
        if (videoRepository.like(v.getId(), CurrentUser.id()) > 0) videoRepository.addLikes(v.getId(), 1);
        return likeState(v.getId(), true);
    }

    public Map<String, Object> unlike(Long id) {
        ShortVideo v = find(id);
        if (videoRepository.unlike(v.getId(), CurrentUser.id()) > 0) videoRepository.addLikes(v.getId(), -1);
        return likeState(v.getId(), false);
    }

    private Map<String, Object> likeState(Long id, boolean liked) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("liked", liked);
        m.put("likeCount", find(id).getLikeCount());
        return m;
    }

    // ==================== sellers ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine() {
        return viewList(videoRepository.findByShopIdOrderByIdDesc(myShop().getId()), null, true);
    }

    public Map<String, Object> create(String videoUrl, String caption, List<Long> productIds) {
        Shop shop = myShop();
        if (videoRepository.countByShopIdAndStatus(shop.getId(), ShortVideo.Status.ACTIVE) >= MAX_ACTIVE_PER_SHOP) {
            throw bad("Mỗi shop tối đa " + MAX_ACTIVE_PER_SHOP + " video đang hiển thị, hãy xóa bớt video cũ");
        }
        String url = videoUrl == null ? "" : videoUrl.trim();
        String lower = url.toLowerCase(Locale.ROOT);
        // only our own uploads: no hotlinked or external videos in the feed
        if (!url.startsWith("/uploads/") || url.contains("..") || !(lower.endsWith(".mp4") || lower.endsWith(".webm"))) {
            throw bad("Vui lòng tải lên video MP4 hoặc WEBM");
        }
        ShortVideo v = ShortVideo.builder().shopId(shop.getId()).videoUrl(url).build();
        apply(v, shop, caption, productIds);
        return viewList(List.of(videoRepository.save(v)), null, true).get(0);
    }

    public Map<String, Object> update(Long id, String caption, List<Long> productIds) {
        Shop shop = myShop();
        ShortVideo v = ofShop(id, shop);
        apply(v, shop, caption, productIds);
        return viewList(List.of(v), null, true).get(0);
    }

    public void delete(Long id) {
        ShortVideo v = ofShop(id, myShop());
        videoRepository.deleteLikes(v.getId());
        videoRepository.delete(v);
    }

    private void apply(ShortVideo v, Shop shop, String caption, List<Long> productIds) {
        String text = caption == null ? "" : caption.trim();
        if (text.length() > CAPTION_MAX) throw bad("Mô tả video tối đa " + CAPTION_MAX + " ký tự");
        String problem = moderationService.textProblem(text);
        if (problem != null) throw bad(problem);
        List<Long> ids = productIds == null ? List.of() : productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) throw bad("Vui lòng gắn ít nhất 1 sản phẩm của shop vào video");
        if (ids.size() > MAX_PRODUCTS) throw bad("Mỗi video gắn tối đa " + MAX_PRODUCTS + " sản phẩm");
        List<Product> own = productRepository.findByShopIdAndIdIn(shop.getId(), ids);
        if (own.size() != ids.size() || own.stream().anyMatch(p -> !p.isActive())) throw bad("Chỉ gắn được sản phẩm đang bán của shop");
        v.setCaption(text.isEmpty() ? null : text);
        v.setProductIds(new ArrayList<>(ids));
    }

    // ==================== admin ====================

    /** status: ACTIVE | HIDDEN */
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> adminList(String status, int page, int size) {
        ShortVideo.Status st;
        try {
            st = ShortVideo.Status.valueOf(status == null ? "ACTIVE" : status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw bad("Trạng thái không hợp lệ");
        }
        Page<ShortVideo> videos = videoRepository.findByStatus(st, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by("id").descending()));
        return PageResponse.of(videos, views(videos.getContent(), null, true));
    }

    public Map<String, Object> hide(Long id, String reason) {
        ShortVideo v = find(id);
        String r = reason == null ? "" : reason.trim();
        if (r.length() < 5) throw bad("Vui lòng nhập lý do (ít nhất 5 ký tự)");
        v.setStatus(ShortVideo.Status.HIDDEN);
        v.setHiddenReason(r.length() > 300 ? r.substring(0, 300) : r);
        shopRepository.findById(v.getShopId()).ifPresent(s -> notificationService.notify(s.getSeller().getId(), NotificationType.SYSTEM,
                "Video của shop đã bị ẩn", "Lý do: " + v.getHiddenReason() + ". Bạn có thể sửa mô tả hoặc liên hệ Trung tâm hỗ trợ.", "/seller/videos"));
        auditService.log("HIDE_VIDEO", "SHOP", v.getShopId(), "Video #" + v.getId(), v.getHiddenReason());
        return viewList(List.of(v), null, true).get(0);
    }

    public Map<String, Object> restore(Long id) {
        ShortVideo v = find(id);
        v.setStatus(ShortVideo.Status.ACTIVE);
        v.setHiddenReason(null);
        auditService.log("RESTORE_VIDEO", "SHOP", v.getShopId(), "Video #" + v.getId(), "Hiện lại video");
        return viewList(List.of(v), null, true).get(0);
    }

    // ==================== views ====================

    private List<Map<String, Object>> viewList(List<ShortVideo> videos, Long viewerId, boolean owner) {
        Function<ShortVideo, Map<String, Object>> view = views(videos, viewerId, owner);
        return videos.stream().map(view).toList();
    }

    // one batch of shops, products and likes for the whole page
    private Function<ShortVideo, Map<String, Object>> views(List<ShortVideo> videos, Long viewerId, boolean owner) {
        Map<Long, Shop> shops = shopRepository.findAllById(videos.stream().map(ShortVideo::getShopId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Shop::getId, s -> s));
        Map<Long, Product> products = productRepository.findAllById(videos.stream().flatMap(v -> v.getProductIds().stream()).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Product::getId, p -> p));
        Set<Long> liked = viewerId == null || videos.isEmpty() ? Set.of()
                : new HashSet<>(videoRepository.likedAmong(viewerId, videos.stream().map(ShortVideo::getId).toList()));
        return v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("videoUrl", v.getVideoUrl());
            m.put("caption", v.getCaption());
            m.put("viewCount", v.getViewCount());
            m.put("likeCount", v.getLikeCount());
            m.put("liked", liked.contains(v.getId()));
            m.put("createdAt", v.getCreatedAt());
            Shop s = shops.get(v.getShopId());
            m.put("shop", s == null ? null : Map.of("id", s.getId(), "name", s.getName(), "logoUrl", Objects.requireNonNullElse(s.getLogoUrl(), ""),
                    "mall", s.isMall(), "favorite", s.isFavorite()));
            // buyers only see products still for sale; the shop sees every tagged product
            m.put("products", v.getProductIds().stream().map(products::get).filter(Objects::nonNull)
                    .filter(p -> owner || p.isActive()).map(p -> {
                        Map<String, Object> x = new LinkedHashMap<>();
                        var r = com.marketplace.backend.dto.response.ProductResponse.from(p);
                        x.put("id", p.getId());
                        x.put("name", p.getName());
                        x.put("thumbnailUrl", r.getThumbnailUrl());
                        x.put("price", r.getPrice());
                        x.put("originalPrice", r.getOriginalPrice());
                        x.put("discountPercent", r.getDiscountPercent());
                        x.put("active", p.isActive());
                        return x;
                    }).toList());
            if (owner) {
                m.put("status", v.getStatus().name());
                m.put("hiddenReason", v.getHiddenReason());
                m.put("productIds", v.getProductIds());
            }
            return m;
        };
    }

    // ==================== helpers ====================

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id()).orElseThrow(() -> bad("Bạn chưa có shop"));
    }

    private ShortVideo ofShop(Long id, Shop shop) {
        ShortVideo v = find(id);
        if (!v.getShopId().equals(shop.getId())) throw new ApiException("Video không tồn tại", HttpStatus.NOT_FOUND);
        return v;
    }

    private ShortVideo activeVideo(Long id) {
        ShortVideo v = find(id);
        if (v.getStatus() != ShortVideo.Status.ACTIVE) throw new ApiException("Video không tồn tại", HttpStatus.NOT_FOUND);
        return v;
    }

    private ShortVideo find(Long id) {
        return videoRepository.findById(id).orElseThrow(() -> new ApiException("Video không tồn tại", HttpStatus.NOT_FOUND));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
