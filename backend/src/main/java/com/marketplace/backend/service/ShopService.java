package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.ShopPolicyRequest;
import com.marketplace.backend.dto.request.ShopRequest;
import com.marketplace.backend.dto.response.AuthResponse;
import com.marketplace.backend.dto.response.ShopPublicResponse;
import com.marketplace.backend.dto.response.ShopResponse;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.security.JwtService;
import com.marketplace.backend.util.Provinces;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// class-level: open-in-view is off, so mapping lazy associations to DTOs
// must happen inside the transaction
@Service
@Transactional
@RequiredArgsConstructor
public class ShopService {

    private static final Set<Integer> RETURN_DAY_OPTIONS = Set.of(0, 3, 7, 15, 30);

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final JwtService jwtService;
    private final ChatService chatService;
    private final AdminShopService adminShopService;

    public ShopResponse getMyShop() {
        return ShopResponse.from(myShop(HttpStatus.NOT_FOUND));
    }

    public ShopResponse createOrUpdate(ShopRequest req) {
        Long sellerId = CurrentUser.id();
        Shop shop = shopRepository.findBySellerId(sellerId).orElseGet(() -> {
            User seller = userRepository.findById(sellerId)
                    .orElseThrow(() -> new ApiException("Người dùng không tồn tại", HttpStatus.NOT_FOUND));
            return Shop.builder().seller(seller).active(true).build();
        });
        applyProfile(shop, req);
        return ShopResponse.from(shopRepository.save(shop));
    }

    /**
     * Shopee-style "Tro thanh nguoi ban": a logged-in BUYER applies to open a
     * shop. This upgrades their account to SELLER and creates the Shop in one
     * step, then re-issues a JWT carrying the new role (the old BUYER token
     * stays valid until it expires, but the frontend swaps in this new one
     * immediately so seller-only pages unlock without a fresh login).
     */
    public AuthResponse becomeSeller(ShopRequest req) {
        Long userId = CurrentUser.id();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("Người dùng không tồn tại", HttpStatus.NOT_FOUND));

        if (user.getRole() == Role.SELLER) {
            throw new ApiException("Bạn đã là người bán rồi", HttpStatus.BAD_REQUEST);
        }
        if (user.getRole() == Role.ADMIN) {
            throw new ApiException("Tài khoản Admin không thể đăng ký bán hàng", HttpStatus.FORBIDDEN);
        }
        if (shopRepository.findBySellerId(userId).isPresent()) {
            throw new ApiException("Bạn đã có shop rồi", HttpStatus.BAD_REQUEST);
        }
        if (isBlank(req.getPickupPhone())) {
            throw bad("Vui lòng nhập số điện thoại lấy hàng");
        }

        Shop shop = Shop.builder().seller(user).active(true).build();
        applyProfile(shop, req);
        // approved at once, or waiting for an admin when the platform requires it
        adminShopService.onNewShop(shop);
        shopRepository.save(shop);

        user.setRole(Role.SELLER);
        userRepository.save(user);

        // same signed-in session, new role in the token
        String token = jwtService.generateToken(user.getEmail(), user.getId(), user.getRole().name(),
                com.marketplace.backend.security.CurrentUser.sessionId());
        AuthResponse a = new AuthResponse(token, user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
        a.setPhone(user.getPhone());
        return a;
    }

    private void applyProfile(Shop shop, ShopRequest req) {
        String name = req.getName().trim().replaceAll("\\s+", " ");
        // only checked when the name changes: shops created before names had to be unique keep theirs
        boolean taken = shop.getId() == null
                ? shopRepository.existsByNameIgnoreCase(name)
                : !name.equalsIgnoreCase(shop.getName()) && shopRepository.existsByNameIgnoreCaseAndIdNot(name, shop.getId());
        if (taken) throw bad("Tên shop \"" + name + "\" đã được sử dụng, vui lòng chọn tên khác");
        if (isBlank(req.getPickupAddress())) throw bad("Vui lòng nhập địa chỉ lấy hàng");

        shop.setName(name);
        shop.setDescription(blankToNull(req.getDescription()));
        shop.setLogoUrl(imageUrl(req.getLogoUrl(), "Logo"));
        shop.setCoverUrl(imageUrl(req.getCoverUrl(), "Ảnh bìa"));
        shop.setPickupAddress(req.getPickupAddress().trim());
        // the field is optional for shops created before it existed: keep the old value when left out
        if (req.getPickupPhone() != null) shop.setPickupPhone(blankToNull(req.getPickupPhone()));
        if (req.getPickupDistrict() != null) shop.setPickupDistrict(blankToNull(req.getPickupDistrict()));
        if (req.getPickupWard() != null) shop.setPickupWard(blankToNull(req.getPickupWard()));
        shop.setProvince(validProvince(req.getProvince()));
    }

    // ==== policies ====

    public ShopResponse updatePolicies(ShopPolicyRequest req) {
        Shop shop = myShop(HttpStatus.BAD_REQUEST);
        if (!RETURN_DAY_OPTIONS.contains(req.getDefaultReturnDays())) {
            throw bad("Thời hạn đổi trả chỉ có thể là 0, 3, 7, 15 hoặc 30 ngày");
        }
        List<String> carriers = req.getCarriers().stream().distinct().toList();
        if (carriers.stream().anyMatch(c -> !ShippingService.isCarrier(c))) throw bad("Đơn vị vận chuyển không hợp lệ");
        BigDecimal threshold = req.getFreeShipMinOrder();
        if (threshold != null && threshold.signum() > 0 && threshold.compareTo(BigDecimal.valueOf(10_000)) < 0) {
            throw bad("Giá trị đơn tối thiểu để miễn phí vận chuyển phải từ ₫10.000");
        }

        shop.setDefaultReturnDays(req.getDefaultReturnDays());
        shop.setReturnConditions(blankToNull(req.getReturnConditions()));
        // all carriers = null, so carriers added to the platform later are on by default
        shop.setCarriers(carriers.size() == ShippingService.CARRIERS.size() ? null : String.join(",", carriers));
        shop.setHandlingDays(req.getHandlingDays());
        shop.setFreeShipMinOrder(threshold == null || threshold.signum() == 0 ? null : threshold);
        if (req.isApplyReturnDaysToAllProducts()) {
            productRepository.updateReturnDaysOfShop(shop.getId(), req.getDefaultReturnDays());
        }
        return ShopResponse.from(shop);
    }

    /** "Gui duyet lai" after a rejection: the seller has fixed the profile. */
    public ShopResponse resubmit() {
        Shop shop = myShop(HttpStatus.NOT_FOUND);
        adminShopService.resubmit(shop);
        return ShopResponse.from(shop);
    }

    // ==== public ====

    @Transactional(readOnly = true)
    public ShopPublicResponse publicShop(Long id) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ApiException("Shop không tồn tại", HttpStatus.NOT_FOUND));
        Object[] rating = orderRepository.shopRating(id).stream().findFirst().orElse(new Object[]{null, 0L});
        ShopPublicResponse r = ShopPublicResponse.from(shop, productRepository.countByShopIdAndActiveTrue(id),
                rating[0] != null ? ((Number) rating[0]).doubleValue() : null,
                rating[1] != null ? ((Number) rating[1]).longValue() : 0);
        Map<String, Object> chat = chatService.responseStats(id);
        r.setChatResponseRate((Double) chat.get("responseRate"));
        r.setChatResponseTime((String) chat.get("responseTime"));
        return r;
    }

    /** Carriers a shop can enable (policy form). */
    public static List<Map<String, Object>> carrierOptions() {
        return ShippingService.CARRIERS.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", c.code());
            m.put("name", c.name());
            m.put("methods", c.methods().stream().map(ShippingService::methodName).sorted().toList());
            return m;
        }).toList();
    }

    // ==== helpers ====

    private Shop myShop(HttpStatus missing) {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", missing));
    }

    // our own uploads, or an external https image (the settings form allows pasting a link)
    private static String imageUrl(String url, String what) {
        if (isBlank(url)) return null;
        String u = url.trim();
        if (!u.startsWith("/uploads/") && !u.startsWith("https://") && !u.startsWith("http://")) {
            throw bad(what + " không hợp lệ");
        }
        return u;
    }

    private static String validProvince(String province) {
        if (!Provinces.isValid(province)) {
            throw bad("Vui lòng chọn tỉnh/thành phố của shop");
        }
        return province;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
