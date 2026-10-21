package com.marketplace.backend.service;

import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.UserSession;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.UserSessionRepository;
import com.marketplace.backend.security.RequestInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Signed-in devices. Refresh token = "&lt;session id&gt;.&lt;random&gt;", stored as a SHA-256 hash and replaced on
 * every refresh; presenting an old refresh token again means it was copied, so the session is revoked.
 * The JWT filter asks isActive(sid) on each request (cached for a few seconds; revoking clears the cache).
 */
@Service
// a reused refresh token revokes the session and then fails the request: the revocation must still be saved
@Transactional(noRollbackFor = ApiException.class)
@RequiredArgsConstructor
public class SessionService {

    private static final long CACHE_MS = 15_000;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, long[]> cache = new ConcurrentHashMap<>();   // sid -> [active 1/0, checked at]

    private final UserSessionRepository repository;

    @Value("${app.jwt.refresh-days:14}")
    private int refreshDays;

    public record Issued(String sessionId, String refreshToken, boolean newDevice) {
    }

    public Issued create(User user, RequestInfo info) {
        boolean newDevice = !repository.existsByUserIdAndDeviceKey(user.getId(), info.deviceKey());
        String sid = UUID.randomUUID().toString();
        String secret = randomToken();
        LocalDateTime now = LocalDateTime.now();
        repository.save(UserSession.builder().id(sid).userId(user.getId()).refreshHash(sha(secret)).deviceKey(info.deviceKey())
                .deviceLabel(info.deviceLabel()).ip(info.ip()).userAgent(info.userAgent()).createdAt(now).lastUsedAt(now)
                .expiresAt(now.plusDays(refreshDays)).build());
        cache.put(sid, new long[]{1, System.currentTimeMillis()});
        return new Issued(sid, sid + "." + secret, newDevice);
    }

    // browser tabs each hold their own session: at most this many live sessions per user and device
    static final int MAX_PER_DEVICE = 20;

    /**
     * A new session for another browser tab of the same sign-in: the given refresh token is checked but NOT rotated
     * (the tab that owns it keeps using it), and the new session ends when that sign-in ends - forking never extends
     * a login. A wrong or stale token is just refused (no reuse alarm: a duplicated tab may hold an old copy).
     */
    public Map.Entry<UserSession, Issued> fork(String refreshToken, RequestInfo info) {
        String[] parts = refreshToken == null ? new String[0] : refreshToken.split("\\.", 2);
        UserSession parent = parts.length == 2 ? repository.findById(parts[0]).orElse(null) : null;
        LocalDateTime now = LocalDateTime.now();
        if (parent == null || !parent.active(now)
                || !MessageDigest.isEqual(parent.getRefreshHash().getBytes(StandardCharsets.UTF_8), sha(parts[1]).getBytes(StandardCharsets.UTF_8))) {
            throw expired();
        }
        String sid = UUID.randomUUID().toString();
        String secret = randomToken();
        repository.save(UserSession.builder().id(sid).userId(parent.getUserId()).refreshHash(sha(secret)).deviceKey(parent.getDeviceKey())
                .deviceLabel(info.deviceLabel()).ip(info.ip()).userAgent(info.userAgent()).createdAt(now).lastUsedAt(now)
                .expiresAt(parent.getExpiresAt()).build());
        cache.put(sid, new long[]{1, System.currentTimeMillis()});
        parent.setLastUsedAt(now);   // in use (the seed, or the tab that forked): never the one trimmed
        trim(parent.getUserId(), parent.getDeviceKey(), now, parent.getId());
        return Map.entry(parent, new Issued(sid, sid + "." + secret, false));
    }

    /** Ends the session of this refresh token if it is right (a tab signing out also drops its sign-in seed). */
    public void revokeByRefresh(String refreshToken, String reason) {
        String[] parts = refreshToken == null ? new String[0] : refreshToken.split("\\.", 2);
        if (parts.length != 2) return;
        repository.findById(parts[0]).ifPresent(s -> {
            if (MessageDigest.isEqual(s.getRefreshHash().getBytes(StandardCharsets.UTF_8), sha(parts[1]).getBytes(StandardCharsets.UTF_8))) revoke(s, reason);
        });
    }

    // many tabs opened over days: the least recently used sessions of that device go first
    private void trim(Long userId, String deviceKey, LocalDateTime now, String keep) {
        List<UserSession> same = repository.active(userId, now).stream()
                .filter(s -> java.util.Objects.equals(s.getDeviceKey(), deviceKey) && !s.getId().equals(keep))
                .sorted(java.util.Comparator.comparing(UserSession::getLastUsedAt, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .toList();
        for (int i = 0; i < same.size() - (MAX_PER_DEVICE - 1); i++) revoke(same.get(i), "TOO_MANY_TABS");
    }

    /** Rotates the refresh token; returns the session and the new refresh token. */
    public Map.Entry<UserSession, String> refresh(String refreshToken) {
        String[] parts = refreshToken == null ? new String[0] : refreshToken.split("\\.", 2);
        UserSession s = parts.length == 2 ? repository.findById(parts[0]).orElse(null) : null;
        LocalDateTime now = LocalDateTime.now();
        if (s == null || !s.active(now)) throw expired();
        if (!MessageDigest.isEqual(s.getRefreshHash().getBytes(StandardCharsets.UTF_8), sha(parts[1]).getBytes(StandardCharsets.UTF_8))) {
            // an old refresh token used again: someone else has a copy -> end the session for everybody
            revoke(s, "TOKEN_REUSE");
            throw new ApiException("Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại", HttpStatus.UNAUTHORIZED);
        }
        String secret = randomToken();
        s.setRefreshHash(sha(secret));
        s.setLastUsedAt(now);
        RequestInfo info = RequestInfo.current();
        s.setIp(info.ip());
        return Map.entry(s, s.getId() + "." + secret);
    }

    @Transactional(readOnly = true)
    public boolean isActive(String sid) {
        if (sid == null) return false;
        long[] c = cache.get(sid);
        long now = System.currentTimeMillis();
        if (c != null && now - c[1] < CACHE_MS) return c[0] == 1;
        boolean active = repository.findById(sid).map(s -> s.active(LocalDateTime.now())).orElse(false);
        cache.put(sid, new long[]{active ? 1 : 0, now});
        return active;
    }

    @Transactional(readOnly = true)
    public Optional<UserSession> find(String sid) {
        return sid == null ? Optional.empty() : repository.findById(sid);
    }

    public void revoke(String sid, String reason) {
        repository.findById(sid).ifPresent(s -> revoke(s, reason));
    }

    private void revoke(UserSession s, String reason) {
        if (s.getRevokedAt() == null) {
            s.setRevokedAt(LocalDateTime.now());
            s.setRevokeReason(reason);
        }
        cache.put(s.getId(), new long[]{0, System.currentTimeMillis()});
    }

    /** Ends every session of the user except `keep` (may be null); returns how many. */
    public int revokeAll(Long userId, String keep, String reason) {
        int n = 0;
        for (UserSession s : repository.active(userId, LocalDateTime.now())) {
            if (s.getId().equals(keep)) continue;
            revoke(s, reason);
            n++;
        }
        return n;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long userId, String currentSid) {
        return repository.active(userId, LocalDateTime.now()).stream().map(s -> AdminUserService.map("id", s.getId(),
                "device", s.getDeviceLabel(), "ip", s.getIp(), "createdAt", s.getCreatedAt(), "lastUsedAt", s.getLastUsedAt(),
                "expiresAt", s.getExpiresAt(), "current", s.getId().equals(currentSid))).toList();
    }

    private String randomToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException expired() {
        return new ApiException("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại", HttpStatus.UNAUTHORIZED);
    }
}
