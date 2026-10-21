package com.marketplace.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Works out where to send the browser after Google login. The frontend starts
 * the flow with /oauth2/authorization/google?redirect_origin=<its origin>, so
 * the user lands back on whichever port Vite is actually running on (5173,
 * 5174, ...). The origin is only trusted if it passes the same CORS allow-list
 * as the API, otherwise we'd be an open redirect that leaks JWTs.
 */
@Component
@RequiredArgsConstructor
public class OAuth2FrontendRedirect {

    private static final String ORIGIN_ATTR = "OAUTH2_FRONTEND_ORIGIN";
    private static final String REDIRECT_PATH = "/oauth2/redirect";

    private final CorsConfigurationSource corsConfigurationSource;

    @Value("${app.oauth2.redirect-uri}")
    private String fallbackRedirectUri;

    // called when the login starts, before the browser leaves for Google
    public void rememberOrigin(HttpServletRequest request) {
        String origin = request.getParameter("redirect_origin");
        if (origin == null || origin.isBlank()) return;

        CorsConfiguration cors = corsConfigurationSource.getCorsConfiguration(request);
        if (cors != null && cors.checkOrigin(origin) != null) {
            request.getSession().setAttribute(ORIGIN_ATTR, origin);
        }
    }

    // called on success/failure; must run before the session is invalidated
    public String buildUrl(HttpServletRequest request, String fragment) {
        HttpSession session = request.getSession(false);
        Object origin = session != null ? session.getAttribute(ORIGIN_ATTR) : null;
        String base = origin != null ? origin + REDIRECT_PATH : fallbackRedirectUri;

        return UriComponentsBuilder.fromUriString(base)
                .fragment(fragment)
                .encode()
                .build()
                .toUriString();
    }
}
