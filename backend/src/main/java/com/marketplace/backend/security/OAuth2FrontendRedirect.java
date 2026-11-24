package com.marketplace.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Works out where to send the browser after a social sign-in, and what the sign-in was for.
 *  - Web: the frontend starts with /oauth2/authorization/{provider}?redirect_origin=<its origin>, so the user
 *    lands back on whichever port Vite runs on. The origin must pass the CORS allow-list, otherwise we'd be an
 *    open redirect that leaks JWTs.
 *  - Mobile app: ?app_redirect=shoppi://oauth2/redirect (or exp://... in Expo Go). Only the app schemes in
 *    app.oauth2.app-schemes are accepted; the app's in-app browser session catches that address.
 *  - Linking ("Lien ket tai khoan Google" on the security page): ?link=<one-time token> from
 *    POST /api/account/oauth/link-token, so the result is attached to the signed-in account instead of signing in.
 */
@Component
@RequiredArgsConstructor
public class OAuth2FrontendRedirect {

    private static final String ORIGIN_ATTR = "OAUTH2_FRONTEND_ORIGIN";
    private static final String APP_ATTR = "OAUTH2_APP_REDIRECT";
    private static final String LINK_ATTR = "OAUTH2_LINK_USER";
    private static final String REDIRECT_PATH = "/oauth2/redirect";
    private static final long LINK_TTL_MS = 5 * 60_000;

    private final CorsConfigurationSource corsConfigurationSource;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, long[]> linkTokens = new ConcurrentHashMap<>();   // token -> {userId, expiresAt}

    @Value("${app.oauth2.redirect-uri}")
    private String fallbackRedirectUri;

    @Value("${app.oauth2.app-schemes:shoppi,shoppiseller,shoppiadmin}")
    private String appSchemes;

    // called when the sign-in starts, before the browser leaves for the provider
    public void rememberOrigin(HttpServletRequest request) {
        HttpSession session = request.getSession();
        String origin = request.getParameter("redirect_origin");
        if (origin != null && !origin.isBlank()) {
            CorsConfiguration cors = corsConfigurationSource.getCorsConfiguration(request);
            if (cors != null && cors.checkOrigin(origin) != null) session.setAttribute(ORIGIN_ATTR, origin);
        }
        String app = request.getParameter("app_redirect");
        if (app != null && appRedirectAllowed(app)) session.setAttribute(APP_ATTR, app);
        String link = request.getParameter("link");
        Long userId = link != null ? useLinkToken(link) : null;
        if (userId != null) session.setAttribute(LINK_ATTR, userId);
        else session.removeAttribute(LINK_ATTR);
    }

    /** The signed-in account a "link" sign-in belongs to, or null for a normal sign-in. */
    public Long linkUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object v = session != null ? session.getAttribute(LINK_ATTR) : null;
        return v instanceof Long l ? l : null;
    }

    // called on success/failure; must run before the session is invalidated
    public String buildUrl(HttpServletRequest request, String fragment) {
        HttpSession session = request.getSession(false);
        Object app = session != null ? session.getAttribute(APP_ATTR) : null;
        if (app != null) {
            // custom schemes: keep the address as given and add the result after '#'
            String base = app.toString();
            int hash = base.indexOf('#');
            if (hash >= 0) base = base.substring(0, hash);
            return base + "#" + UriComponentsBuilder.newInstance().fragment(fragment).encode().build().toUriString().substring(1);
        }
        Object origin = session != null ? session.getAttribute(ORIGIN_ATTR) : null;
        String base = origin != null ? origin + REDIRECT_PATH : fallbackRedirectUri;

        return UriComponentsBuilder.fromUriString(base)
                .fragment(fragment)
                .encode()
                .build()
                .toUriString();
    }

    // ==================== one-time link tokens ====================

    public String issueLinkToken(Long userId) {
        long now = System.currentTimeMillis();
        linkTokens.values().removeIf(v -> v[1] < now);
        byte[] b = new byte[24];
        random.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        linkTokens.put(token, new long[]{userId, now + LINK_TTL_MS});
        return token;
    }

    private Long useLinkToken(String token) {
        long[] v = linkTokens.remove(token);
        return v != null && v[1] >= System.currentTimeMillis() ? v[0] : null;
    }

    boolean appRedirectAllowed(String url) {
        int colon = url.indexOf("://");
        if (colon <= 0 || url.length() > 300) return false;
        String scheme = url.substring(0, colon).toLowerCase();
        if (!Pattern.matches("[a-z][a-z0-9+.-]*", scheme)) return false;
        for (String s : appSchemes.split(",")) {
            if (s.trim().equalsIgnoreCase(scheme)) return true;
        }
        return false;
    }
}
