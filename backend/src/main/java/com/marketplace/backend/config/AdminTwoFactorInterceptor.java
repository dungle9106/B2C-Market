package com.marketplace.backend.config;

import com.marketplace.backend.security.UserPrincipal;
import com.marketplace.backend.service.PlatformConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// "Bat buoc xac thuc 2 lop cho quan tri vien": while it is on, an admin without 2FA can only reach
// /api/admin/me (to learn why) - the console sends them to the security page to turn it on.
@Configuration
@RequiredArgsConstructor
public class AdminTwoFactorInterceptor implements WebMvcConfigurer, HandlerInterceptor {

    private final PlatformConfig config;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/admin/**").excludePathPatterns("/api/admin/me");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p && !p.isTotpEnabled() && config.requireAdmin2fa()) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":403,\"code\":\"TWO_FACTOR_SETUP_REQUIRED\",\"message\":"
                    + "\"Sàn yêu cầu quản trị viên bật xác thực 2 lớp. Vui lòng bật trong mục Bảo mật tài khoản.\"}");
            return false;
        }
        return true;
    }
}
