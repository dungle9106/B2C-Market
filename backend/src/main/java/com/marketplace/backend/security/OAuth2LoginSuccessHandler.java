package com.marketplace.backend.security;

import com.marketplace.backend.dto.response.AuthResponse;
import com.marketplace.backend.entity.AuthProvider;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs after Spring Security has exchanged the provider's authorization code for tokens and loaded the
 * profile (Google: OpenID Connect; Facebook / GitHub: SocialOAuth2UserService). We either sign the matching
 * Shoppi account in (creating it on the first visit) and hand our JWT back in the URL fragment (#token=...;
 * a fragment is never sent to a server, so it stays out of access logs), or, for a "link" sign-in, attach
 * this provider account to the signed-in Shoppi account (#linked=google).
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final OAuth2FrontendRedirect frontendRedirect;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User profile = (OAuth2User) authentication.getPrincipal();
        String registrationId = authentication instanceof OAuth2AuthenticationToken t ? t.getAuthorizedClientRegistrationId() : "google";
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());
        String subject = String.valueOf((Object) profile.getAttribute("sub"));
        String email = profile.getAttribute("email");
        String name = profile.getAttribute("name");
        boolean emailVerified = Boolean.parseBoolean(String.valueOf((Object) profile.getAttribute("email_verified")));
        Long linkUserId = frontendRedirect.linkUserId(request);

        String fragment;
        try {
            if (linkUserId != null) {
                authService.linkOAuth(linkUserId, provider, subject, email, name);
                fragment = "linked=" + registrationId;
            } else {
                AuthResponse auth = authService.loginWithOAuth2(provider, subject, email, name, emailVerified);
                // two-step verification still to do: the login page asks for the code
                fragment = auth.isTwoFactorRequired() ? "challenge=" + auth.getChallenge()
                        : "token=" + auth.getToken() + "&refresh=" + auth.getRefreshToken();
            }
        } catch (ApiException e) {
            fragment = "error=" + e.getMessage();
        }

        String target = frontendRedirect.buildUrl(request, fragment);

        // the HTTP session only carried the OAuth2 "state" (and where to return) through the
        // round-trip to the provider; our API itself is stateless (JWT)
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        response.sendRedirect(target);
    }
}
