package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.ProductModeration;
import com.marketplace.backend.entity.ProductReport;
import com.marketplace.backend.entity.FlashSaleRegistration;
import com.marketplace.backend.entity.Refund;
import com.marketplace.backend.entity.ReturnStatus;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.entity.ShopStatus;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.ProductReportRepository;
import com.marketplace.backend.repository.FlashSaleRegistrationRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.RefundRepository;
import com.marketplace.backend.repository.ReturnRequestRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Admin console. Each path group needs its own permission (SecurityConfig): users -> PERM_USERS,
// shops -> PERM_SHOPS, staff/audit -> PERM_STAFF, settings -> PERM_SETTINGS; me/stats -> any admin.
// Categories and product moderation: AdminCatalogController (PERM_PRODUCTS).
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final ProductReportRepository reportRepository;
    private final ReturnRequestRepository returnRepository;
    private final RefundRepository refundRepository;
    private final FlashSaleRegistrationRepository flashRegistrationRepository;
    private final AdminUserService userService;
    private final AdminShopService shopService;
    private final AdminStaffService staffService;
    private final AdminAuditService auditService;
    private final PlatformSettingService settingService;
    private final PlatformConfig platformConfig;

    // ==== the signed-in admin: { superAdmin, permissions, allPermissions } ====

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        Map<String, Object> m = new HashMap<>(staffService.me());
        // the console sends an admin without 2FA to the security page while the platform requires it
        boolean totp = userRepository.findById(com.marketplace.backend.security.CurrentUser.id()).map(u -> u.isTotpEnabled()).orElse(false);
        m.put("twoFactorEnabled", totp);
        m.put("twoFactorSetupRequired", !totp && platformConfig.requireAdmin2fa());
        return ResponseEntity.ok(m);
    }

    // ==== users ====

    // ?role=BUYER|SELLER|ADMIN&status=ALL|ACTIVE|LOCKED|DELETED&q=&page=&size=
    @GetMapping("/users")
    public ResponseEntity<PageResponse<Map<String, Object>>> listUsers(@RequestParam(required = false) String role,
                                                                       @RequestParam(defaultValue = "ALL") String status,
                                                                       @RequestParam(required = false) String q,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.list(role, status, q, page, size));
    }

    @GetMapping("/users/counts")
    public ResponseEntity<Map<String, Object>> userCounts() {
        return ResponseEntity.ok(userService.counts());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> user(@PathVariable Long id) {
        return ResponseEntity.ok(userService.detail(id));
    }

    // body: { reason, days? } (no days = until unlocked)
    @PostMapping("/users/{id}/lock")
    public ResponseEntity<Map<String, Object>> lock(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object days = body.get("days");
        return ResponseEntity.ok(userService.lock(id, (String) body.get("reason"), days instanceof Number n ? n.intValue() : null));
    }

    @PostMapping("/users/{id}/unlock")
    public ResponseEntity<Map<String, Object>> unlock(@PathVariable Long id) {
        return ResponseEntity.ok(userService.unlock(id));
    }

    // body: { reason } - anonymises the account
    @PostMapping("/users/{id}/delete")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(userService.delete(id, body.get("reason")));
    }

    // ==== shops: approval & moderation ====

    // ?status=PENDING|APPROVED|REJECTED|SUSPENDED|ALL&q=
    @GetMapping("/shops")
    public ResponseEntity<PageResponse<Map<String, Object>>> shops(@RequestParam(defaultValue = "ALL") String status,
                                                                   @RequestParam(required = false) String q,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(shopService.list(status, q, page, size));
    }

    @GetMapping("/shops/counts")
    public ResponseEntity<Map<String, Object>> shopCounts() {
        return ResponseEntity.ok(shopService.counts());
    }

    @PostMapping("/shops/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(shopService.approve(id));
    }

    @PostMapping("/shops/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(shopService.reject(id, body.get("reason")));
    }

    @PostMapping("/shops/{id}/suspend")
    public ResponseEntity<Map<String, Object>> suspend(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(shopService.suspend(id, body.get("reason")));
    }

    @PostMapping("/shops/{id}/reinstate")
    public ResponseEntity<Map<String, Object>> reinstate(@PathVariable Long id) {
        return ResponseEntity.ok(shopService.reinstate(id));
    }

    // ==== platform settings ====

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> settings() {
        return ResponseEntity.ok(settingService.all());
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(settingService.update(body));
    }

    // ==== staff & permissions ====

    @GetMapping("/staff")
    public ResponseEntity<List<Map<String, Object>>> staff() {
        return ResponseEntity.ok(staffService.list());
    }

    // body: { email, fullName, password, permissions: [...] }
    @SuppressWarnings("unchecked")
    @PostMapping("/staff")
    public ResponseEntity<Map<String, Object>> createStaff(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(staffService.create((String) body.get("email"), (String) body.get("fullName"),
                (String) body.get("password"), (List<String>) body.get("permissions")));
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/staff/{id}")
    public ResponseEntity<Map<String, Object>> updateStaff(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(staffService.update(id, (List<String>) body.get("permissions")));
    }

    @PostMapping("/staff/{id}/revoke")
    public ResponseEntity<Map<String, Object>> revokeStaff(@PathVariable Long id) {
        return ResponseEntity.ok(staffService.revoke(id));
    }

    // ?adminId=&targetType=USER|SHOP|SETTING
    @GetMapping("/audit")
    public ResponseEntity<PageResponse<Map<String, Object>>> audit(@RequestParam(required = false) Long adminId,
                                                                   @RequestParam(required = false) String targetType,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(auditService.search(adminId, targetType, page, size));
    }

    // ==== overview ====

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> platformStats() {
        List<Order> orders = orderRepository.findAll();
        // GMV as in the reports: not cancelled, refunded or left unpaid
        java.util.Set<com.marketplace.backend.entity.OrderStatus> notSale = java.util.EnumSet.of(com.marketplace.backend.entity.OrderStatus.CANCELLED,
                com.marketplace.backend.entity.OrderStatus.REFUNDED, com.marketplace.backend.entity.OrderStatus.AWAITING_PAYMENT);
        BigDecimal totalRevenue = orders.stream()
                .filter(o -> !notSale.contains(o.getStatus()))
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.countByRoleAndDeletedAtIsNull(Role.BUYER) + userRepository.countByRoleAndDeletedAtIsNull(Role.SELLER));
        stats.put("buyers", userRepository.countByRoleAndDeletedAtIsNull(Role.BUYER));
        stats.put("sellers", userRepository.countByRoleAndDeletedAtIsNull(Role.SELLER));
        stats.put("lockedUsers", userRepository.countByEnabledFalseAndDeletedAtIsNull());
        stats.put("pendingShops", shopRepository.countByStatus(ShopStatus.PENDING));
        stats.put("suspendedShops", shopRepository.countByStatus(ShopStatus.SUSPENDED));
        stats.put("pendingProducts", productRepository.countByModerationStatus(ProductModeration.PENDING));
        stats.put("openReports", reportRepository.countByStatus(ProductReport.Status.OPEN));
        stats.put("openDisputes", returnRepository.countByComplaintIsNotNullAndStatus(ReturnStatus.ESCALATED));
        stats.put("pendingRefunds", refundRepository.countByStatus(Refund.Status.PENDING));
        stats.put("pendingFlashItems", flashRegistrationRepository.countByStatus(FlashSaleRegistration.Status.PENDING));
        stats.put("totalOrders", orders.size());
        stats.put("totalRevenue", totalRevenue);
        return ResponseEntity.ok(stats);
    }
}
