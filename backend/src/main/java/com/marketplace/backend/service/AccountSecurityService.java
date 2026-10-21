package com.marketplace.backend.service;

import com.marketplace.backend.entity.AuthProvider;
import com.marketplace.backend.entity.LoginEvent;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.LoginEventRepository;
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

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        User u = me();
        return AdminUserService.map("email", u.getEmail(), "emailVerified", u.isEmailVerified(), "provider", u.getAuthProvider().name(),
                "passwordChangedAt", u.getPasswordChangedAt(), "twoFactorEnabled", u.isTotpEnabled(),
                "backupCodesLeft", u.getBackupCodes() == null || u.getBackupCodes().isBlank() ? 0 : u.getBackupCodes().split(",").length,
                "sessions", sessionService.list(u.getId(), CurrentUser.sessionId()),
                "logins", loginEvents.findTop30ByUserIdOrderByIdDesc(u.getId()).stream().map(AccountSecurityService::loginView).toList());
    }

    /** The other devices are signed out; this one stays signed in. */
    public Map<String, Object> changePassword(String current, String next) {
        User u = me();
        if (u.getAuthProvider() == AuthProvider.GOOGLE) throw bad("Tài khoản đăng nhập bằng Google không dùng mật khẩu Shoppi");
        if (current == null || !passwordEncoder.matches(current, u.getPassword())) throw bad("Mật khẩu hiện tại không đúng");
        PasswordPolicy.check(next, u.getEmail());
        if (passwordEncoder.matches(next, u.getPassword())) throw bad("Mật khẩu mới phải khác mật khẩu hiện tại");
        u.setPassword(passwordEncoder.encode(next));
        u.setPasswordChangedAt(LocalDateTime.now());
        int ended = sessionService.revokeAll(u.getId(), CurrentUser.sessionId(), "PASSWORD_CHANGED");
        events.publishEvent(new SecurityEvent(u.getId(), "PASSWORD_CHANGED", Map.of("device", RequestInfo.current().deviceLabel())));
        return AdminUserService.map("signedOutDevices", ended);
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
        if (u.getAuthProvider() == AuthProvider.LOCAL && (password == null || !passwordEncoder.matches(password, u.getPassword()))) {
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
