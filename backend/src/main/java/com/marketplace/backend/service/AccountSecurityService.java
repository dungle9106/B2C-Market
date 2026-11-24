package com.marketplace.backend.service;

import com.marketplace.backend.entity.AuthProvider;
import com.marketplace.backend.entity.LoginEvent;
import com.marketplace.backend.entity.OAuthAccount;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.LoginEventRepository;
import com.marketplace.backend.repository.OAuthAccountRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * "Bao mat tai khoan" (every role): change the password, two-step verification (set up, turn off, backup
 * codes), signed-in devices (sign one or all others out) and the recent sign-ins.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AccountSecurityService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private final UserRepository userRepository;
    private final LoginEventRepository loginEvents;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totp;
    private final SecretCipher cipher;
    private final ApplicationEventPublisher events;
    private final OAuthAccountRepository oauthAccounts;
    private final SocialLoginSettings socialLogin;
    private final OAuth2FrontendRedirect frontendRedirect;

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        User u = me();
        return AdminUserService.map("email", u.getEmail(), "emailVerified", u.isEmailVerified(), "provider", u.getAuthProvider().name(),
                "hasPassword", u.usesPassword(), "linkedAccounts", linkedAccounts(u),
                "passwordChangedAt", u.getPasswordChangedAt(), "twoFactorEnabled", u.isTotpEnabled(),
                "backupCodesLeft", u.getBackupCodes() == null || u.getBackupCodes().isBlank() ? 0 : u.getBackupCodes().split(",").length,
                "sessions", sessionService.list(u.getId(), CurrentUser.sessionId()),
                "logins", loginEvents.findTop30ByUserIdOrderByIdDesc(u.getId()).stream().map(AccountSecurityService::loginView).toList());
    }

    /** The other devices are signed out; this one stays signed in. */
    public Map<String, Object> changePassword(String current, String next) {
        User u = me();
        // an account created by a social sign-in sets its first password without a current one
        boolean first = !u.usesPassword();
        if (!first && (current == null || !passwordEncoder.matches(current, u.getPassword()))) throw bad("Mật khẩu hiện tại không đúng");
        PasswordPolicy.check(next, u.getEmail());
        if (!first && passwordEncoder.matches(next, u.getPassword())) throw bad("Mật khẩu mới phải khác mật khẩu hiện tại");
        u.setPassword(passwordEncoder.encode(next));
        u.setPasswordSet(true);
        u.setPasswordChangedAt(LocalDateTime.now());
        int ended = sessionService.revokeAll(u.getId(), CurrentUser.sessionId(), "PASSWORD_CHANGED");
        events.publishEvent(new SecurityEvent(u.getId(), "PASSWORD_CHANGED", Map.of("device", RequestInfo.current().deviceLabel())));
        return AdminUserService.map("signedOutDevices", ended);
    }

    // ==================== linked accounts (Google / Facebook / GitHub) ====================

    private List<Map<String, Object>> linkedAccounts(User u) {
        Map<AuthProvider, OAuthAccount> mine = new EnumMap<>(AuthProvider.class);
        oauthAccounts.findByUserIdOrderByIdAsc(u.getId()).forEach(a -> mine.put(a.getProvider(), a));
        List<Map<String, Object>> list = new ArrayList<>();
        for (SocialLoginSettings.Provider p : SocialLoginSettings.PROVIDERS) {
            AuthProvider ap = AuthProvider.valueOf(p.id().toUpperCase());
            OAuthAccount a = mine.get(ap);
            // a provider that is not set up is only listed when the account is already linked to it
            if (a == null && !socialLogin.ready(p.id())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("name", p.name());
            m.put("available", socialLogin.ready(p.id()));
            m.put("linked", a != null);
            if (a != null) {
                m.put("email", a.getEmail());
                m.put("displayName", a.getName());
                m.put("linkedAt", a.getCreatedAt());
                m.put("lastUsedAt", a.getLastUsedAt());
            }
            list.add(m);
        }
        return list;
    }

    /** A one-time token (5 minutes) the page passes to /oauth2/authorization/{provider}?link=... */
    public Map<String, Object> linkToken() {
        return AdminUserService.map("token", frontendRedirect.issueLinkToken(me().getId()), "startBase", socialLogin.publicUrl());
    }

    /** Gỡ liên kết: never the last way to sign in. */
    public Map<String, Object> unlink(String provider) {
        User u = me();
        SocialLoginSettings.Provider p = SocialLoginSettings.provider(provider);
        if (p == null) throw bad("Không hỗ trợ " + provider);
        AuthProvider ap = AuthProvider.valueOf(p.id().toUpperCase());
        OAuthAccount a = oauthAccounts.findByUserIdAndProvider(u.getId(), ap).orElseThrow(() -> bad("Tài khoản chưa liên kết với " + p.name()));
        long links = oauthAccounts.findByUserIdOrderByIdAsc(u.getId()).size();
        if (!u.usesPassword() && links <= 1) {
            throw bad("Đây là cách đăng nhập duy nhất của bạn. Hãy tạo mật khẩu Shoppi trước khi gỡ liên kết " + p.name());
        }
        oauthAccounts.delete(a);
        events.publishEvent(new SecurityEvent(u.getId(), "SOCIAL_UNLINKED", Map.of("device", p.name())));
        return overview();
    }

    // ==================== two-step verification ====================

    /** A new secret (not active until confirmed with a code). */
    public Map<String, Object> setupTwoFactor() {
        User u = me();
        if (u.isTotpEnabled()) throw bad("Xác thực 2 lớp đang bật");
        String secret = totp.newSecret();
        u.setTotpPendingSecret(cipher.encrypt(secret));
        return AdminUserService.map("secret", secret, "uri", totp.uri(secret, u.getEmail()));
    }

    /** Confirms the secret with a code from the app; returns the backup codes (shown once). */
    public Map<String, Object> enableTwoFactor(String code) {
        User u = me();
        if (u.isTotpEnabled()) throw bad("Xác thực 2 lớp đang bật");
        String pending = cipher.decrypt(u.getTotpPendingSecret());
        if (pending == null) throw bad("Vui lòng bắt đầu cài đặt lại");
        if (!totp.verify(pending, code)) throw bad("Mã xác thực không đúng, hãy kiểm tra giờ trên điện thoại");
        u.setTotpSecret(u.getTotpPendingSecret());
        u.setTotpPendingSecret(null);
        u.setTotpEnabled(true);
        List<String> codes = newBackupCodes(u);
        // a stolen session from before must not survive the upgrade
        sessionService.revokeAll(u.getId(), CurrentUser.sessionId(), "TWO_FACTOR_ENABLED");
        events.publishEvent(new SecurityEvent(u.getId(), "TWO_FACTOR_ENABLED", Map.of()));
        return AdminUserService.map("backupCodes", codes);
    }

    /** Needs the password (local accounts) and a current code. */
    public void disableTwoFactor(String password, String code) {
        User u = me();
        if (!u.isTotpEnabled()) throw bad("Xác thực 2 lớp đang tắt");
        if (u.usesPassword() && (password == null || !passwordEncoder.matches(password, u.getPassword()))) {
            throw bad("Mật khẩu không đúng");
        }
        if (!totp.verify(cipher.decrypt(u.getTotpSecret()), code)) throw bad("Mã xác thực không đúng");
        u.setTotpEnabled(false);
        u.setTotpSecret(null);
        u.setBackupCodes(null);
        events.publishEvent(new SecurityEvent(u.getId(), "TWO_FACTOR_DISABLED", Map.of("device", RequestInfo.current().deviceLabel())));
    }

    public Map<String, Object> regenerateBackupCodes(String code) {
        User u = me();
        if (!u.isTotpEnabled()) throw bad("Hãy bật xác thực 2 lớp trước");
        if (!totp.verify(cipher.decrypt(u.getTotpSecret()), code)) throw bad("Mã xác thực không đúng");
        return AdminUserService.map("backupCodes", newBackupCodes(u));
    }

    // 8 codes "XXXX-XXXX"; only their hashes are kept
    private List<String> newBackupCodes(User u) {
        List<String> codes = new ArrayList<>(), hashes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 8; j++) sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            String c = sb.substring(0, 4) + "-" + sb.substring(4);
            codes.add(c);
            hashes.add(SessionService.sha(sb.toString()));
        }
        u.setBackupCodes(String.join(",", hashes));
        return codes;
    }

    // ==================== devices ====================

    public void revokeSession(String sid) {
        User u = me();
        var s = sessionService.find(sid).filter(x -> x.getUserId().equals(u.getId()))
                .orElseThrow(() -> new ApiException("Không tìm thấy phiên đăng nhập", HttpStatus.NOT_FOUND));
        sessionService.revoke(s.getId(), "SIGNED_OUT_BY_USER");
    }

    public Map<String, Object> revokeOtherSessions() {
        return AdminUserService.map("signedOut", sessionService.revokeAll(me().getId(), CurrentUser.sessionId(), "LOGOUT_OTHERS"));
    }

    // ==================== helpers ====================

    static Map<String, Object> loginView(LoginEvent e) {
        return AdminUserService.map("id", e.getId(), "time", e.getCreatedAt(), "result", e.getResult().name(), "method", e.getMethod(),
                "ip", e.getIp(), "device", e.getDeviceLabel(), "newDevice", e.isNewDevice(), "email", e.getEmail(), "userId", e.getUserId());
    }

    private User me() {
        return userRepository.findById(CurrentUser.id()).orElseThrow(() -> new ApiException("Không tìm thấy tài khoản", HttpStatus.UNAUTHORIZED));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
