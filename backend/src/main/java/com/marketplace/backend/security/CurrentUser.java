package com.marketplace.backend.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

// small helper so controllers/services don't repeat the SecurityContext boilerplate
public class CurrentUser {

    /** request attribute set by JwtAuthFilter: the session id of the access token */
    public static final String SESSION_ATTRIBUTE = "shoppi.sessionId";

    public static Long id() {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.getId();
    }

    public static String role() {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.getRole();
    }

    /** The signed-in device (session) of this request, or null. */
    public static String sessionId() {
        RequestAttributes a = RequestContextHolder.getRequestAttributes();
        Object sid = a == null ? null : a.getAttribute(SESSION_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return sid == null ? null : sid.toString();
    }
}
