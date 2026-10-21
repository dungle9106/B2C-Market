package com.marketplace.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// Identifies a visitor for de-duplicating views/clicks: the user id when logged in,
// otherwise the IP address plus a hash of the browser's user agent.
public final class VisitorKey {

    private VisitorKey() {
    }

    /** The logged-in user's id, or null for a guest (works on public endpoints). */
    public static Long userIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal u ? u.getId() : null;
    }

    public static String of(HttpServletRequest request, Long userId) {
        return userId != null ? "u:" + userId
                : "g:" + request.getRemoteAddr() + ":" + Integer.toHexString(String.valueOf(request.getHeader("User-Agent")).hashCode());
    }
}
