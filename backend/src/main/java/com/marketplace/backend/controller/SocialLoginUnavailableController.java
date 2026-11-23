package com.marketplace.backend.controller;

import com.marketplace.backend.security.OAuth2FrontendRedirect;
import com.marketplace.backend.security.SocialLoginSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;

// /oauth2/authorization/{provider} only reaches MVC when the provider is not set up (Spring Security's
// filter handles the configured ones): send the browser / app back with a readable message
@Controller
@RequiredArgsConstructor
public class SocialLoginUnavailableController {

    private final OAuth2FrontendRedirect frontendRedirect;

    @GetMapping("/oauth2/authorization/{provider}")
    public void unavailable(@PathVariable String provider, HttpServletRequest request, HttpServletResponse response) throws IOException {
        SocialLoginSettings.Provider p = SocialLoginSettings.provider(provider);
        frontendRedirect.rememberOrigin(request);
        String target = frontendRedirect.buildUrl(request, "error=Đăng nhập bằng " + (p != null ? p.name() : provider) + " chưa được bật trên Shoppi");
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        response.sendRedirect(target);
    }
}
