package com.marketplace.backend.security;

import com.marketplace.backend.dto.response.AuthResponse;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs after Spring Security has exchanged Google's authorization code for
 * tokens and loaded the user's profile (OpenID Connect). We map that Google
 * profile to our own User, issue our usual JWT, and redirect the browser back
 * to the Vue app with the token in the URL fragment (#token=...). A fragment
 * is never sent to any server, so the JWT doesn't end up in access logs.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final OAuth2FrontendRedirect frontendRedirect;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();
        String email = googleUser.getAttribute("email");
        String name = googleUser.getAttribute("name");
        boolean emailVerified = Boolean.parseBoolean(String.valueOf(googleUser.getAttribute("email_verified")));

        String fragment;
        try {
            AuthResponse auth = authService.loginWithOAuth2(email, name, emailVerified);
            // two-step verification still to do: the login page asks for the code
            fragment = auth.isTwoFactorRequired() ? "challenge=" + auth.getChallenge()
                    : "token=" + auth.getToken() + "&refresh=" + auth.getRefreshToken();
        } catch (ApiException e) {
            fragment = "error=" + e.getMessage();
        }

        String target = frontendRedirect.buildUrl(request, fragment);

        // we only needed the HTTP session to carry the OAuth2 "state" (and the
        // frontend origin) through the Google round-trip; our API is stateless (JWT)
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        response.sendRedirect(target);
    }
}
