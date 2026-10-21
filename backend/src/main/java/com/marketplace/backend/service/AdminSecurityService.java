package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.LoginEvent;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.LoginEventRepository;
import com.marketplace.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** "Giam sat dang nhap": sign-in results, addresses with many failures, accounts locked for wrong passwords. */
@Service
@Transactional
@RequiredArgsConstructor
public class AdminSecurityService {

    private final LoginEventRepository events;
    private final UserRepository users;
    private final PlatformConfig config;
    private final AdminAuditService auditService;

    @Transactional(readOnly = true)
    public Map<String, Object> overview(int hours) {
        int h = Math.min(Math.max(hours, 1), 24 * 30);
        LocalDateTime since = LocalDateTime.now().minusHours(h);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LoginEvent.Result r : LoginEvent.Result.values()) counts.put(r.name(), 0L);
        events.countsSince(since).forEach(r -> counts.put(((LoginEvent.Result) r[0]).name(), ((Number) r[1]).longValue()));
        long failed = counts.entrySet().stream().filter(e -> LoginGuard.FAILURES.stream().anyMatch(f -> f.name().equals(e.getKey())))
                .mapToLong(Map.Entry::getValue).sum();
        return AdminUserService.map("hours", h, "counts", counts, "failed", failed, "success", counts.get("SUCCESS"),
                "topIps", events.failuresByIp(since, PageRequest.of(0, 10)).stream().map(r -> AdminUserService.map("ip", r[0],
                        "failures", ((Number) r[1]).longValue(), "accounts", ((Number) r[2]).longValue(),
                        "blocked", ((Number) r[1]).longValue() >= config.ipFailLimit())).toList(),
                "lockedAccounts", users.findByLoginLockedUntilAfter(LocalDateTime.now()).stream().map(u -> AdminUserService.map("id", u.getId(),
                        "email", u.getEmail(), "fullName", u.getFullName(), "until", u.getLoginLockedUntil())).toList(),
                "rules", AdminUserService.map("maxFailedLogins", config.maxFailedLogins(), "lockMinutes", config.lockMinutes(),
                        "ipFailLimit", config.ipFailLimit(), "registerPerIp", config.registerPerIp(), "requireAdmin2fa", config.requireAdmin2fa()));
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> logins(boolean failedOnly, int page, int size) {
        return PageResponse.of(events.recent(LocalDateTime.now().minusDays(30), failedOnly,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id").descending())), AccountSecurityService::loginView);
    }

    public void unlock(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> new ApiException("Không tìm thấy tài khoản", HttpStatus.NOT_FOUND));
        u.setLoginLockedUntil(null);
        u.setFailedLoginCount(0);
        auditService.log("UNLOCK_LOGIN", "USER", u.getId(), u.getEmail(), "Mở khóa đăng nhập (khóa tạm do nhập sai)");
    }
}
