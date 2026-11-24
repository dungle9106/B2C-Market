package com.marketplace.backend.service;

import com.marketplace.backend.entity.AuthProvider;
import com.marketplace.backend.entity.OAuthAccount;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OAuthAccountRepository;
import com.marketplace.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Linking Google / Facebook / GitHub accounts, and who may sign in with a password. */
class SocialAccountRulesTest {

    private final UserRepository users = mock(UserRepository.class);
    private final OAuthAccountRepository links = mock(OAuthAccountRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuthService auth = new AuthService(users, null, null, null, null, null, null, null, null, null, events, links);

    private static OAuthAccount link(long userId, AuthProvider p, String subject) {
        return OAuthAccount.builder().userId(userId).provider(p).subject(subject).build();
    }

    @Test
    void linkNewAccount() {
        when(links.findByProviderAndSubject(AuthProvider.GITHUB, "777")).thenReturn(Optional.empty());
        when(links.findByUserIdAndProvider(5L, AuthProvider.GITHUB)).thenReturn(Optional.empty());
        auth.linkOAuth(5L, AuthProvider.GITHUB, "777", "dev@example.com", "Dev");
        verify(links).save(argThat(a -> a.getUserId() == 5L && a.getSubject().equals("777") && a.getProvider() == AuthProvider.GITHUB));
        verify(events).publishEvent(any(SecurityEvent.class));
    }

    @Test
    void sameAccountTwiceIsANoOp() {
        when(links.findByProviderAndSubject(AuthProvider.GOOGLE, "g1")).thenReturn(Optional.of(link(5L, AuthProvider.GOOGLE, "g1")));
        auth.linkOAuth(5L, AuthProvider.GOOGLE, "g1", "a@gmail.com", "A");
        verify(links, never()).save(any());
    }

    @Test
    void providerAccountOfSomeoneElse() {
        when(links.findByProviderAndSubject(AuthProvider.GOOGLE, "g1")).thenReturn(Optional.of(link(9L, AuthProvider.GOOGLE, "g1")));
        ApiException e = assertThrows(ApiException.class, () -> auth.linkOAuth(5L, AuthProvider.GOOGLE, "g1", "a@gmail.com", "A"));
        assertTrue(e.getMessage().contains("tài khoản Shoppi khác"));
    }

    @Test
    void onlyOneAccountPerProvider() {
        when(links.findByProviderAndSubject(AuthProvider.FACEBOOK, "f2")).thenReturn(Optional.empty());
        when(links.findByUserIdAndProvider(5L, AuthProvider.FACEBOOK)).thenReturn(Optional.of(link(5L, AuthProvider.FACEBOOK, "f1")));
        ApiException e = assertThrows(ApiException.class, () -> auth.linkOAuth(5L, AuthProvider.FACEBOOK, "f2", null, "B"));
        assertTrue(e.getMessage().contains("gỡ liên kết trước"));
    }

    @Test
    void unverifiedEmailCannotCreateOrClaimAnAccount() {
        when(links.findByProviderAndSubject(any(), any())).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> auth.loginWithOAuth2(AuthProvider.GITHUB, "777", null, "Dev", false));
        assertThrows(ApiException.class, () -> auth.loginWithOAuth2(AuthProvider.FACEBOOK, "f1", "victim@shoppi.vn", "X", false),
                "an address the provider did not verify must not take over the Shoppi account using it");
        verify(users, never()).findByEmail(any());
    }

    @Test
    void whoUsesAPassword() {
        assertTrue(User.builder().authProvider(AuthProvider.LOCAL).build().usesPassword());
        assertFalse(User.builder().authProvider(AuthProvider.GOOGLE).build().usesPassword(), "older Google accounts: no password");
        assertFalse(User.builder().authProvider(AuthProvider.GITHUB).passwordSet(false).build().usesPassword());
        assertTrue(User.builder().authProvider(AuthProvider.FACEBOOK).passwordSet(true).build().usesPassword(), "created one with Quen mat khau");
    }
}
