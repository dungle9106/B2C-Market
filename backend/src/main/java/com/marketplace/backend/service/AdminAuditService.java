package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.AdminAuditLog;
import com.marketplace.backend.entity.AdminPermission;
import com.marketplace.backend.entity.NotificationType;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.repository.AdminAuditLogRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// "Nhat ky quan tri" + notifying only the admins who handle a given section.
@Service
@Transactional
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository auditRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public void log(String action, String targetType, Long targetId, String targetLabel, String detail) {
        Long adminId = CurrentUser.id();
        String adminName = userRepository.findById(adminId).map(User::getFullName).orElse("Admin #" + adminId);
        auditRepository.save(AdminAuditLog.builder().adminId(adminId).adminName(adminName).action(action).targetType(targetType)
                .targetId(targetId).targetLabel(cut(targetLabel, 300)).detail(cut(detail, 1000)).build());
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> forTarget(String targetType, Long targetId, int page, int size) {
        return PageResponse.of(auditRepository.findByTargetTypeAndTargetIdOrderByIdDesc(targetType, targetId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))), AdminAuditService::view);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> search(Long adminId, String targetType, int page, int size) {
        return PageResponse.of(auditRepository.search(adminId, blank(targetType) ? null : targetType.toUpperCase(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))), AdminAuditService::view);
    }

    /** Admins (enabled, not deleted) who have the permission. */
    @Transactional(readOnly = true)
    public List<Long> adminsWith(AdminPermission permission) {
        return userRepository.findByRoleAndDeletedAtIsNullOrderByIdAsc(Role.ADMIN).stream()
                .filter(User::isEnabled)
                .filter(u -> UserPrincipal.permissionsOf(u).contains(permission))
                .map(User::getId).toList();
    }

    public void notifyAdmins(AdminPermission permission, String title, String content, String link) {
        notificationService.notifyMany(adminsWith(permission), NotificationType.SYSTEM, title, content, link);
    }

    static Map<String, Object> view(AdminAuditLog a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("adminId", a.getAdminId());
        m.put("adminName", a.getAdminName());
        m.put("action", a.getAction());
        m.put("targetType", a.getTargetType());
        m.put("targetId", a.getTargetId());
        m.put("targetLabel", a.getTargetLabel());
        m.put("detail", a.getDetail());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
