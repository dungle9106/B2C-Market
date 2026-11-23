package com.marketplace.backend.entity;

// how the account was first created; later sign-ins can use any linked provider (OAuthAccount)
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    FACEBOOK,
    GITHUB
}
