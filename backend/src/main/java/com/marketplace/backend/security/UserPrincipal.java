package com.marketplace.backend.security;

import com.marketplace.backend.entity.AdminPermission;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.*;

@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String role;
    private final boolean enabled;
    private final boolean superAdmin;
    private final boolean totpEnabled;
    private final Set<AdminPermission> permissions;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.role = user.getRole().name();
        this.enabled = isActive(user, LocalDateTime.now());
        this.superAdmin = user.getRole() == Role.ADMIN && user.isSuperAdmin();
        this.totpEnabled = user.isTotpEnabled();
        this.permissions = permissionsOf(user);
    }

    /** Not deleted, and not locked (a temporary lock counts as over once its end time has passed). */
    public static boolean isActive(User user, LocalDateTime now) {
        if (user.getDeletedAt() != null) return false;
        return user.isEnabled() || (user.getLockedUntil() != null && user.getLockedUntil().isBefore(now));
    }

    /** Admin permissions of the account: all of them for the super admin, none for buyers/sellers. */
    public static Set<AdminPermission> permissionsOf(User user) {
        if (user.getRole() != Role.ADMIN) return EnumSet.noneOf(AdminPermission.class);
        if (user.isSuperAdmin()) return EnumSet.allOf(AdminPermission.class);
        Set<AdminPermission> set = EnumSet.noneOf(AdminPermission.class);
        if (user.getAdminPermissions() != null) {
            for (String p : user.getAdminPermissions().split(",")) {
                try {
                    if (!p.isBlank()) set.add(AdminPermission.valueOf(p.trim()));
                } catch (IllegalArgumentException ignored) {
                    // a permission removed from the enum
                }
            }
        }
        return set;
    }

    // ROLE_ADMIN plus one PERM_<name> per admin permission (SecurityConfig maps /api/admin/** sections to them)
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new SimpleGrantedAuthority("ROLE_" + role));
        permissions.forEach(p -> list.add(new SimpleGrantedAuthority("PERM_" + p.name())));
        return list;
    }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return enabled; }
}
