package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.LoginRequest;
import com.marketplace.backend.dto.request.RegisterRequest;
import com.marketplace.backend.dto.response.AuthResponse;
import com.marketplace.backend.entity.AuthProvider;
import com.marketplace.backend.entity.LoginEvent;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.UserSession;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.LoginEventRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Sign-up and sign-in. Protections: per-IP limits (LoginGuard), a short automatic lock after too many wrong
 * passwords / codes, two-step verification (TOTP or a backup code), one session per device with a rotating
 * refresh token, and a login history (new devices trigger an e-mail alert through SecurityEvent).
 * Failed attempts must be saved even though the request fails, hence noRollbackFor.
 */
@Service
@Transactional(noRollbackFor = ApiException.class)
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final SessionService sessionService;
    private final LoginGuard guard;
    private final LoginEventRepository loginEvents;
    private final PlatformConfig config;
    private final TotpService totp;
    private final SecretCipher cipher;
    private final ApplicationEventPublisher events;

    public AuthResponse register(RegisterRequest request) {
        RequestInfo info = RequestInfo.current();
        guard.checkRegister(info.ip());
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException("Email đã được sử dụng", HttpStatus.BAD_REQUEST);
        }
        PasswordPolicy.check(request.getPassword(), email);

        // Self-registration is always a BUYER account, like Shopee. Becoming a
        // seller is a separate step done later (see ShopService#becomeSeller).
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone())
                .role(Role.BUYER)
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .passwordChangedAt(LocalDateTime.now())
                .build();
        user = userRepository.save(user);
        events.publishEvent(new SecurityEvent(user.getId(), "REGISTERED", Map.of()));
        return complete(user, "PASSWORD", info, false);
    }

    public AuthResponse login(LoginRequest request) {
        RequestInfo info = RequestInfo.current();
        String email = request.getEmail().trim().toLowerCase();
        try {
            guard.checkLogin(info.ip());
        } catch (ApiException e) {
            record(null, email, LoginEvent.Result.RATE_LIMITED, "PASSWORD", info, false);
            throw e;
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            record(null, email, LoginEvent.Result.UNKNOWN_EMAIL, "PASSWORD", info, false);
            throw wrongCredentials();
        }
        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            throw new ApiException(
                    "Tài khoản này đăng nhập bằng Google, vui lòng chọn \"Đăng nhập với Google\"",
                    HttpStatus.BAD_REQUEST);
        }
        checkTempLock(user, info, "PASSWORD");

        // Spring Security checks the BCrypt hash (DaoAuthenticationProvider +
        // CustomUserDetailsService) and the enabled flag for us.
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (DisabledException e) {
            record(user, email, LoginEvent.Result.LOCKED, "PASSWORD", info, false);
            throw locked(user);
        } catch (BadCredentialsException e) {
            failed(user, LoginEvent.Result.BAD_PASSWORD, "PASSWORD", info);
            throw wrongCredentials();
        }
        return passwordOk(user, "PASSWORD", info);
    }

    /** Step 2 of a sign-in with two-step verification: the authenticator code or a backup code. */
    public AuthResponse loginTwoFactor(String challenge, String code) {
        RequestInfo info = RequestInfo.current();
        Long userId = jwtService.challengeUserId(challenge);
        if (userId == null) throw new ApiException("Phiên xác thực đã hết hạn, vui lòng đăng nhập lại", HttpStatus.UNAUTHORIZED);
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException("Không tìm thấy tài khoản", HttpStatus.UNAUTHORIZED));
        String method = jwtService.challengeMethod(challenge);
        guard.checkLogin(info.ip());
        checkTempLock(user, info, "TWO_FACTOR");
        if (!UserPrincipal.isActive(user, LocalDateTime.now())) throw locked(user);
        String used = verifySecondFactor(user, code);
        if (used == null) {
            failed(user, LoginEvent.Result.TWO_FACTOR_FAILED, "TWO_FACTOR", info);
            throw new ApiException("Mã xác thực không đúng", HttpStatus.UNAUTHORIZED);
        }
        user.setFailedLoginCount(0);
        return complete(user, "GOOGLE".equals(method) ? "GOOGLE" : used, info, false);
    }

    /**
     * Called by OAuth2LoginSuccessHandler once Spring Security has finished the
     * Google OAuth2 / OpenID Connect flow. Logs the matching user in, or
     * auto-creates a BUYER account on first Google sign-in (like Shopee's
     * "Dang nhap voi Google" on both the login and signup pages).
     */
    public AuthResponse loginWithOAuth2(String email, String name, boolean emailVerified) {
        if (email == null || !emailVerified) {
            throw new ApiException("Không thể xác thực email của tài khoản Google này", HttpStatus.UNAUTHORIZED);
        }
        RequestInfo info = RequestInfo.current();
        String normalizedEmail = email.trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail).orElseGet(() -> {
            User newUser = User.builder()
                    .email(normalizedEmail)
                    .fullName(name != null && !name.isBlank() ? name : normalizedEmail)
                    // password is unusable via /api/auth/login (random hash, never
                    // given to the user) since this account only signs in via Google
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(Role.BUYER)
                    .authProvider(AuthProvider.GOOGLE)
                    .enabled(true)
                    .emailVerified(true)
                    .build();
            return userRepository.save(newUser);
        });
        if (!user.isEmailVerified()) user.setEmailVerified(true);   // Google checked the address

        if (!UserPrincipal.isActive(user, LocalDateTime.now())) {
            record(user, normalizedEmail, LoginEvent.Result.LOCKED, "GOOGLE", info, false);
            throw locked(user);
        }
        return passwordOk(user, "GOOGLE", info);
    }

    /** A new access token for the session of the refresh token (which is replaced). */
    public AuthResponse refresh(String refreshToken) {
        Map.Entry<UserSession, String> r = sessionService.refresh(refreshToken);
        User user = userRepository.findById(r.getKey().getUserId()).orElseThrow(() -> new ApiException("Không tìm thấy tài khoản", HttpStatus.UNAUTHORIZED));
        if (!UserPrincipal.isActive(user, LocalDateTime.now())) {
            sessionService.revoke(r.getKey().getId(), "ACCOUNT_LOCKED");
            throw locked(user);
        }
        AuthResponse a = response(user, r.getKey().getId());
        a.setRefreshToken(r.getValue());
        return a;
    }

    /** A session of its own for another browser tab, from the refresh token of this sign-in (not rotated). */
    public AuthResponse fork(String refreshToken) {
        Map.Entry<UserSession, SessionService.Issued> r = sessionService.fork(refreshToken, RequestInfo.current());
        User user = userRepository.findById(r.getKey().getUserId()).orElseThrow(() -> new ApiException("Không tìm thấy tài khoản", HttpStatus.UNAUTHORIZED));
        if (!UserPrincipal.isActive(user, LocalDateTime.now())) {
            sessionService.revoke(r.getValue().sessionId(), "ACCOUNT_LOCKED");
            throw locked(user);
        }
        AuthResponse a = response(user, r.getValue().sessionId());
        a.setRefreshToken(r.getValue().refreshToken());
        return a;
    }

    /** Ends the session of a refresh token the caller holds (e.g. the tab seed), if it is right. */
    public void logoutRefresh(String refreshToken) {
        if (refreshToken != null) sessionService.revokeByRefresh(refreshToken, "LOGOUT");
    }

    /** Ends the session of this access token (no-op without one). */
    public void logout(String token) {
        if (token == null) return;
        try {
            String sid = jwtService.extractSessionId(token);
            if (sid != null) sessionService.revoke(sid, "LOGOUT");
        } catch (Exception ignored) {
            // an expired / broken token: nothing to end
        }
    }

    // Lets the frontend turn a bare JWT (e.g. the one handed back after the
    // Google redirect) into the full session info it keeps in the auth store.
    public AuthResponse me(Long userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("Không tìm thấy tài khoản", HttpStatus.UNAUTHORIZED));
        AuthResponse a = response(user, null);
        a.setToken(token);
        return a;
    }

    /** A fresh access token in the same session, after the account changed (e.g. it became a seller). */
    public AuthResponse reissue(User user, String sessionId) {
        return response(user, sessionId);
    }

    // ==================== helpers ====================

    private AuthResponse passwordOk(User user, String method, RequestInfo info) {
        user.setFailedLoginCount(0);
        if (user.isTotpEnabled()) {
            record(user, user.getEmail(), LoginEvent.Result.TWO_FACTOR_REQUIRED, method, info, false);
            return AuthResponse.challenge(jwtService.challengeToken(user.getEmail(), user.getId(), method), user.getEmail());
        }
        return complete(user, method, info, false);
    }

    private AuthResponse complete(User user, String method, RequestInfo info, boolean ignored) {
        SessionService.Issued s = sessionService.create(user, info);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLoginLockedUntil(null);
        // (an expired temporary lock is lifted by AdminUserService.liftExpiredLocks, which also re-opens the shop)
        record(user, user.getEmail(), LoginEvent.Result.SUCCESS, method, info, s.newDevice());
        // the very first session is not a "new device" worth an alert
        if (s.newDevice() && loginEvents.findTop30ByUserIdOrderByIdDesc(user.getId()).stream()
                .filter(e -> e.getResult() == LoginEvent.Result.SUCCESS).count() > 1) {
            events.publishEvent(new SecurityEvent(user.getId(), "NEW_DEVICE_LOGIN",
                    Map.of("device", info.deviceLabel(), "ip", info.ip(), "time", LocalDateTime.now())));
        }
        AuthResponse a = response(user, s.sessionId());
        a.setRefreshToken(s.refreshToken());
        return a;
    }

    private AuthResponse response(User user, String sessionId) {
        AuthResponse a = new AuthResponse(sessionId == null ? null : jwtService.generateToken(user.getEmail(), user.getId(), user.getRole().name(), sessionId),
                user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
        a.setExpiresInSeconds(jwtService.accessTokenMs() / 1000);
        a.setEmailVerified(user.isEmailVerified());
        a.setPhone(user.getPhone());
        a.setTwoFactorEnabled(user.isTotpEnabled());
        a.setTwoFactorSetupRequired(user.getRole() == Role.ADMIN && !user.isTotpEnabled() && config.requireAdmin2fa());
        return a;
    }

    // "TOTP" / "BACKUP_CODE" when the code is right (a backup code is used up), else null
    private String verifySecondFactor(User user, String code) {
        if (code == null) return null;
        String c = code.replaceAll("[\\s-]", "");
        if (c.matches("\\d{6}") && totp.verify(cipher.decrypt(user.getTotpSecret()), c)) return "TOTP";
        String hash = SessionService.sha(c.toUpperCase());
        String codes = user.getBackupCodes();
        if (codes != null && !codes.isBlank()) {
            java.util.List<String> left = new java.util.ArrayList<>(java.util.List.of(codes.split(",")));
            if (left.remove(hash)) {
                user.setBackupCodes(String.join(",", left));
                return "BACKUP_CODE";
            }
        }
        return null;
    }

    private void checkTempLock(User user, RequestInfo info, String method) {
        LocalDateTime until = user.getLoginLockedUntil();
        if (until != null && until.isAfter(LocalDateTime.now())) {
            record(user, user.getEmail(), LoginEvent.Result.TEMP_LOCKED, method, info, false);
            long min = Math.max(1, Duration.between(LocalDateTime.now(), until).toMinutes() + 1);
            throw new ApiException("Tài khoản tạm khóa đăng nhập do nhập sai nhiều lần. Vui lòng thử lại sau " + min
                    + " phút hoặc đặt lại mật khẩu.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    // a wrong password / code: count it, lock the account for a while at the limit
    private void failed(User user, LoginEvent.Result result, String method, RequestInfo info) {
        user.setFailedLoginCount(user.getFailedLoginCount() + 1);
        record(user, user.getEmail(), result, method, info, false);
        if (user.getFailedLoginCount() >= config.maxFailedLogins()) {
            user.setFailedLoginCount(0);
            user.setLoginLockedUntil(LocalDateTime.now().plusMinutes(config.lockMinutes()));
            events.publishEvent(new SecurityEvent(user.getId(), "ACCOUNT_TEMP_LOCKED",
                    Map.of("minutes", config.lockMinutes(), "ip", info.ip(), "device", info.deviceLabel())));
        }
    }

    private void record(User user, String email, LoginEvent.Result result, String method, RequestInfo info, boolean newDevice) {
        loginEvents.save(LoginEvent.builder().userId(user == null ? null : user.getId()).email(email == null ? null : email.length() > 120 ? email.substring(0, 120) : email)
                .result(result).method(method).ip(info.ip()).deviceLabel(info.deviceLabel()).newDevice(newDevice).createdAt(LocalDateTime.now()).build());
    }

    private static ApiException wrongCredentials() {
        return new ApiException("Sai email hoặc mật khẩu", HttpStatus.UNAUTHORIZED);
    }

    // the reason and the end of a temporary lock are shown on the login page
    private static ApiException locked(User user) {
        String msg = "Tài khoản đã bị khóa";
        if (user.getLockedReason() != null) msg += ": " + user.getLockedReason();
        if (user.getLockedUntil() != null) {
            msg += " (đến " + user.getLockedUntil().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")) + ")";
        }
        return new ApiException(msg + ". Liên hệ bộ phận hỗ trợ nếu bạn cần trợ giúp.", HttpStatus.FORBIDDEN);
    }
}
