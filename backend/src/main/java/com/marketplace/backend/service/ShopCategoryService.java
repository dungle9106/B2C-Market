package com.marketplace.backend.service;

import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.entity.ShopCategory;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ShopCategoryRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * "Danh muc cua Shop": categories a seller defines for their own products
 * (e.g. "Hàng mới về", "Áo khoác", "Sale cuối mùa"). A product can be in several.
 * They show as tabs on the public shop page.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ShopCategoryService {

    public static final int MAX_CATEGORIES = 30;

    private final ShopCategoryRepository categoryRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;

    // ==== seller ====

    /** [{ id, name, position, productCount }] in display order. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine() {
        return list(myShop().getId(), false);
    }

    public Map<String, Object> create(String name) {
        Shop shop = myShop();
        String n = validName(name);
        if (categoryRepository.countByShopId(shop.getId()) >= MAX_CATEGORIES) {
            throw bad("Shop chỉ được tạo tối đa " + MAX_CATEGORIES + " danh mục");
        }
        if (categoryRepository.existsByShopIdAndNameIgnoreCase(shop.getId(), n)) throw bad("Danh mục \"" + n + "\" đã tồn tại");
        int position = categoryRepository.findByShopIdOrderByPositionAscIdAsc(shop.getId()).stream()
                .mapToInt(ShopCategory::getPosition).max().orElse(-1) + 1;
        ShopCategory c = categoryRepository.save(ShopCategory.builder().shop(shop).name(n).position(position).build());
        return view(c, 0);
    }

    public Map<String, Object> rename(Long id, String name) {
        ShopCategory c = mineById(id);
        String n = validName(name);
        if (categoryRepository.existsByShopIdAndNameIgnoreCaseAndIdNot(c.getShop().getId(), n, id)) {
            throw bad("Danh mục \"" + n + "\" đã tồn tại");
        }
        c.setName(n);
        return view(c, null);
    }

    /** Products stay; they just leave this category. */
    public void delete(Long id) {
        ShopCategory c = mineById(id);
        categoryRepository.detachProducts(c.getId());
        categoryRepository.delete(c);
    }

    /** ids in the new display order (must be exactly the shop's categories). */
    public List<Map<String, Object>> reorder(List<Long> ids) {
        Shop shop = myShop();
        List<ShopCategory> all = categoryRepository.findByShopIdOrderByPositionAscIdAsc(shop.getId());
        if (ids == null || ids.size() != all.size() || !new HashSet<>(ids).equals(all.stream().map(ShopCategory::getId).collect(Collectors.toSet()))) {
            throw bad("Danh sách sắp xếp không hợp lệ, vui lòng tải lại trang");
        }
        Map<Long, ShopCategory> byId = new HashMap<>();
        all.forEach(c -> byId.put(c.getId(), c));
        for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).setPosition(i);
        return list(shop.getId(), false);
    }

    /** Adds (add=true) or removes the given products of the shop to/from a category. */
    public Map<String, Object> assign(Long id, List<Long> productIds, boolean add) {
        ShopCategory c = mineById(id);
        if (productIds == null || productIds.isEmpty()) throw bad("Vui lòng chọn sản phẩm");
        List<Product> products = productRepository.findByShopIdAndIdIn(c.getShop().getId(), productIds);
        for (Product p : products) {
            if (add) p.getShopCategories().add(c);
            else p.getShopCategories().remove(c);
        }
        return Map.of("updated", products.size());
    }

    // ==== public (shop page tabs) ====

    @Transactional(readOnly = true)
    public List<Map<String, Object>> forShop(Long shopId) {
        // empty categories would be dead tabs for buyers
        return list(shopId, true).stream().filter(m -> ((Number) m.get("productCount")).longValue() > 0).toList();
    }

    // ==== helpers ====

    private List<Map<String, Object>> list(Long shopId, boolean activeOnly) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : categoryRepository.productCounts(shopId, activeOnly)) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return categoryRepository.findByShopIdOrderByPositionAscIdAsc(shopId).stream()
                .map(c -> view(c, counts.getOrDefault(c.getId(), 0L))).toList();
    }

    private static Map<String, Object> view(ShopCategory c, Number productCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("position", c.getPosition());
        if (productCount != null) m.put("productCount", productCount);
        return m;
    }

    private static String validName(String name) {
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) throw bad("Vui lòng nhập tên danh mục");
        if (n.length() > 60) throw bad("Tên danh mục tối đa 60 ký tự");
        return n;
    }

    private ShopCategory mineById(Long id) {
        Long shopId = myShop().getId();
        return categoryRepository.findById(id)
                .filter(c -> c.getShop().getId().equals(shopId))
                .orElseThrow(() -> new ApiException("Danh mục không tồn tại", HttpStatus.NOT_FOUND));
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
