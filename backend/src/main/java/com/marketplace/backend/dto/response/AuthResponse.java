package com.marketplace.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A signed-in session (token + refreshToken), or - when the account uses two-step verification -
 * twoFactorRequired with a short-lived challenge to send back with the code.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long userId;
    private String email;
    private String fullName;
    private String role;
    private String phone;   // pre-fills the delivery address form

    private String refreshToken;
    private long expiresInSeconds;
    private boolean emailVerified;
    private boolean twoFactorEnabled;
    private boolean twoFactorSetupRequired;   // an admin who must turn on 2FA before using the console

    private boolean twoFactorRequired;        // sign-in step 2: ask for the authenticator code
    private String challenge;

    public AuthResponse(String token, Long userId, String email, String fullName, String role) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    public static AuthResponse challenge(String challenge, String email) {
        AuthResponse r = new AuthResponse();
        r.twoFactorRequired = true;
        r.challenge = challenge;
        r.email = email;
        return r;
    }
}
