package com.marketplace.backend.controller;

import com.marketplace.backend.dto.request.LoginRequest;
import com.marketplace.backend.dto.request.RegisterRequest;
import com.marketplace.backend.dto.response.AuthResponse;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.service.AccountSecurityService;
import com.marketplace.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Email/password auth lives here. "Dang nhap voi Google" does NOT go through
// this controller: the browser is sent to /oauth2/authorization/google and
// Spring Security's oauth2Login() handles it (see SecurityConfig and
// OAuth2LoginSuccessHandler).
// /api/account/** is the signed-in user's own security page (every role).
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccountSecurityService security;
    private final com.marketplace.backend.service.EmailAccountService emailAccount;

    @PostMapping("/api/auth/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    // a session, or { twoFactorRequired, challenge } when the account uses two-step verification
    @PostMapping("/api/auth/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // body: { challenge, code } - the authenticator code or a backup code
    @PostMapping("/api/auth/login/2fa")
    public ResponseEntity<AuthResponse> loginTwoFactor(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.loginTwoFactor(body.get("challenge"), body.get("code")));
    }

    // body: { refreshToken } -> new access token + new refresh token
    @PostMapping("/api/auth/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.refresh(body.get("refreshToken")));
    }

    // body: { refreshToken } -> a separate session for another browser tab (the given token is not rotated)
    @PostMapping("/api/auth/session/fork")
    public ResponseEntity<AuthResponse> fork(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.fork(body.get("refreshToken")));
    }

    // ends this tab's session; body { refreshToken } (optional) also ends that session, e.g. the seed for new tabs
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                       @RequestBody(required = false) Map<String, String> body) {
        authService.logout(authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null);
        if (body != null) authService.logoutRefresh(body.get("refreshToken"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<AuthResponse> me(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(authService.me(CurrentUser.id(), authHeader.substring(7)));
    }

    // ==================== e-mail links ====================

    // body: { token } - the link of the verification e-mail
    @PostMapping("/api/auth/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmail(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(emailAccount.verify(body.get("token")));
    }

    // body: { email } - always 204, whether or not the e-mail has an account
    @PostMapping("/api/auth/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody Map<String, String> body) {
        emailAccount.forgotPassword(body.get("email"));
        return ResponseEntity.noContent().build();
    }

    // body: { token, newPassword }
    @PostMapping("/api/auth/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody Map<String, String> body) {
        emailAccount.resetPassword(body.get("token"), body.get("newPassword"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/account/verify-email/send")
    public ResponseEntity<Map<String, Object>> sendVerification() {
        return ResponseEntity.ok(emailAccount.sendVerification());
    }

    // ==================== account security ====================

    @GetMapping("/api/account/security")
    public ResponseEntity<Map<String, Object>> security() {
        return ResponseEntity.ok(security.overview());
    }

    // body: { currentPassword, newPassword }
    @PostMapping("/api/account/password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(security.changePassword(body.get("currentPassword"), body.get("newPassword")));
    }

    @PostMapping("/api/account/2fa/setup")
    public ResponseEntity<Map<String, Object>> setupTwoFactor() {
        return ResponseEntity.ok(security.setupTwoFactor());
    }

    // body: { code }
    @PostMapping("/api/account/2fa/enable")
    public ResponseEntity<Map<String, Object>> enableTwoFactor(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(security.enableTwoFactor(body.get("code")));
    }

    // body: { password, code }
    @PostMapping("/api/account/2fa/disable")
    public ResponseEntity<Void> disableTwoFactor(@RequestBody Map<String, String> body) {
        security.disableTwoFactor(body.get("password"), body.get("code"));
        return ResponseEntity.noContent().build();
    }

    // body: { code }
    @PostMapping("/api/account/2fa/backup-codes")
    public ResponseEntity<Map<String, Object>> backupCodes(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(security.regenerateBackupCodes(body.get("code")));
    }

    @DeleteMapping("/api/account/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@PathVariable String id) {
        security.revokeSession(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/account/sessions/revoke-others")
    public ResponseEntity<Map<String, Object>> revokeOthers() {
        return ResponseEntity.ok(security.revokeOtherSessions());
    }
}
