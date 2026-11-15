package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.PriceWatchRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Bao khi giam gia". The buyer presses the bell on a product page; every few minutes the prices of watched products
 * are compared with the last check, and a drop of at least MIN_DROP_PERCENT (seller cut the price, a flash sale began)
 * sends a notification. With a target price set, only a price at or below it is announced.
 * A drop is announced once: the reference moves to the new price; a later rise and a new drop is announced again,
 * but not more than once per COOLDOWN for the same product (flash sales switching on and off would spam otherwise).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceWatchService {

    static final int MAX_WATCHES = 100;
    static final BigDecimal MIN_DROP_PERCENT = new BigDecimal("1");
    static final Duration COOLDOWN = Duration.ofHours(12);
    private static final int BATCH = 500;

    private final PriceWatchRepository watchRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate tx;

    // ==== buyer ====

    @Transactional
    public Map<String, Object> watch(Long productId, BigDecimal targetPrice) {
        Long userId = CurrentUser.id();
        Product p = productRepository.findById(productId)
                .filter(this::onSale)
                .orElseThrow(() -> new ApiException("Sản phẩm không tồn tại hoặc đã ngừng bán", HttpStatus.NOT_FOUND));
        BigDecimal now = p.getEffectivePrice();
        if (targetPrice != null && (targetPrice.signum() <= 0 || targetPrice.compareTo(now) >= 0)) {
            throw new ApiException("Giá mong muốn phải thấp hơn giá hiện tại (" + NotificationService.vnd(now) + ")", HttpStatus.BAD_REQUEST);
        }
        PriceWatch w = watchRepository.findByUserIdAndProductId(userId, productId).orElse(null);
        if (w == null) {
            if (watchRepository.countByUserId(userId) >= MAX_WATCHES) {
                throw new ApiException("Bạn đang theo dõi giá tối đa " + MAX_WATCHES + " sản phẩm. Hãy bỏ bớt trước khi thêm.", HttpStatus.BAD_REQUEST);
            }
            w = PriceWatch.builder().userId(userId).productId(productId)
                    .watchedPrice(now).lastPrice(now).createdAt(LocalDateTime.now()).build();
        }
        w.setTargetPrice(targetPrice);
        watchRepository.save(w);
        return view(w, p);
    }

    @Transactional
    public void unwatch(Long productId) {
        watchRepository.findByUserIdAndProductId(CurrentUser.id(), productId).ifPresent(watchRepository::delete);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(Long productId) {
        Long userId = CurrentUser.id();
        Map<String, Object> m = new LinkedHashMap<>();
        Optional<PriceWatch> w = watchRepository.findByUserIdAndProductId(userId, productId);
        m.put("watching", w.isPresent());
        m.put("targetPrice", w.map(PriceWatch::getTargetPrice).orElse(null));
        m.put("watchedPrice", w.map(PriceWatch::getWatchedPrice).orElse(null));
        m.put("watchers", watchRepository.countByProductId(productId));
        return m;
    }

    /** "San pham dang theo doi gia": biggest drop since watching first, then newest. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine() {
        List<PriceWatch> list = watchRepository.findByUserIdOrderByCreatedAtDesc(CurrentUser.id());
        Map<Long, Product> products = productRepository.findAllById(list.stream().map(PriceWatch::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        return list.stream()
                .filter(w -> products.containsKey(w.getProductId()))
                .map(w -> view(w, products.get(w.getProductId())))
                .sorted(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("dropPercent")).reversed())
                .toList();
    }

    private Map<String, Object> view(PriceWatch w, Product p) {
        BigDecimal now = p.getEffectivePrice();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", p.getId());
        m.put("product", ProductResponse.from(p));
        m.put("onSale", onSale(p));
        m.put("watchedPrice", w.getWatchedPrice());
        m.put("currentPrice", now);
        m.put("targetPrice", w.getTargetPrice());
        m.put("dropPercent", dropPercent(w.getWatchedPrice(), now).max(BigDecimal.ZERO));
        m.put("reachedTarget", w.getTargetPrice() != null && now.compareTo(w.getTargetPrice()) <= 0);
        m.put("notifiedAt", w.getNotifiedAt());
        m.put("createdAt", w.getCreatedAt());
        return m;
    }

    // ==== background check ====

    @Scheduled(fixedDelayString = "${shoppi.price-watch.check-ms:300000}", initialDelay = 60000)
    public void checkAll() {
        long after = 0;
        int sent = 0;
        while (true) {
            final long from = after;
            List<PriceWatch> page = watchRepository.page(from, PageRequest.of(0, BATCH));
            if (page.isEmpty()) break;
            Integer n = tx.execute(s -> checkPage(page));
            sent += n == null ? 0 : n;
            after = page.get(page.size() - 1).getId();
            if (page.size() < BATCH) break;
        }
        if (sent > 0) log.info("price watch: {} price-drop notifications", sent);
    }

    /** one page in one transaction; returns how many notifications went out */
    int checkPage(List<PriceWatch> page) {
        Map<Long, Product> products = productRepository.findAllById(page.stream().map(PriceWatch::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        int sent = 0;
        for (PriceWatch w : page) {
            Product p = products.get(w.getProductId());
            if (p == null) {   // product deleted: nothing left to watch
                watchRepository.delete(w);
                continue;
            }
            if (!onSale(p)) continue;   // hidden / out of stock: keep the reference, look again later
            BigDecimal price = p.getEffectivePrice();
            if (shouldNotify(w, price, now)) {
                notifyDrop(w, p, price);
                w.setNotifiedPrice(price);
                w.setNotifiedAt(now);
                sent++;
            }
            if (price.compareTo(w.getLastPrice()) != 0) w.setLastPrice(price);
            watchRepository.save(w);
        }
        return sent;
    }

    static boolean shouldNotify(PriceWatch w, BigDecimal price, LocalDateTime now) {
        if (dropPercent(w.getLastPrice(), price).compareTo(MIN_DROP_PERCENT) < 0) return false;   // not a real drop
        if (w.getTargetPrice() != null && price.compareTo(w.getTargetPrice()) > 0) return false;  // not low enough yet
        if (w.getNotifiedPrice() != null && price.compareTo(w.getNotifiedPrice()) >= 0
                && w.getNotifiedAt() != null && w.getNotifiedAt().isAfter(now.minus(COOLDOWN))) return false; // told already
        return true;
    }

    private void notifyDrop(PriceWatch w, Product p, BigDecimal price) {
        BigDecimal pct = dropPercent(w.getLastPrice(), price).setScale(0, RoundingMode.HALF_UP);
        String title = w.getTargetPrice() != null && price.compareTo(w.getTargetPrice()) <= 0
                ? "🔔 Đã đạt giá bạn mong muốn!"
                : "🔔 Sản phẩm bạn theo dõi vừa giảm " + pct + "%";
        String content = p.getName() + " giảm từ " + NotificationService.vnd(w.getLastPrice()) + " còn " + NotificationService.vnd(price)
                + (p.isFlashSaleActive() ? " (Flash Sale, số lượng có hạn)" : "") + ". Mua ngay kẻo lỡ!";
        notificationService.notify(w.getUserId(), NotificationType.PROMOTION, title, content, "/products/" + p.getId(), p.getThumbnailUrl());
    }

    private boolean onSale(Product p) {
        return p.isActive() && p.getModerationStatus() == ProductModeration.APPROVED
                && p.getStock() != null && p.getStock() > 0
                && (p.getShop() == null || p.getShop().getStatus() == ShopStatus.APPROVED);
    }

    /** how much lower `now` is than `before`, in percent (negative when it went up) */
    static BigDecimal dropPercent(BigDecimal before, BigDecimal now) {
        if (before == null || now == null || before.signum() <= 0) return BigDecimal.ZERO;
        return before.subtract(now).multiply(BigDecimal.valueOf(100)).divide(before, 1, RoundingMode.HALF_UP);
    }
}
