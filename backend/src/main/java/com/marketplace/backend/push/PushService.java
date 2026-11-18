package com.marketplace.backend.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.entity.PushSubscription;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.PushSubscriptionRepository;
import com.marketplace.backend.security.CurrentUser;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Push notifications: devices register here, and every in-app notification (NotificationService) is also pushed to
 * the user's browsers (Web Push) and phones (Expo). Sending happens on a background thread after the commit, so a
 * slow push service never delays the request that created the notification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final PushSubscriptionRepository repository;
    private final WebPushSender webPush;
    private final ExpoPushSender expoPush;
    private final ObjectMapper json;

    @Value("${app.push.subject:mailto:admin@shoppi.vn}")
    private String subject;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "push-sender");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    // ==================== devices ====================

    public Map<String, Object> webConfig() {
        return Map.of("publicKey", webPush.publicKey());
    }

    /** body: { kind: WEB|EXPO, endpoint, p256dh?, auth?, app?, device? } - a device already registered moves to this user */
    public Map<String, Object> subscribe(Map<String, Object> body) {
        PushSubscription.Kind kind;
        try {
            kind = PushSubscription.Kind.valueOf(String.valueOf(body.get("kind")).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException("Loại thiết bị không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        String endpoint = str(body.get("endpoint"), 1000);
        if (endpoint == null) throw new ApiException("Thiếu địa chỉ nhận thông báo", HttpStatus.BAD_REQUEST);
        if (kind == PushSubscription.Kind.WEB && (!endpoint.startsWith("https://") || str(body.get("p256dh"), 200) == null || str(body.get("auth"), 100) == null))
            throw new ApiException("Đăng ký Web Push không hợp lệ", HttpStatus.BAD_REQUEST);
        if (kind == PushSubscription.Kind.EXPO && !endpoint.matches("Expo(nent)?PushToken\\[[^\\]]+]"))
            throw new ApiException("Mã thiết bị không hợp lệ", HttpStatus.BAD_REQUEST);

        PushSubscription s = repository.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        s.setUserId(CurrentUser.id());
        s.setKind(kind);
        s.setEndpoint(endpoint);
        s.setP256dh(str(body.get("p256dh"), 200));
        s.setAuth(str(body.get("auth"), 100));
        String app = str(body.get("app"), 10);
        s.setApp("seller".equals(app) ? "seller" : kind == PushSubscription.Kind.EXPO ? "buyer" : null);
        s.setDevice(str(body.get("device"), 200));
        repository.save(s);
        return Map.of("subscribed", true, "devices", repository.findByUserId(s.getUserId()).size());
    }

    /** body: { endpoint } - only removes the device if it belongs to the signed-in user */
    public void unsubscribe(Map<String, Object> body) {
        String endpoint = str(body.get("endpoint"), 1000);
        if (endpoint != null) repository.deleteByEndpointAndUserId(endpoint, CurrentUser.id());
    }

    /** Sends a test notification to all devices of the signed-in user. */
    public Map<String, Object> test() {
        Long me = CurrentUser.id();
        List<PushSubscription> devices = repository.findByUserId(me);
        if (devices.isEmpty()) throw new ApiException("Chưa có thiết bị nào bật thông báo đẩy", HttpStatus.BAD_REQUEST);
        executor.submit(() -> deliver(new PushEvent(List.of(me), "SYSTEM", "Shoppi đã bật thông báo đẩy 🎉",
                "Bạn sẽ nhận tin về đơn hàng, tin nhắn và khuyến mãi ngay cả khi không mở Shoppi.", null, null)));
        return Map.of("devices", devices.size());
    }

    // ==================== sending ====================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotification(PushEvent e) {
        if (e.userIds() == null || e.userIds().isEmpty()) return;
        executor.submit(() -> {
            try {
                deliver(e);
            } catch (Exception ex) {
                log.warn("Push failed: {}", ex.getMessage());
            }
        });
    }

    private void deliver(PushEvent e) {
        List<PushSubscription> targets = repository.findByUserIdIn(new HashSet<>(e.userIds()));
        if (targets.isEmpty()) return;
        String body = e.body() == null ? "" : e.body();
        String link = e.link();

        // browsers: one encrypted message each
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", e.title());
        payload.put("body", body);
        payload.put("link", link);
        payload.put("image", e.imageUrl());
        payload.put("type", e.type());
        String webJson;
        try {
            webJson = json.writeValueAsString(payload);
        } catch (Exception ex) {
            return;
        }
        for (PushSubscription s : targets) {
            if (s.getKind() != PushSubscription.Kind.WEB) continue;
            WebPushSender.Result r = webPush.send(s, webJson, subject);
            if (r == WebPushSender.Result.GONE) repository.deleteByEndpoint(s.getEndpoint());
            else if (r == WebPushSender.Result.OK) touch(s);
        }

        // phones: seller news to the seller app, the rest to the buyer app; admin pages have no app
        String targetApp = link == null ? null : link.startsWith("/seller") ? "seller" : link.startsWith("/admin") ? "none" : "buyer";
        List<PushSubscription> phones = targets.stream()
                .filter(s -> s.getKind() == PushSubscription.Kind.EXPO)
                .filter(s -> targetApp == null || targetApp.equals(s.getApp()))
                .toList();
        if (!phones.isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("link", link);
            data.put("type", e.type());
            expoPush.send(phones, e.title(), body, data).forEach(repository::deleteByEndpoint);
        }
    }

    private void touch(PushSubscription s) {
        try {
            s.setLastSuccessAt(LocalDateTime.now());
            repository.save(s);
        } catch (Exception ignored) {
            // deleted meanwhile
        }
    }

    private static String str(Object o, int max) {
        if (o == null) return null;
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
