package com.marketplace.backend.config;

import com.marketplace.backend.security.JwtAuthFilter;
import com.marketplace.backend.security.OAuth2FrontendRedirect;
import com.marketplace.backend.security.OAuth2LoginFailureHandler;
import com.marketplace.backend.security.OAuth2LoginSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // The OAuth2 handlers are method parameters rather than constructor fields:
    // they depend (via AuthService) on the PasswordEncoder bean declared in
    // this class, and constructor injection would make that a circular reference.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   OAuth2LoginSuccessHandler oAuth2SuccessHandler,
                                                   OAuth2LoginFailureHandler oAuth2FailureHandler,
                                                   OAuth2FrontendRedirect frontendRedirect,
                                                   ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            // the API only returns JSON / files: forbid framing, sniffing and leaking the referrer
            .headers(h -> h
                .frameOptions(f -> f.deny())
                .contentTypeOptions(c -> { })
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; img-src 'self'; media-src 'self'; frame-ancestors 'none'"))
                .httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31536000)))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // STATELESS: our API is authenticated purely by JWT. The OAuth2 flow
            // still briefly uses an HTTP session to remember the "state" param
            // between /oauth2/authorization/google and Google's callback.
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // without this, an anonymous API call would get a 302 to Google's
            // login page (oauth2Login's default entry point) instead of a 401
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            // "Dang nhap voi Google": browser goes to /oauth2/authorization/google,
            // Google redirects back to /login/oauth2/code/google, Spring Security
            // exchanges the code + loads the profile, then our success handler
            // issues a JWT and redirects to the frontend.
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(ep -> ep.authorizationRequestResolver(
                    authorizationRequestResolver(clientRegistrationRepository, frontendRedirect)))
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                // Spring forwards errors (e.g. a 403) to /error without re-running the JWT filter;
                // it must stay reachable or every error would turn into a misleading 401
                .requestMatchers("/error").permitAll()
                // WebSocket handshake; the STOMP CONNECT frame carries the JWT (realtime.WebSocketConfig)
                .requestMatchers("/ws", "/ws/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**", "/api/shops/**").permitAll()
                // clicks on sponsored search results, guests included
                .requestMatchers(HttpMethod.POST, "/api/ads/*/click", "/api/banners/*/click").permitAll()
                // payment gateway callbacks (signature-checked) and the list of available gateways
                .requestMatchers(HttpMethod.GET, "/api/payments/vnpay/return", "/api/payments/vnpay/ipn", "/api/payments/methods").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/shipping/ghn/webhook").permitAll()
                // platform marketing shown to everyone: home banners, programme pages, Shoppi Flash Sale slots
                .requestMatchers(HttpMethod.GET, "/api/banners", "/api/campaigns/**", "/api/flash-sale/**", "/api/policies/**", "/api/policies").permitAll()
                // must come before the generic /api/seller/** rule below: a BUYER
                // hits this once to become a SELLER, so it can't require ROLE_SELLER
                .requestMatchers("/api/seller/apply").hasAnyAuthority("ROLE_BUYER")
                .requestMatchers("/api/seller/**").hasAuthority("ROLE_SELLER")
                // admin console: each section needs its own permission (staff admins get a subset)
                .requestMatchers("/api/admin/users/**").hasAuthority("PERM_USERS")
                .requestMatchers("/api/admin/shops/**", "/api/admin/verifications/**").hasAuthority("PERM_SHOPS")
                // a manual refund is a finance action even though it hangs off an order
                .requestMatchers("/api/admin/orders/*/refund").hasAuthority("PERM_FINANCE")
                .requestMatchers("/api/admin/complaints/**", "/api/admin/orders/**", "/api/admin/disputes/**").hasAuthority("PERM_ORDERS")
                .requestMatchers("/api/admin/refunds/**", "/api/admin/settlements/**", "/api/admin/reconciliation/**").hasAuthority("PERM_FINANCE")
                .requestMatchers("/api/admin/vouchers/**", "/api/admin/campaigns/**", "/api/admin/banners/**", "/api/admin/flash-sales/**")
                    .hasAuthority("PERM_VOUCHERS")
                .requestMatchers("/api/admin/support/**").hasAuthority("PERM_SUPPORT")
                .requestMatchers("/api/admin/broadcasts/**").hasAuthority("PERM_BROADCAST")
                .requestMatchers("/api/admin/staff/**", "/api/admin/audit/**").hasAuthority("PERM_STAFF")
                .requestMatchers("/api/admin/products/**", "/api/admin/categories/**").hasAuthority("PERM_PRODUCTS")
                .requestMatchers("/api/admin/settings/**", "/api/admin/system/**", "/api/admin/policies/**").hasAuthority("PERM_SETTINGS")
                .requestMatchers("/api/admin/analytics/**").hasAuthority("PERM_REPORTS")
                .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                .requestMatchers("/api/buyer/**", "/api/cart/**", "/api/orders/**", "/api/payments/**", "/api/vouchers/**", "/api/ai/**").hasAuthority("ROLE_BUYER")
                // /api/notifications/** and /api/support/** serve every role
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Spring's default resolver, plus: remember which frontend origin started
    // the login so the success/failure handlers can redirect back to it.
    private OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository, OAuth2FrontendRedirect frontendRedirect) {
        DefaultOAuth2AuthorizationRequestResolver defaultResolver =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return remember(request, defaultResolver.resolve(request));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return remember(request, defaultResolver.resolve(request, clientRegistrationId));
            }

            private OAuth2AuthorizationRequest remember(HttpServletRequest request, OAuth2AuthorizationRequest authRequest) {
                if (authRequest != null) {
                    frontendRedirect.rememberOrigin(request);
                }
                return authRequest;
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // patterns (not exact origins) so "http://localhost:*" matches any dev port
        configuration.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
