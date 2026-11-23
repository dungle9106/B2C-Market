package com.marketplace.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Where a social sign-in returns to: only trusted web origins and the apps' own URL schemes get the token. */
class OAuth2FrontendRedirectTest {

    private OAuth2FrontendRedirect redirect;

    @BeforeEach
    void setUp() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("http://localhost:*"));
        CorsConfigurationSource source = mock(CorsConfigurationSource.class);
        when(source.getCorsConfiguration(any())).thenReturn(cors);
        redirect = new OAuth2FrontendRedirect(source);
        ReflectionTestUtils.setField(redirect, "fallbackRedirectUri", "http://localhost:5173/oauth2/redirect");
        ReflectionTestUtils.setField(redirect, "appSchemes", "shoppi,shoppiseller,shoppiadmin");
    }

    @Test
    void appSchemesOnly() {
        assertTrue(redirect.appRedirectAllowed("shoppi://oauth2/redirect"));
        assertTrue(redirect.appRedirectAllowed("ShoppiAdmin://oauth2/redirect"));
        assertFalse(redirect.appRedirectAllowed("https://evil.example.com/steal"), "a web page is not an app");
        assertFalse(redirect.appRedirectAllowed("exp://192.168.1.5:8081/--/oauth2/redirect"), "Expo Go only when configured");
        assertFalse(redirect.appRedirectAllowed("javascript:alert(1)"));
        assertFalse(redirect.appRedirectAllowed("shoppi:/no-slashes"));
    }

    @Test
    void appGetsTheResultAfterTheHash() {
        MockHttpServletRequest start = new MockHttpServletRequest();
        start.setParameter("app_redirect", "shoppi://oauth2/redirect");
        redirect.rememberOrigin(start);
        MockHttpServletRequest callback = new MockHttpServletRequest();
        callback.setSession(start.getSession());
        assertEquals("shoppi://oauth2/redirect#token=abc&refresh=x%20y", redirect.buildUrl(callback, "token=abc&refresh=x y"));
    }

    @Test
    void untrustedOriginFallsBackToTheConfiguredPage() {
        MockHttpServletRequest start = new MockHttpServletRequest();
        start.setParameter("redirect_origin", "https://evil.example.com");
        start.setParameter("app_redirect", "https://evil.example.com/app");
        redirect.rememberOrigin(start);
        MockHttpServletRequest callback = new MockHttpServletRequest();
        callback.setSession(start.getSession());
        assertEquals("http://localhost:5173/oauth2/redirect#error=x", redirect.buildUrl(callback, "error=x"));

        MockHttpServletRequest web = new MockHttpServletRequest();
        web.setParameter("redirect_origin", "http://localhost:5174");
        redirect.rememberOrigin(web);
        MockHttpServletRequest back = new MockHttpServletRequest();
        back.setSession(web.getSession());
        assertEquals("http://localhost:5174/oauth2/redirect#error=x", redirect.buildUrl(back, "error=x"));
    }

    @Test
    void linkTokenWorksOnce() {
        String token = redirect.issueLinkToken(42L);
        MockHttpServletRequest first = new MockHttpServletRequest();
        first.setParameter("link", token);
        redirect.rememberOrigin(first);
        assertEquals(42L, redirect.linkUserId(first));

        MockHttpServletRequest again = new MockHttpServletRequest();
        again.setParameter("link", token);
        redirect.rememberOrigin(again);
        assertNull(redirect.linkUserId(again), "a used link token signs in normally instead of linking");

        MockHttpServletRequest forged = new MockHttpServletRequest();
        forged.setParameter("link", "made-up");
        redirect.rememberOrigin(forged);
        assertNull(redirect.linkUserId(forged));
    }
}
