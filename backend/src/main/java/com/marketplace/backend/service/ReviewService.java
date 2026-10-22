package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.ReviewRequest;
import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.dto.response.ReviewResponse;
import com.marketplace.backend.dto.response.ReviewSummaryResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderItemRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ReviewRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.util.ReplyPlaceholders;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final NotificationService notificationService;
    private final org.springframework.context.ApplicationEventPublisher events;

    /** filter: "all", "media", or "1".."5" */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> list(Long productId, String filter, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<Review> result;
        if ("media".equals(filter)) {
            result = reviewRepository.findWithMedia(productId, pageable);
        } else if (filter != null && filter.matches("[1-5]")) {
            result = reviewRepository.findByProductIdAndRatingOrderByCreatedAtDesc(productId, Integer.valueOf(filter), pageable);
        } else {
            result = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable);
        }
        return PageResponse.of(result, ReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(Long productId) {
        Map<Integer, Long> byStars = new LinkedHashMap<>();
        for (int s = 5; s >= 1; s--) byStars.put(s, 0L);
        long total = 0;
        long sum = 0;
        for (Object[] row : reviewRepository.countByRating(productId)) {
            int stars = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            byStars.put(stars, count);
            total += count;
            sum += stars * count;
        }
        double avg = total == 0 ? 0 : Math.round(sum * 10.0 / total) / 10.0;
        return new ReviewSummaryResponse(avg, total, byStars, reviewRepository.countWithMedia(productId));
    }

    public ReviewResponse create(ReviewRequest req) {
        OrderItem item = orderItemRepository.findById(req.getOrderItemId())
                .orElseThrow(() -> new ApiException("Sản phẩm trong đơn hàng không tồn tại", HttpStatus.NOT_FOUND));
        Order order = item.getOrder();
        if (!order.getBuyer().getId().equals(CurrentUser.id())) {
            throw new ApiException("Bạn chỉ có thể đánh giá sản phẩm mình đã mua", HttpStatus.FORBIDDEN);
        }
        if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.REFUNDED
                && order.getStatus() != OrderStatus.RETURN_REQUESTED) {
            throw new ApiException("Bạn chỉ có thể đánh giá sau khi đã nhận hàng", HttpStatus.BAD_REQUEST);
        }
        if (reviewRepository.existsByOrderItemId(item.getId())) {
            throw new ApiException("Bạn đã đánh giá sản phẩm này rồi", HttpStatus.BAD_REQUEST);
        }
        List<String> images = req.getImageUrls() == null ? List.of() : req.getImageUrls().stream()
                .filter(u -> u != null && u.startsWith("/uploads/")) // only photos uploaded through us
                .distinct().toList();

        String video = req.getVideoUrl() != null && req.getVideoUrl().startsWith("/uploads/") ? req.getVideoUrl() : null;

        // shop + delivery stars are rated once per order (with its first review)
        if (order.getShopRating() == null && req.getShopRating() != null) {
            order.setShopRating(req.getShopRating());
            order.setShippingRating(req.getShippingRating());
        }

        Product product = item.getProduct();
        Review review = reviewRepository.save(Review.builder()
                .product(product)
                .buyer(order.getBuyer())
                .orderItem(item)
                .rating(req.getRating())
                .comment(req.getComment() == null ? null : req.getComment().trim())
                .imageUrls(new java.util.ArrayList<>(images))
                .videoUrl(video)
                .build());

        // keep the denormalised average used for rating filters/sorting in step
        int count = product.getRatingCount();
        double avg = (product.getRatingAvg() * count + req.getRating()) / (count + 1);
        product.setRatingCount(count + 1);
        product.setRatingAvg(Math.round(avg * 100.0) / 100.0);
        productRepository.save(product);
        // the shop's review hub picks it up (after commit): analysis, priority, anomaly checks
        events.publishEvent(new com.marketplace.backend.service.reviewhub.ReviewHubEvents.ReviewWritten(review.getId()));

        return ReviewResponse.from(review);
    }

    // ==================== seller side ====================

    static final int REPLY_MAX = 255;

    /** The shop's reviews. rating 1-5 or null; maxRating 1-5 or null; replied true/false or null. */
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> shopReviews(Integer rating, Integer maxRating, Long productId, Boolean replied, int page, int size) {
        Shop shop = myShop();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Integer stars = rating != null && rating >= 1 && rating <= 5 ? rating : null;
        Integer max = maxRating != null && maxRating >= 1 && maxRating <= 5 ? maxRating : null;
        return PageResponse.of(reviewRepository.findForShop(shop.getId(), stars, max, productId, replied, pageable), ReviewService::forSeller);
    }

    public Map<String, Object> reply(Long reviewId, String text) {
        Shop shop = myShop();
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> r.getProduct().getShop().getId().equals(shop.getId()))
                .orElseThrow(() -> new ApiException("Đánh giá không tồn tại", HttpStatus.NOT_FOUND));
        return applyReply(shop, review, text);
    }

    /**
     * "Phan hoi hang loat": the same template to many reviews, personalised per buyer
     * ({ten_khach}, {ten_san_pham}). Reviews already answered or of another shop are skipped.
     */
    public Map<String, Object> bulkReply(List<Long> reviewIds, String text) {
        Shop shop = myShop();
        if (reviewIds == null || reviewIds.isEmpty()) throw new ApiException("Chưa chọn đánh giá nào", HttpStatus.BAD_REQUEST);
        if (reviewIds.size() > 50) throw new ApiException("Mỗi lần phản hồi tối đa 50 đánh giá", HttpStatus.BAD_REQUEST);
        int replied = 0;
        for (Review r : reviewRepository.findAllById(reviewIds)) {
            if (!r.getProduct().getShop().getId().equals(shop.getId()) || r.getSellerReply() != null) continue;
            applyReply(shop, r, text);
            replied++;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("replied", replied);
        m.put("skipped", reviewIds.size() - replied);
        return m;
    }

    private Map<String, Object> applyReply(Shop shop, Review review, String text) {
        String reply = ReplyPlaceholders.fill(text == null ? "" : text.trim(), review.getBuyer().getFullName(), shop.getName(),
                review.getProduct().getName());
        if (reply.isEmpty()) throw new ApiException("Vui lòng nhập nội dung phản hồi", HttpStatus.BAD_REQUEST);
        if (reply.length() > REPLY_MAX) {
            throw new ApiException("Phản hồi tối đa " + REPLY_MAX + " ký tự", HttpStatus.BAD_REQUEST);
        }
        boolean first = review.getSellerReply() == null;
        review.setSellerReply(reply);
        events.publishEvent(new com.marketplace.backend.service.reviewhub.ReviewHubEvents.ReviewReplied(review.getId(), reply));
        if (first) {
            notificationService.notify(review.getBuyer().getId(), NotificationType.MESSAGE,
                    shop.getName() + " đã phản hồi đánh giá của bạn",
                    "\"" + (reply.length() > 80 ? reply.substring(0, 80) + "..." : reply) + "\"",
                    "/products/" + review.getProduct().getId(), review.getProduct().getThumbnailUrl());
        }
        return forSeller(review);
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }

    // the shop sees the buyer's real name and which order the review came from
    private static Map<String, Object> forSeller(Review r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("rating", r.getRating());
        m.put("comment", r.getComment());
        m.put("imageUrls", List.copyOf(r.getImageUrls()));
        m.put("videoUrl", r.getVideoUrl());
        m.put("sellerReply", r.getSellerReply());
        m.put("createdAt", r.getCreatedAt());
        m.put("buyerName", r.getBuyer().getFullName());
        m.put("productId", r.getProduct().getId());
        m.put("productName", r.getProduct().getName());
        m.put("thumbnailUrl", r.getProduct().getThumbnailUrl());
        OrderItem item = r.getOrderItem();
        m.put("variantName", item != null ? item.getVariantNameSnapshot() : null);
        m.put("orderId", item != null ? item.getOrder().getId() : null);
        m.put("orderCode", item != null ? item.getOrder().getCode() : null);
        return m;
    }
}
