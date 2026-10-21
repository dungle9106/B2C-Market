package com.marketplace.backend.service;

import com.marketplace.backend.entity.LoginEvent;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.LoginEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-address limits against password guessing and mass sign-ups:
 *  - failed sign-ins of an IP in the last 15 minutes (from the login history);
 *  - new accounts of an IP in the last 10 minutes, and password-reset requests (kept in memory).
 * Over the limit the request is refused with HTTP 429.
 */
@Component
@RequiredArgsConstructor
public class LoginGuard {

    static final EnumSet<LoginEvent.Result> FAILURES = EnumSet.of(LoginEvent.Result.BAD_PASSWORD, LoginEvent.Result.UNKNOWN_EMAIL,
            LoginEvent.Result.TWO_FACTOR_FAILED);
    private static final int RESET_PER_IP = 5;    // password-reset e-mails per IP per 15 minutes

    private final LoginEventRepository events;
    private final PlatformConfig config;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /** Refuses when this address already failed too often. */
    public void checkLogin(String ip) {
        long failed = events.countByIpSince(ip, LocalDateTime.now().minusMinutes(15), FAILURES);
        if (failed >= config.ipFailLimit()) {
            throw new ApiException("Có quá nhiều lần đăng nhập sai từ địa chỉ của bạn. Vui lòng thử lại sau 15 phút.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    public void checkRegister(String ip) {
        hit("register:" + ip, config.registerPerIp(), 10, "Đã tạo quá nhiều tài khoản từ địa chỉ của bạn. Vui lòng thử lại sau.");
    }

    public void checkPasswordReset(String ip) {
        hit("reset:" + ip, RESET_PER_IP, 15, "Bạn đã yêu cầu đặt lại mật khẩu quá nhiều lần. Vui lòng thử lại sau 15 phút.");
    }

    // sliding window kept in memory
    private void hit(String key, int limit, int minutes, String message) {
        long now = System.currentTimeMillis(), from = now - minutes * 60_000L;
        Deque<Long> q = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < from) q.pollFirst();
            if (q.size() >= limit) throw new ApiException(message, HttpStatus.TOO_MANY_REQUESTS);
            q.addLast(now);
        }
        if (windows.size() > 50_000) windows.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
