package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Yeu thich" (a heart on a product) and "Theo doi shop" (following a shop). Counts are public; whether the viewer
 * liked / follows is filled in when someone is logged in. Followers can use the shop's follower-only vouchers.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SocialService {

    private final ProductLikeRepository likeRepository;
    private final ShopFollowRepository followRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final VoucherRepository voucherRepository;

    // ==== likes ====

    @Transactional(readOnly = true)
    public Map<String, Object> productLikes(Long productId, Long viewerId) {
        return Map.of("count", likeRepository.countByProductId(productId),
                "liked", viewerId != null && likeRepository.existsByUserIdAndProductId(viewerId, productId));
    }

    public Map<String, Object> like(Long productId) {
        Long me = CurrentUser.id();
        Product p = productRepository.findById(productId)
                .filter(x -> x.isActive() && x.getShop().isActive())
                .orElseThrow(() -> new ApiException("Sản phẩm không tồn tại hoặc đã ngừng bán", HttpStatus.NOT_FOUND));
        if (!likeRepository.existsByUserIdAndProductId(me, productId)) {
            try {
                likeRepository.saveAndFlush(ProductLike.builder().user(userRepository.getReferenceById(me)).product(p).build());
            } catch (DataIntegrityViolationException ignored) {
                // a double click: already liked
            }
        }
        return productLikes(productId, me);
    }

    public Map<String, Object> unlike(Long productId) {
        Long me = CurrentUser.id();
        likeRepository.deleteByUserAndProduct(me, productId);
        return productLikes(productId, me);
    }

    /** "San pham yeu thich": still-for-sale products first, then the ones no longer sold (marked). */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> myLikes(int page, int size) {
        var likes = likeRepository.findMine(CurrentUser.id(), PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 60)));
        return PageResponse.of(likes, l -> {
            ProductResponse r = ProductResponse.from(l.getProduct());
            r.setAvailable(l.getProduct().isActive() && l.getProduct().getShop().isActive());
            return r;
        });
    }

    // ==== follows ====

    @Transactional(readOnly = true)
    public Map<String, Object> shopFollowers(Long shopId, Long viewerId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", followRepository.countByShopId(shopId));
        m.put("following", viewerId != null && followRepository.existsByUserIdAndShopId(viewerId, shopId));
        // a nudge on the shop page: "Theo doi shop de nhan voucher"
        m.put("followerVouchers", voucherRepository.runningPublicOfShop(shopId, java.time.LocalDateTime.now()).stream()
                .filter(v -> v.getAudience() == VoucherAudience.FOLLOWERS && !v.isUsedUp()).count());
        return m;
    }

    public Map<String, Object> follow(Long shopId) {
        Long me = CurrentUser.id();
        Shop shop = shopRepository.findById(shopId)
                .filter(Shop::isActive)
                .orElseThrow(() -> new ApiException("Shop không tồn tại", HttpStatus.NOT_FOUND));
        if (shop.getSeller() != null && shop.getSeller().getId().equals(me)) {
            throw new ApiException("Bạn không thể theo dõi shop của chính mình", HttpStatus.BAD_REQUEST);
        }
        if (!followRepository.existsByUserIdAndShopId(me, shopId)) {
            try {
                followRepository.saveAndFlush(ShopFollow.builder().user(userRepository.getReferenceById(me)).shop(shop).build());
            } catch (DataIntegrityViolationException ignored) {
                // already following
            }
        }
        return shopFollowers(shopId, me);
    }

    public Map<String, Object> unfollow(Long shopId) {
        Long me = CurrentUser.id();
        followRepository.deleteByUserAndShop(me, shopId);
        return shopFollowers(shopId, me);
    }

    /** "Shop dang theo doi". */
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> myFollows(int page, int size) {
        var follows = followRepository.findMine(CurrentUser.id(), PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 60)));
        return PageResponse.of(follows, f -> {
            Shop s = f.getShop();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("logoUrl", s.getLogoUrl());
            m.put("province", s.getProvince());
            m.put("active", s.isActive());
            m.put("followers", followRepository.countByShopId(s.getId()));
            m.put("productCount", productRepository.countByShopIdAndActiveTrue(s.getId()));
            m.put("followedAt", f.getCreatedAt());
            return m;
        });
    }

    @Transactional(readOnly = true)
    public boolean follows(Long userId, Long shopId) {
        return userId != null && followRepository.existsByUserIdAndShopId(userId, shopId);
    }

    @Transactional(readOnly = true)
    public List<Long> followerIds(Long shopId) {
        return followRepository.followerIds(shopId);
    }
}
