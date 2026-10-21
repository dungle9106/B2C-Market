package com.marketplace.backend.service;

import com.marketplace.backend.entity.AuthProvider;
import com.marketplace.backend.entity.EmailToken;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.EmailTokenRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.security.PasswordPolicy;
import com.marketplace.backend.security.RequestInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

/**
 * E-mail links: "xac thuc email" (24 hours) and "quen mat khau" (30 minutes, one use). The token in the link
 * is random; only its SHA-256 is stored. A reset signs every device out and lifts a temporary sign-in lock.
 */
@Service
@Transactional(noRollbackFor = ApiException.class)
@RequiredArgsConstructor
public class EmailAccountService {

    private final SecureRandom random = new SecureRandom();

    private final UserRepository userRepository;
    private final EmailTokenRepository tokens;
    private final EmailService email;
    private final SessionService sessions;
    private final LoginGuard guard;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /** Sends (again) the verification link to the signed-in user. */
    public Map<String, Object> sendVerification() {
        User u = userRepository.findById(CurrentUser.id()).orElseThrow();
        if (u.isEmailVerified()) throw new ApiException("Email đã được xác thực", HttpStatus.BAD_REQUEST);
        sendVerification(u);
        return AdminUserService.map("sentTo", u.getEmail());
    }

    void sendVerification(User u) {
        String token = issue(u.getId(), EmailToken.Purpose.VERIFY_EMAIL, 24 * 60);
        EmailTemplates.Mail m = EmailTemplates.verifyEmail(u.getFullName(), frontendUrl + "/verify-email?token=" + token);
        email.queue(u.getEmail(), u.getId(), "VERIFY_EMAIL", m.subject(), m.html());
    }

    public Map<String, Object> verify(String token) {
        EmailToken t = use(token, EmailToken.Purpose.VERIFY_EMAIL);
        User u = userRepository.findById(t.getUserId()).orElseThrow();
        u.setEmailVerified(true);
        return AdminUserService.map("email", u.getEmail(), "verified", true);
    }

    /** Always answers the same way, so it can't be used to find out which e-mails have an account. */
    public void forgotPassword(String address) {
        RequestInfo info = RequestInfo.current();
        guard.checkPasswordReset(info.ip());
        if (address == null) return;
        userRepository.findByEmail(address.trim().toLowerCase()).filter(u -> u.getAuthProvider() == AuthProvider.LOCAL && u.getDeletedAt() == null)
                .ifPresent(u -> {
                    String token = issue(u.getId(), EmailToken.Purpose.RESET_PASSWORD, 30);
                    EmailTemplates.Mail m = EmailTemplates.resetPassword(u.getFullName(), frontendUrl + "/reset-password?token=" + token, info.ip());
                    email.queue(u.getEmail(), u.getId(), "RESET_PASSWORD", m.subject(), m.html());
                });
    }

    public void resetPassword(String token, String newPassword) {
        EmailToken t = find(token, EmailToken.Purpose.RESET_PASSWORD);
        User u = userRepository.findById(t.getUserId()).orElseThrow();
        PasswordPolicy.check(newPassword, u.getEmail());
        t.setUsedAt(LocalDateTime.now());
        u.setPassword(passwordEncoder.encode(newPassword));
        u.setPasswordChangedAt(LocalDateTime.now());
        u.setFailedLoginCount(0);
        u.setLoginLockedUntil(null);
        u.setEmailVerified(true);   // they proved they read this mailbox
        sessions.revokeAll(u.getId(), null, "PASSWORD_RESET");
        events.publishEvent(new SecurityEvent(u.getId(), "PASSWORD_RESET", Map.of("device", RequestInfo.current().deviceLabel())));
    }

    // ==================== helpers ====================

    private String issue(Long userId, EmailToken.Purpose purpose, int minutes) {
        tokens.expireAll(userId, purpose, LocalDateTime.now());
        byte[] b = new byte[32];
        random.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        tokens.save(EmailToken.builder().userId(userId).purpose(purpose).tokenHash(SessionService.sha(token))
                .expiresAt(LocalDateTime.now().plusMinutes(minutes)).createdAt(LocalDateTime.now()).build());
        return token;
    }

    private EmailToken find(String token, EmailToken.Purpose purpose) {
        EmailToken t = token == null ? null : tokens.findByTokenHash(SessionService.sha(token)).orElse(null);
        if (t == null || t.getPurpose() != purpose) throw new ApiException("Liên kết không hợp lệ", HttpStatus.BAD_REQUEST);
        if (t.getUsedAt() != null) throw new ApiException("Liên kết đã được sử dụng hoặc đã có liên kết mới hơn", HttpStatus.BAD_REQUEST);
        if (t.getExpiresAt().isBefore(LocalDateTime.now())) throw new ApiException("Liên kết đã hết hạn, vui lòng yêu cầu lại", HttpStatus.BAD_REQUEST);
        return t;
    }

    private EmailToken use(String token, EmailToken.Purpose purpose) {
        EmailToken t = find(token, purpose);
        t.setUsedAt(LocalDateTime.now());
        return t;
    }
}
