package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.ProductRequest;
import com.marketplace.backend.dto.request.ProductSearchCriteria;
import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.dto.response.SuggestResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.spec.ProductSpecifications;
import com.marketplace.backend.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {

    private static final int MAX_PAGE_SIZE = 60;

    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final ViewHistoryRepository viewHistoryRepository;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductQuestionRepository questionRepository;
    private final OrderRepository orderRepository;
    private final ShopCategoryRepository shopCategoryRepository;
    private final StockService stockService;
    private final ProductModerationService moderationService;
    private final ProductReportRepository reportRepository;

    // ==== Buyer-facing browsing ====

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(ProductSearchCriteria c) {
        Set<Long> categoryIds = c.getCategoryId() != null ? categoryService.withDescendants(c.getCategoryId()) : null;
        int size = Math.min(Math.max(c.getSize(), 1), MAX_PAGE_SIZE);
        var page = productRepository.findAll(
                ProductSpecifications.search(c, categoryIds),
                PageRequest.of(Math.max(c.getPage(), 0), size));
        return PageResponse.of(page, ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public SuggestResponse suggest(String q) {
        String keyword = TextNormalizer.normalize(q);
        if (keyword == null || keyword.isEmpty()) return new SuggestResponse(List.of(), List.of());
        String escaped = ProductSpecifications.escapeLike(keyword);

        // names starting with what was typed first, then names containing it
        Set<String> names = new LinkedHashSet<>(productRepository.suggestNames(escaped + "%", PageRequest.of(0, 8)));
        if (names.size() < 8) {
            names.addAll(productRepository.suggestNames("%" + escaped + "%", PageRequest.of(0, 16)));
        }
        return new SuggestResponse(names.stream().limit(8).toList(), categoryService.search(keyword, 3));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> flashSales(int limit) {
        return productRepository.findActiveFlashSales(LocalDateTime.now(), PageRequest.of(0, Math.min(limit, 30)))
                .stream().map(ProductResponse::from).toList();
    }

    public ProductResponse getDetail(Long id, Long viewerUserId) {
        Product product = productRepository.findById(id)
                .filter(p -> p.isActive() && p.getShop().isActive())
                .orElseThrow(() -> new ApiException("Sản phẩm không tồn tại hoặc đã ngừng bán", HttpStatus.NOT_FOUND));
        // log view history for the recommendation feature (best-effort, ignore if not logged in)
        if (viewerUserId != null) {
            userRepository.findById(viewerUserId).ifPresent(u ->
                    viewHistoryRepository.save(ViewHistory.builder().user(u).product(product).build()));
        }
        ProductResponse r = ProductResponse.detail(product);
        List<Object[]> rating = orderRepository.shopRating(product.getShop().getId());
        if (!rating.isEmpty() && rating.get(0)[0] != null) {
            r.setShopRating(Math.round(((Number) rating.get(0)[0]).doubleValue() * 10) / 10.0);
            r.setShopRatingCount(((Number) rating.get(0)[1]).longValue());
        }
        return r;
    }

    // rule-based recommendation: same category products, best sellers first
    @Transactional(readOnly = true)
    public List<ProductResponse> recommendSimilar(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApiException("Sản phẩm không tồn tại", HttpStatus.NOT_FOUND));
        if (product.getCategory() == null) return List.of();
        return productRepository.findSimilarByCategory(product.getCategory().getId(), productId)
                .stream().limit(12).map(ProductResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    // the home page's best sellers: a few minutes old is fine
    @org.springframework.cache.annotation.Cacheable(com.marketplace.backend.config.CacheConfig.TOP_SELLING)
    public List<ProductResponse> topSelling() {
        return productRepository.findTop10ByActiveTrueOrderBySoldCountDesc().stream()
                .map(ProductResponse::from).collect(Collectors.toList());
    }

    // ==== Seller-only operations ====

    @Transactional(readOnly = true)
    public List<ProductResponse> myProducts() {
        Shop shop = requireOwnShop();
        return productRepository.findByShopIdOrderByCreatedAtDesc(shop.getId()).stream()
                .map(ProductResponse::forSeller).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse myProduct(Long productId) {
        return ProductResponse.forSeller(ownedProductOrThrow(productId));
    }

    public ProductResponse create(ProductRequest req) {
        Product product = Product.builder().shop(requireOwnShop()).active(true).build();
        apply(product, req);
        moderationService.onSellerSave(product, true);
        Product saved = productRepository.saveAndFlush(product);
        stockService.recordChanges(saved, Map.of(), InventoryReason.CREATE, null);
        return ProductResponse.forSeller(saved);
    }

    public ProductResponse update(Long productId, ProductRequest req) {
        Product product = ownedProductOrThrow(productId);
        Map<String, Integer> before = StockService.snapshot(product);
        apply(product, req);
        moderationService.onSellerSave(product, false);
        Product saved = productRepository.saveAndFlush(product); // new variants get their ids for the log
        stockService.recordChanges(saved, before, InventoryReason.EDIT, "Sửa sản phẩm");
        return ProductResponse.forSeller(saved);
    }

    /** "Gui duyet lai": a rejected / removed product the seller has fixed. */
    public ProductResponse resubmit(Long productId) {
        Product product = ownedProductOrThrow(productId);
        moderationService.resubmit(product);
        return ProductResponse.forSeller(productRepository.save(product));
    }

    public void toggleActive(Long productId) {
        Product product = ownedProductOrThrow(productId);
        if (!product.isActive()) moderationService.ensureCanShow(product);
        product.setActive(!product.isActive());
        productRepository.save(product);
    }

    public void delete(Long productId) {
        Product product = ownedProductOrThrow(productId);
        // order history keeps a reference to the product, so sold products can only be hidden
        if (orderItemRepository.existsByProductId(productId)) {
            throw new ApiException("Sản phẩm đã có đơn hàng nên không thể xóa, hãy chọn \"Ẩn\" thay thế", HttpStatus.BAD_REQUEST);
        }
        deleteNow(product);
    }

    private void deleteNow(Product product) {
        reportRepository.deleteByProductId(product.getId());
        cartItemRepository.deleteByProductId(product.getId());
        viewHistoryRepository.deleteByProductId(product.getId());
        questionRepository.deleteByProductId(product.getId());
        productRepository.delete(product);
    }

    // ==== bulk actions, quick stock edit, duplicate ====

    public enum BulkAction { HIDE, SHOW, SOLD_OUT, DELETE, ADD_CATEGORY, REMOVE_CATEGORY }

    /**
     * One action on many products. Products that can't take it (a sold product can't be
     * deleted) are skipped and reported instead of failing the whole batch.
     * Returns { updated, skipped: [{ id, name, reason }] }.
     */
    public Map<String, Object> bulk(List<Long> ids, BulkAction action, Long shopCategoryId) {
        if (ids == null || ids.isEmpty()) throw bad("Vui lòng chọn sản phẩm");
        if (action == null) throw bad("Thao tác không hợp lệ");
        Shop shop = requireOwnShop();
        List<Product> products = productRepository.findByShopIdAndIdIn(shop.getId(), new HashSet<>(ids));
        ShopCategory category = null;
        if (action == BulkAction.ADD_CATEGORY || action == BulkAction.REMOVE_CATEGORY) {
            if (shopCategoryId == null) throw bad("Vui lòng chọn danh mục");
            category = ownShopCategories(shop, List.of(shopCategoryId)).iterator().next();
        }

        int updated = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (Product p : products) {
            switch (action) {
                case HIDE -> p.setActive(false);
                case SHOW -> {
                    if (p.getModerationStatus() != ProductModeration.APPROVED) {
                        skipped.add(Map.of("id", p.getId(), "name", p.getName(), "reason", "Chưa được duyệt hoặc đã bị gỡ"));
                        continue;
                    }
                    p.setActive(true);
                }
                case SOLD_OUT -> markSoldOut(p);
                case ADD_CATEGORY -> p.getShopCategories().add(category);
                case REMOVE_CATEGORY -> p.getShopCategories().remove(category);
                case DELETE -> {
                    if (orderItemRepository.existsByProductId(p.getId())) {
                        skipped.add(Map.of("id", p.getId(), "name", p.getName(), "reason", "Đã có đơn hàng, chỉ có thể ẩn"));
                        continue;
                    }
                    deleteNow(p);
                }
            }
            updated++;
        }
        return Map.of("updated", updated, "skipped", skipped);
    }

    /** "Danh dau het hang": stock 0 for the product and every variant; it stays listed as sold out. */
    private void markSoldOut(Product p) {
        Map<String, Integer> before = StockService.snapshot(p);
        p.getVariants().forEach(v -> v.setStock(0));
        p.setStock(0);
        stockService.recordChanges(p, before, InventoryReason.SOLD_OUT, null);
    }

    /** Quick edit from the product list: the product's stock, or each variant's (by id). */
    public ProductResponse updateStock(Long productId, Integer stock, Map<Long, Integer> variantStock) {
        Product p = ownedProductOrThrow(productId);
        Map<String, Integer> before = StockService.snapshot(p);
        if (p.getVariants().isEmpty()) {
            if (stock == null || stock < 0) throw bad("Số lượng kho không hợp lệ");
            p.setStock(stock);
        } else {
            if (variantStock == null || variantStock.isEmpty()) throw bad("Vui lòng nhập kho cho phân loại");
            for (ProductVariant v : p.getVariants()) {
                Integer s = variantStock.get(v.getId());
                if (s == null) continue;
                if (s < 0) throw bad("Số lượng kho không hợp lệ");
                v.setStock(s);
            }
            p.setStock(p.getVariants().stream().mapToInt(ProductVariant::getStock).sum());
        }
        stockService.recordChanges(p, before, InventoryReason.EDIT, "Sửa nhanh trong danh sách");
        return ProductResponse.forSeller(p);
    }

    /** "Sao chep": a hidden copy to edit into a similar product (no SKU, sales or reviews carried over). */
    public ProductResponse duplicate(Long productId) {
        Product src = ownedProductOrThrow(productId);
        String name = ("Bản sao - " + src.getName());
        Product copy = Product.builder()
                .shop(src.getShop())
                .category(src.getCategory())
                .name(name.length() > 120 ? name.substring(0, 120) : name)
                .description(src.getDescription())
                .price(src.getPrice())
                .originalPrice(src.getOriginalPrice())
                .stock(src.getStock())
                .thumbnailUrl(src.getThumbnailUrl())
                .imageUrls(new ArrayList<>(src.getImageUrls()))
                .videoUrl(src.getVideoUrl())
                .freeShipping(src.isFreeShipping())
                .returnDays(src.getReturnDays())
                .tier1Name(src.getTier1Name())
                .tier2Name(src.getTier2Name())
                .shopCategories(new HashSet<>(src.getShopCategories()))
                .active(false)
                .build();
        for (ProductVariant v : src.getVariants()) {
            copy.getVariants().add(ProductVariant.builder()
                    .product(copy).name(v.getName()).option1(v.getOption1()).option2(v.getOption2())
                    .imageUrl(v.getImageUrl()).extraPrice(v.getExtraPrice()).stock(v.getStock())
                    .build());
        }
        moderationService.onSellerSave(copy, true);
        copy.setActive(false); // the copy starts hidden whatever its moderation result
        Product saved = productRepository.saveAndFlush(copy);
        stockService.recordChanges(saved, Map.of(), InventoryReason.CREATE, "Sao chép từ " + src.getName());
        return ProductResponse.forSeller(saved);
    }

    // ==== bulk import (rows already parsed by ProductExcelService) ====

    /**
     * One product to create from the spreadsheet; firstRow is the Excel row number (1-based).
     * flagged: the row already has reading errors; it is still checked here so the seller sees
     * every problem in one pass, but it is never counted as valid.
     */
    public record ImportDraft(int firstRow, ProductRequest request, boolean flagged) {}

    /**
     * Runs every draft through the same rules as the product form (apply). All or nothing:
     * products are only saved when dryRun is false and no draft has an error.
     * apply() is called on this instance (not through the Spring proxy), so a rule
     * violation in one draft is collected without marking the transaction rollback-only.
     */
    public Map<String, Object> importDrafts(List<ImportDraft> drafts, List<Map<String, Object>> errors, boolean dryRun) {
        Shop shop = requireOwnShop();
        List<Product> built = new ArrayList<>();
        List<Map<String, Object>> preview = new ArrayList<>();
        for (ImportDraft d : drafts) {
            Product p = Product.builder().shop(shop).active(true).build();
            try {
                apply(p, d.request());
            } catch (ApiException e) {
                errors.add(Map.of("row", d.firstRow(), "message", e.getMessage()));
                continue;
            }
            if (d.flagged()) continue;
            built.add(p);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("row", d.firstRow());
            m.put("sku", p.getSku());
            m.put("name", p.getName());
            m.put("price", p.getPrice());
            m.put("stock", p.getStock());
            m.put("variantCount", p.getVariants().size());
            m.put("categoryName", p.getCategory() != null ? p.getCategory().getName() : null);
            preview.add(m);
        }
        errors.sort(Comparator.comparingInt(e -> (Integer) e.get("row")));
        boolean create = !dryRun && errors.isEmpty() && !built.isEmpty();
        if (create) {
            built.forEach(p -> moderationService.onSellerSave(p, true));
            productRepository.saveAllAndFlush(built);
            built.forEach(p -> stockService.recordChanges(p, Map.of(), InventoryReason.IMPORT, "Đăng hàng loạt từ Excel"));
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dryRun", dryRun);
        r.put("products", preview);
        r.put("errors", errors);
        r.put("created", create ? built.size() : 0);
        return r;
    }

    // copies the seller's form onto the entity, validating cross-field rules
    // (package-private: the Excel import builds products through the same rules)
    void apply(Product product, ProductRequest req) {
        BigDecimal price = req.getPrice();

        String sku = trimToNull(req.getSku());
        if (sku != null) {
            Long shopId = product.getShop().getId();
            boolean taken = product.getId() == null
                    ? productRepository.existsByShopIdAndSkuIgnoreCase(shopId, sku)
                    : productRepository.existsByShopIdAndSkuIgnoreCaseAndIdNot(shopId, sku, product.getId());
            if (taken) throw bad("SKU \"" + sku + "\" đã được dùng cho sản phẩm khác của shop");
        }

        if (req.getOriginalPrice() != null && req.getOriginalPrice().compareTo(price) <= 0) {
            throw new ApiException("Giá gốc phải lớn hơn giá bán", HttpStatus.BAD_REQUEST);
        }

        boolean anyFlash = req.getFlashSalePrice() != null || req.getFlashSaleStart() != null || req.getFlashSaleEnd() != null;
        if (anyFlash) {
            if (req.getFlashSalePrice() == null || req.getFlashSaleStart() == null || req.getFlashSaleEnd() == null) {
                throw new ApiException("Flash sale cần đủ giá, thời gian bắt đầu và kết thúc", HttpStatus.BAD_REQUEST);
            }
            if (req.getFlashSalePrice().compareTo(price) >= 0) {
                throw new ApiException("Giá flash sale phải thấp hơn giá bán", HttpStatus.BAD_REQUEST);
            }
            if (!req.getFlashSaleEnd().isAfter(req.getFlashSaleStart())) {
                throw new ApiException("Thời gian kết thúc flash sale phải sau thời gian bắt đầu", HttpStatus.BAD_REQUEST);
            }
        }

        List<String> images = cleanUrls(req.getImageUrls());
        String video = req.getVideoUrl() == null || req.getVideoUrl().isBlank() ? null : req.getVideoUrl().trim();
        if (video != null) checkUrl(video);

        product.setName(req.getName().trim());
        product.setSku(sku);
        product.setDescription(req.getDescription());
        product.setPrice(price);
        product.setOriginalPrice(req.getOriginalPrice());
        // a different start time is a new flash sale: its quota starts from 0
        if (!anyFlash || !Objects.equals(product.getFlashSaleStart(), req.getFlashSaleStart())) product.setFlashSaleSold(0);
        product.setFlashSalePrice(anyFlash ? req.getFlashSalePrice() : null);
        product.setFlashSaleStart(anyFlash ? req.getFlashSaleStart() : null);
        product.setFlashSaleEnd(anyFlash ? req.getFlashSaleEnd() : null);
        product.setFlashSaleStock(anyFlash ? req.getFlashSaleStock() : null);
        product.setFlashSaleMaxPerBuyer(anyFlash ? req.getFlashSaleMaxPerBuyer() : null);
        product.setFreeShipping(req.isFreeShipping());
        // not sent: the shop's default return window (shop policy)
        Integer shopDays = product.getShop() != null ? product.getShop().getDefaultReturnDays() : null;
        product.setReturnDays(req.getReturnDays() != null ? req.getReturnDays() : shopDays != null ? shopDays : 7);
        product.getImageUrls().clear();
        product.getImageUrls().addAll(images);
        product.setThumbnailUrl(images.isEmpty() ? null : images.get(0));
        product.setVideoUrl(video);
        product.setCategory(req.getCategoryId() != null
                ? categoryRepository.findById(req.getCategoryId())
                    .orElseThrow(() -> new ApiException("Ngành hàng không tồn tại", HttpStatus.BAD_REQUEST))
                : null);

        product.getShopCategories().clear();
        product.getShopCategories().addAll(ownShopCategories(product.getShop(), req.getShopCategoryIds()));

        syncVariants(product, req);
        // with variants, the product's stock is the sum of its variants' stock
        product.setStock(product.getVariants().isEmpty()
                ? (req.getStock() != null ? req.getStock() : 0)
                : product.getVariants().stream().mapToInt(ProductVariant::getStock).sum());
    }

    private Set<ShopCategory> ownShopCategories(Shop shop, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        Set<Long> wanted = new HashSet<>(ids);
        List<ShopCategory> found = shopCategoryRepository.findAllById(wanted);
        if (found.size() != wanted.size() || found.stream().anyMatch(c -> !c.getShop().getId().equals(shop.getId()))) {
            throw bad("Danh mục của shop không hợp lệ");
        }
        return new HashSet<>(found);
    }

    /**
     * Variants come in two shapes:
     *  - tiers (tier1Name "Màu sắc", optional tier2Name "Kích cỡ"): every variant gives option1
     *    (and option2) and its name is derived as "Đỏ / M";
     *  - no tiers: free-text names, as products created before tiers existed.
     * Existing variants are updated in place by id (carts keep pointing at them), new ones are
     * added, the rest dropped.
     */
    private void syncVariants(Product product, ProductRequest req) {
        List<ProductRequest.VariantRequest> requested = req.getVariants();
        String t1 = trimToNull(req.getTier1Name());
        String t2 = trimToNull(req.getTier2Name());
        if (t2 != null && t1 == null) throw bad("Vui lòng đặt tên nhóm phân loại 1 trước nhóm 2");
        if (t1 != null && t1.equalsIgnoreCase(t2)) throw bad("Hai nhóm phân loại phải có tên khác nhau");
        if (t1 != null && requested.isEmpty()) throw bad("Vui lòng thêm giá trị cho nhóm phân loại \"" + t1 + "\"");

        Set<String> names = new HashSet<>();
        Set<String> skus = new HashSet<>();
        for (var v : requested) {
            if (t1 != null) {
                String o1 = trimToNull(v.getOption1());
                String o2 = t2 != null ? trimToNull(v.getOption2()) : null;
                if (o1 == null) throw bad("Vui lòng nhập " + t1.toLowerCase(Locale.ROOT) + " cho mọi phân loại");
                if (t2 != null && o2 == null) throw bad("Vui lòng nhập " + t2.toLowerCase(Locale.ROOT) + " cho mọi phân loại");
                v.setOption1(o1);
                v.setOption2(o2);
                v.setName(o2 != null ? o1 + " / " + o2 : o1);
            } else {
                if (trimToNull(v.getName()) == null) throw bad("Vui lòng nhập tên phân loại");
                v.setOption1(null);
                v.setOption2(null);
            }
            if (!names.add(v.getName().trim().toLowerCase(Locale.ROOT))) {
                throw bad("Phân loại \"" + v.getName().trim() + "\" bị trùng");
            }
            String vSku = trimToNull(v.getSku());
            if (vSku != null && !skus.add(vSku.toLowerCase(Locale.ROOT))) throw bad("SKU phân loại \"" + vSku + "\" bị trùng");
            v.setSku(vSku);
            String img = trimToNull(v.getImageUrl());
            if (img != null) checkUrl(img);
            v.setImageUrl(img);
        }
        product.setTier1Name(requested.isEmpty() ? null : t1);
        product.setTier2Name(requested.isEmpty() ? null : t2);

        Map<Long, ProductVariant> existing = product.getVariants().stream()
                .filter(v -> v.getId() != null)
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Set<Long> kept = requested.stream().map(ProductRequest.VariantRequest::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        List<Long> removed = existing.keySet().stream().filter(id -> !kept.contains(id)).toList();
        if (!removed.isEmpty()) {
            cartItemRepository.deleteByVariantIdIn(removed);
            product.getVariants().removeIf(v -> v.getId() != null && removed.contains(v.getId()));
        }

        for (var v : requested) {
            ProductVariant variant = v.getId() != null ? existing.get(v.getId()) : null;
            if (variant == null) {
                variant = ProductVariant.builder().product(product).build();
                product.getVariants().add(variant);
            }
            variant.setName(v.getName().trim());
            variant.setOption1(v.getOption1());
            variant.setOption2(v.getOption2());
            variant.setSku(v.getSku());
            variant.setImageUrl(v.getImageUrl());
            variant.setExtraPrice(v.getExtraPrice() != null ? v.getExtraPrice() : BigDecimal.ZERO);
            variant.setStock(v.getStock());
        }
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }

    private static List<String> cleanUrls(List<String> urls) {
        if (urls == null) return List.of();
        List<String> cleaned = urls.stream().filter(u -> u != null && !u.isBlank()).map(String::trim).distinct().toList();
        cleaned.forEach(ProductService::checkUrl);
        return cleaned;
    }

    // our own uploads, or a normal web link (never javascript:/data: etc.)
    private static void checkUrl(String url) {
        if (!(url.startsWith("/uploads/") || url.startsWith("https://") || url.startsWith("http://"))) {
            throw new ApiException("Đường dẫn hình ảnh/video không hợp lệ", HttpStatus.BAD_REQUEST);
        }
    }

    private Product ownedProductOrThrow(Long productId) {
        Shop shop = requireOwnShop();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApiException("Sản phẩm không tồn tại", HttpStatus.NOT_FOUND));
        if (!product.getShop().getId().equals(shop.getId())) {
            throw new ApiException("Bạn không có quyền thao tác sản phẩm này", HttpStatus.FORBIDDEN);
        }
        return product;
    }

    private Shop requireOwnShop() {
        Long sellerId = CurrentUser.id();
        return shopRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new ApiException("Bạn chưa tạo shop, vui lòng tạo shop trước", HttpStatus.BAD_REQUEST));
    }
}
