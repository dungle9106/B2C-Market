package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.InventoryLogRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ProductVariantRepository;
import com.marketplace.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every stock movement goes through here ("ton kho theo thoi gian thuc"):
 *
 *  - orders take stock and cancellations / returns put it back with single
 *    conditional UPDATE statements, so two buyers can never both get the last
 *    item (no read-modify-write race);
 *  - each movement is written to the InventoryLog ("the kho");
 *  - the seller is notified when a product or variant runs low or sells out.
 *
 * Products with variants keep product.stock = sum of the variants' stock, so
 * both rows move together.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StockService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryLogRepository logRepository;
    private final NotificationService notificationService;

    /** An order line is placed: take qty out of stock or fail with "only N left". */
    public void take(Product p, ProductVariant v, int qty, String orderCode) {
        if (v != null && variantRepository.takeStock(v.getId(), qty) == 0) {
            throw outOfStock(p.getName() + " (" + v.getName() + ")", variantRepository.currentStock(v.getId()));
        }
        if (productRepository.takeStock(p.getId(), qty) == 0) {
            throw outOfStock(p.getName(), productRepository.currentStock(p.getId()));
        }
        int after = v != null ? variantRepository.currentStock(v.getId()) : productRepository.currentStock(p.getId());
        log(p, v != null ? v.getId() : null, v != null ? v.getName() : null, -qty, after, InventoryReason.ORDER, orderCode, null);
        alertIfLow(p, v != null ? v.getName() : null, after + qty, after);
    }

    /** Items come back (cancelled order, approved return). A variant deleted since is skipped. */
    public void putBack(Product p, Long variantId, String variantName, int qty, InventoryReason reason, String orderCode) {
        if (qty <= 0) return;
        if (variantId != null && variantRepository.addStock(variantId, qty) == 0) {
            return; // the variant no longer exists: product.stock is the sum of the remaining ones
        }
        productRepository.putBackStock(p.getId(), qty);
        int after = variantId != null ? variantRepository.currentStock(variantId) : productRepository.currentStock(p.getId());
        log(p, variantId, variantName, qty, after, reason, orderCode, null);
    }

    /**
     * Seller's adjustment ("Nhap hang", "Kiem kho", "Hang hong"...). mode: ADD | SUBTRACT | SET.
     * Returns the new stock of the product (or of the variant).
     */
    public int adjust(Product p, ProductVariant v, String mode, int qty, InventoryReason reason, String note) {
        if (qty < 0) throw bad("Số lượng không hợp lệ");
        int current = v != null ? variantRepository.currentStock(v.getId()) : productRepository.currentStock(p.getId());
        int delta = switch (mode == null ? "" : mode) {
            case "ADD" -> qty;
            case "SUBTRACT" -> -qty;
            case "SET" -> qty - current;
            default -> throw bad("Kiểu điều chỉnh không hợp lệ");
        };
        if (delta == 0) return current;
        if (v != null && variantRepository.addStock(v.getId(), delta) == 0) throw bad("Kho chỉ còn " + current + ", không đủ để xuất");
        if (productRepository.addStock(p.getId(), delta) == 0) throw bad("Kho chỉ còn " + current + ", không đủ để xuất");
        int after = current + delta;
        log(p, v != null ? v.getId() : null, v != null ? v.getName() : null, delta, after, reason, null, note);
        alertIfLow(p, v != null ? v.getName() : null, current, after);
        return after;
    }

    // ---- entity-level edits (product form, quick edit, mark sold out, import) ----

    /** Stock per product ("p") or per variant ("v<id>"), taken before an edit. */
    public static Map<String, Integer> snapshot(Product p) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (p.getVariants().isEmpty()) m.put("p", p.getStock());
        else p.getVariants().forEach(v -> m.put("v" + v.getId(), v.getStock()));
        return m;
    }

    /** Logs what an edit changed compared to a snapshot (variants must have ids, i.e. be flushed). */
    public void recordChanges(Product p, Map<String, Integer> before, InventoryReason reason, String note) {
        Map<String, String> names = new HashMap<>();
        Map<String, Integer> after = new LinkedHashMap<>();
        if (p.getVariants().isEmpty()) {
            after.put("p", p.getStock());
        } else {
            for (ProductVariant v : p.getVariants()) {
                after.put("v" + v.getId(), v.getStock());
                names.put("v" + v.getId(), v.getName());
            }
        }
        for (var e : after.entrySet()) {
            int change = e.getValue() - before.getOrDefault(e.getKey(), 0);
            if (change != 0) {
                log(p, variantId(e.getKey()), names.get(e.getKey()), change, e.getValue(), reason, null, note);
            }
        }
        // variants that were removed take their stock with them
        for (var e : before.entrySet()) {
            if (!after.containsKey(e.getKey()) && e.getValue() != 0) {
                log(p, variantId(e.getKey()), null, -e.getValue(), 0, reason, null, "Xóa phân loại / bỏ phân loại");
            }
        }
    }

    private static Long variantId(String key) {
        return key.startsWith("v") ? Long.valueOf(key.substring(1)) : null;
    }

    // ---- helpers ----

    private void log(Product p, Long variantId, String variantName, int change, int stockAfter,
                     InventoryReason reason, String orderCode, String note) {
        logRepository.save(InventoryLog.builder()
                .shopId(p.getShop().getId())
                .productId(p.getId())
                .productName(p.getName())
                .variantId(variantId)
                .variantName(variantName)
                .change(change)
                .stockAfter(stockAfter)
                .reason(reason)
                .orderCode(orderCode)
                .note(note == null || note.isBlank() ? null : NotificationService.cut(note.trim(), 300))
                .createdBy(currentUserId())
                .build());
    }

    // tell the seller when something crosses into "low" or "sold out" (not on every sale)
    private void alertIfLow(Product p, String variantName, int before, int after) {
        try {
            String what = "\"" + p.getName() + "\"" + (variantName != null ? " - " + variantName : "");
            Long sellerId = p.getShop().getSeller().getId();
            String link = "/seller/inventory?productId=" + p.getId();
            if (before > 0 && after <= 0) {
                notificationService.notify(sellerId, NotificationType.SYSTEM, "Sản phẩm đã hết hàng",
                        what + " đã hết hàng. Nhập thêm hàng để không bỏ lỡ đơn.", link, p.getThumbnailUrl());
            } else if (before > ProductResponse.LOW_STOCK && after <= ProductResponse.LOW_STOCK) {
                notificationService.notify(sellerId, NotificationType.SYSTEM, "Sản phẩm sắp hết hàng",
                        what + " chỉ còn " + after + " sản phẩm.", link, p.getThumbnailUrl());
            }
        } catch (Exception e) {
            log.warn("Could not send stock alert for product {}: {}", p.getId(), e.getMessage());
        }
    }

    // scheduled jobs (unpaid orders expiring) run without a logged-in user
    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal u ? u.getId() : null;
    }

    private static ApiException outOfStock(String what, Integer left) {
        int n = left == null ? 0 : left;
        return new ApiException(n <= 0 ? "Sản phẩm \"" + what + "\" vừa hết hàng"
                : "Sản phẩm \"" + what + "\" chỉ còn " + n + " sản phẩm", HttpStatus.BAD_REQUEST);
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
