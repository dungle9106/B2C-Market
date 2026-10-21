package com.marketplace.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// User cancelled on Google's consent screen, bad client id/secret, etc.:
// send them back to the Vue app with a readable error instead of a Whitelabel page.
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final OAuth2FrontendRedirect frontendRedirect;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("Google OAuth2 login failed: {}", exception.getMessage());

        String target = frontendRedirect.buildUrl(request, "error=Đăng nhập Google thất bại, vui lòng thử lại");
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        response.sendRedirect(target);
    }
}
