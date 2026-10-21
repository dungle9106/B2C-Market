package com.marketplace.backend.service;

import com.marketplace.backend.entity.AdminPermission;
import com.marketplace.backend.entity.AuthProvider;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * "Phan quyen nhan vien admin": the super admin (owner account) and staff admins with a subset of
 * AdminPermission. Rules that keep the console safe:
 *  - nobody edits or revokes their own account (no self-lockout, no self-promotion);
 *  - the super admin is never edited or revoked by anyone;
 *  - only the super admin grants, removes or touches the STAFF permission, so a staff manager
 *    cannot create someone more powerful than themselves.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AdminStaffService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService auditService;

    /** The signed-in admin and what they may do (the console builds its menu from this). */
    @Transactional(readOnly = true)
    public Map<String, Object> me() {
        User u = find(CurrentUser.id());
        Map<String, Object> m = view(u);
        m.put("allPermissions", Arrays.stream(AdminPermission.values()).map(p -> AdminUserService.map("key", p.name(), "label", p.getLabel())).toList());
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return userRepository.findByRoleAndDeletedAtIsNullOrderByIdAsc(Role.ADMIN).stream()
                .sorted(Comparator.comparing((User u) -> !u.isSuperAdmin()).thenComparing(User::getId))
                .map(this::view).toList();
    }

    /** body: { email, fullName, password, permissions: [..] } - a new account that can only sign in to the admin console. */
    public Map<String, Object> create(String email, String fullName, String password, List<String> permissions) {
        String e = email == null ? "" : email.trim().toLowerCase();
        if (!e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw bad("Email không hợp lệ");
        if (userRepository.existsByEmail(e)) throw bad("Email đã được sử dụng");
        if (fullName == null || fullName.trim().length() < 2) throw bad("Vui lòng nhập họ tên");
        com.marketplace.backend.security.PasswordPolicy.check(password, email);
        Set<AdminPermission> perms = parse(permissions);
        checkStaffGrant(perms, EnumSet.noneOf(AdminPermission.class));
        User u = userRepository.save(User.builder().email(e).fullName(fullName.trim()).password(passwordEncoder.encode(password))
                .role(Role.ADMIN).authProvider(AuthProvider.LOCAL).enabled(true).superAdmin(false).adminPermissions(join(perms)).build());
        auditService.log("CREATE_STAFF", "USER", u.getId(), u.getEmail(), "Quyền: " + labels(perms));
        return view(u);
    }

    public Map<String, Object> update(Long id, List<String> permissions) {
        User u = editable(id);
        Set<AdminPermission> before = UserPrincipal.permissionsOf(u);
        Set<AdminPermission> perms = parse(permissions);
        checkStaffGrant(perms, before);
        u.setAdminPermissions(join(perms));
        auditService.log("UPDATE_STAFF", "USER", u.getId(), u.getEmail(), labels(before) + " → " + labels(perms));
        return view(u);
    }

    /** Takes the admin role away: the account becomes a regular buyer account. */
    public Map<String, Object> revoke(Long id) {
        User u = editable(id);
        Set<AdminPermission> before = UserPrincipal.permissionsOf(u);
        checkStaffGrant(EnumSet.noneOf(AdminPermission.class), before);
        u.setRole(Role.BUYER);
        u.setAdminPermissions(null);
        auditService.log("REVOKE_STAFF", "USER", u.getId(), u.getEmail(), "Thu hồi quyền quản trị (" + labels(before) + ")");
        return view(u);
    }

    // ---- rules ----

    private User editable(Long id) {
        User u = find(id);
        if (u.getRole() != Role.ADMIN || u.getDeletedAt() != null) throw new ApiException("Nhân viên không tồn tại", HttpStatus.NOT_FOUND);
        if (u.getId().equals(CurrentUser.id())) throw new ApiException("Bạn không thể tự thay đổi quyền của mình", HttpStatus.FORBIDDEN);
        if (u.isSuperAdmin()) throw new ApiException("Không thể thay đổi quản trị viên cấp cao", HttpStatus.FORBIDDEN);
        return u;
    }

    // the STAFF permission (managing admins) is granted / removed only by the super admin
    private void checkStaffGrant(Set<AdminPermission> after, Set<AdminPermission> before) {
        boolean touchesStaff = after.contains(AdminPermission.STAFF) || before.contains(AdminPermission.STAFF);
        if (touchesStaff && !find(CurrentUser.id()).isSuperAdmin()) {
            throw new ApiException("Chỉ quản trị viên cấp cao mới được cấp hoặc thu hồi quyền quản lý nhân viên", HttpStatus.FORBIDDEN);
        }
    }

    private static Set<AdminPermission> parse(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) throw bad("Chọn ít nhất một quyền");
        Set<AdminPermission> set = EnumSet.noneOf(AdminPermission.class);
        for (String p : permissions) {
            try {
                set.add(AdminPermission.valueOf(p));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw bad("Quyền không hợp lệ: " + p);
            }
        }
        return set;
    }

    private static String join(Set<AdminPermission> perms) {
        return perms.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static String labels(Set<AdminPermission> perms) {
        return perms.isEmpty() ? "không có" : perms.stream().map(AdminPermission::getLabel).collect(Collectors.joining(", "));
    }

    private Map<String, Object> view(User u) {
        return AdminUserService.map("id", u.getId(), "email", u.getEmail(), "fullName", u.getFullName(), "superAdmin", u.isSuperAdmin(),
                "permissions", UserPrincipal.permissionsOf(u).stream().map(Enum::name).toList(),
                "role", u.getRole().name(), "enabled", u.isEnabled(), "createdAt", u.getCreatedAt(), "lastLoginAt", u.getLastLoginAt());
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ApiException("Tài khoản không tồn tại", HttpStatus.NOT_FOUND));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
