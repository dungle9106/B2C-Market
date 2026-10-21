package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * "Quan ly tai khoan buyer & seller": search, view, lock (with a reason, optionally for N days), unlock, delete.
 * Deleting anonymises the account (email, name, phone) instead of removing the row, so orders, reviews and
 * the shop's history stay consistent. A locked/deleted seller's shop is hidden from buyers.
 * Only the super admin may lock or delete another admin; nobody can do it to themselves.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AdminUserService {

    private static final Set<OrderStatus> OPEN = EnumSet.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.PENDING, OrderStatus.CONFIRMED,
            OrderStatus.SHIPPING, OrderStatus.RETURN_REQUESTED);
    private static final Set<OrderStatus> NOT_A_SALE = EnumSet.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED, OrderStatus.AWAITING_PAYMENT);

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final AdminAuditService auditService;
    private final AdminAuditLogRepository auditRepository;
    private final PasswordEncoder passwordEncoder;

    // ==================== list ====================

    /** role: BUYER | SELLER | ADMIN | null; status: ALL | ACTIVE | LOCKED | DELETED */
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(String role, String status, String q, int page, int size) {
        Role r = null;
        if (role != null && !role.isBlank()) {
            try {
                r = Role.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw bad("Vai trò không hợp lệ");
            }
        }
        String st = status == null || status.isBlank() ? "ALL" : status.toUpperCase();
        if (!List.of("ALL", "ACTIVE", "LOCKED", "DELETED").contains(st)) throw bad("Trạng thái không hợp lệ");
        String needle = q == null ? "" : q.trim().toLowerCase();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(userRepository.search(r, st, needle, pageable), this::row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> counts() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("all", userRepository.countByRoleAndDeletedAtIsNull(Role.BUYER) + userRepository.countByRoleAndDeletedAtIsNull(Role.SELLER)
                + userRepository.countByRoleAndDeletedAtIsNull(Role.ADMIN));
        m.put("buyers", userRepository.countByRoleAndDeletedAtIsNull(Role.BUYER));
        m.put("sellers", userRepository.countByRoleAndDeletedAtIsNull(Role.SELLER));
        m.put("admins", userRepository.countByRoleAndDeletedAtIsNull(Role.ADMIN));
        m.put("locked", userRepository.countByEnabledFalseAndDeletedAtIsNull());
        m.put("deleted", userRepository.countByDeletedAtIsNotNull());
        return m;
    }

    // ==================== detail ====================

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        User u = find(id);
        Map<String, Object> m = row(u);
        List<Order> bought = orderRepository.findByBuyerIdOrderByCreatedAtDesc(id);
        List<Order> sales = bought.stream().filter(o -> !NOT_A_SALE.contains(o.getStatus())).toList();
        m.put("buyer", map(
                "orders", bought.size(),
                "spent", sales.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                "cancelled", bought.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count(),
                "openOrders", bought.stream().filter(o -> OPEN.contains(o.getStatus())).count(),
                "recentOrders", bought.stream().limit(5).map(o -> map("id", o.getId(), "code", o.getCode(), "shopName", o.getShop().getName(),
                        "status", o.getStatus().name(), "total", o.getTotal(), "createdAt", o.getCreatedAt())).toList()));
        shopRepository.findBySellerId(id).ifPresent(s -> {
            List<Order> shopOrders = orderRepository.findByShopIdOrderByCreatedAtDesc(s.getId());
            m.put("shopDetail", map(
                    "id", s.getId(), "name", s.getName(), "logoUrl", s.getLogoUrl(), "status", s.getStatus().name(),
                    "statusReason", s.getStatusReason(), "active", s.isActive(), "verified", s.isVerified(), "createdAt", s.getCreatedAt(),
                    "province", s.getProvince(), "products", productRepository.countByShopId(s.getId()),
                    "activeProducts", productRepository.countByShopIdAndActiveTrue(s.getId()),
                    "orders", shopOrders.size(),
                    "openOrders", shopOrders.stream().filter(o -> OPEN.contains(o.getStatus())).count(),
                    "revenue", shopOrders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).map(Order::getTotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)));
        });
        if (u.getRole() == Role.ADMIN) {
            m.put("permissions", UserPrincipal.permissionsOf(u).stream().map(Enum::name).toList());
        }
        m.put("history", auditService.forTarget("USER", id, 0, 20).getItems());
        return m;
    }

    // ==================== lock / unlock / delete ====================

    /** days: null = until unlocked, otherwise 1-365. */
    public Map<String, Object> lock(Long id, String reason, Integer days) {
        User u = find(id);
        guard(u, "khóa");
        String r = reason(reason);
        if (days != null && (days < 1 || days > 365)) throw bad("Thời hạn khóa từ 1 đến 365 ngày");
        if (!u.isEnabled() && u.getDeletedAt() == null && u.getLockedUntil() == null && days == null) throw bad("Tài khoản đã bị khóa");
        u.setEnabled(false);
        u.setLockedReason(r);
        u.setLockedAt(LocalDateTime.now());
        u.setLockedUntil(days == null ? null : LocalDateTime.now().plusDays(days));
        syncShop(u);
        auditService.log("LOCK_USER", "USER", u.getId(), u.getEmail(), (days == null ? "Khóa vĩnh viễn" : "Khóa " + days + " ngày") + " – " + r);
        return row(u);
    }

    public Map<String, Object> unlock(Long id) {
        User u = find(id);
        guard(u, "mở khóa");
        if (u.isEnabled()) throw bad("Tài khoản đang hoạt động");
        clearLock(u);
        syncShop(u);
        auditService.log("UNLOCK_USER", "USER", u.getId(), u.getEmail(), "Mở khóa tài khoản");
        return row(u);
    }

    /**
     * Anonymises the account. Refused while the user still has orders in progress (as buyer or seller),
     * so nobody is left with a parcel and no counterpart.
     */
    public Map<String, Object> delete(Long id, String reason) {
        User u = find(id);
        guard(u, "xóa");
        String r = reason(reason);
        long open = orderRepository.findByBuyerIdOrderByCreatedAtDesc(id).stream().filter(o -> OPEN.contains(o.getStatus())).count();
        Optional<Shop> shop = shopRepository.findBySellerId(id);
        if (shop.isPresent()) {
            open += orderRepository.findByShopIdOrderByCreatedAtDesc(shop.get().getId()).stream().filter(o -> OPEN.contains(o.getStatus())).count();
        }
        if (open > 0) throw bad("Tài khoản còn " + open + " đơn hàng đang xử lý, hãy chờ hoàn tất hoặc khóa tài khoản thay vì xóa");
        String originalEmail = u.getEmail();
        u.setEmail("deleted-" + u.getId() + "@deleted.shoppi.local");
        u.setFullName("Tài khoản đã xóa");
        u.setPhone(null);
        u.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        u.setEnabled(false);
        u.setDeletedAt(LocalDateTime.now());
        u.setLockedReason(r);
        u.setLockedUntil(null);
        u.setSuperAdmin(false);
        u.setAdminPermissions(null);
        shop.ifPresent(s -> {
            s.setStatus(ShopStatus.SUSPENDED);
            s.setStatusReason("Tài khoản người bán đã bị xóa");
            s.setStatusChangedAt(LocalDateTime.now());
            AdminShopService.syncVisibility(s);
        });
        auditService.log("DELETE_USER", "USER", u.getId(), originalEmail, r);
        return row(u);
    }

    /** Temporary locks whose time is up are lifted every 5 minutes (and the seller's shop re-opened). */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void liftExpiredLocks() {
        for (User u : userRepository.findByEnabledFalseAndDeletedAtIsNullAndLockedUntilBefore(LocalDateTime.now())) {
            clearLock(u);
            syncShop(u);
            auditRepository.save(AdminAuditLog.builder().adminName("Hệ thống").action("UNLOCK_USER").targetType("USER")
                    .targetId(u.getId()).targetLabel(u.getEmail()).detail("Hết thời hạn khóa").build());
            log.info("Lifted the expired lock of user {}", u.getId());
        }
    }

    // ==================== helpers ====================

    private static void clearLock(User u) {
        u.setEnabled(true);
        u.setLockedReason(null);
        u.setLockedAt(null);
        u.setLockedUntil(null);
    }

    private void syncShop(User u) {
        shopRepository.findBySellerId(u.getId()).ifPresent(AdminShopService::syncVisibility);
    }

    /** Nobody acts on their own account; only the super admin acts on admins, and never on another super admin. */
    private void guard(User target, String what) {
        Long me = CurrentUser.id();
        if (target.getId().equals(me)) throw new ApiException("Bạn không thể " + what + " tài khoản của chính mình", HttpStatus.FORBIDDEN);
        if (target.getDeletedAt() != null) throw bad("Tài khoản đã bị xóa");
        if (target.getRole() == Role.ADMIN) {
            User actor = find(me);
            if (!actor.isSuperAdmin() || target.isSuperAdmin()) {
                throw new ApiException("Chỉ quản trị viên cấp cao mới được " + what + " tài khoản quản trị", HttpStatus.FORBIDDEN);
            }
        }
    }

    private static String reason(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.length() < 5) throw bad("Vui lòng nhập lý do (ít nhất 5 ký tự)");
        if (r.length() > 500) throw bad("Lý do tối đa 500 ký tự");
        return r;
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ApiException("Tài khoản không tồn tại", HttpStatus.NOT_FOUND));
    }

    Map<String, Object> row(User u) {
        String status = u.getDeletedAt() != null ? "DELETED" : UserPrincipal.isActive(u, LocalDateTime.now()) && u.isEnabled() ? "ACTIVE" : "LOCKED";
        Map<String, Object> m = map("id", u.getId(), "email", u.getEmail(), "fullName", u.getFullName(), "phone", u.getPhone(),
                "role", u.getRole().name(), "authProvider", u.getAuthProvider().name(), "status", status,
                "lockedReason", u.getLockedReason(), "lockedAt", u.getLockedAt(), "lockedUntil", u.getLockedUntil(),
                "deletedAt", u.getDeletedAt(), "createdAt", u.getCreatedAt(), "lastLoginAt", u.getLastLoginAt(),
                "superAdmin", u.isSuperAdmin());
        if (u.getRole() == Role.SELLER) {
            shopRepository.findBySellerId(u.getId()).ifPresent(s -> m.put("shop", map("id", s.getId(), "name", s.getName(), "status", s.getStatus().name())));
        }
        return m;
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
