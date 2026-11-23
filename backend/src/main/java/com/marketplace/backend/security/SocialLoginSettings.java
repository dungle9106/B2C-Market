package com.marketplace.backend.security;

import com.marketplace.backend.entity.PlatformSetting;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.PlatformSettingRepository;
import com.marketplace.backend.service.AdminAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * "Dang nhap voi Google / Facebook / GitHub". The client id / secret of each provider are entered on the admin
 * page (secret stored encrypted), falling back to the GOOGLE_CLIENT_ID / FACEBOOK_CLIENT_ID / GITHUB_CLIENT_ID
 * (+ _SECRET) variables. This is also Spring Security's ClientRegistrationRepository, so new keys work at once,
 * without a restart. A provider without keys (or switched off) simply has no registration.
 *
 * Public URL: every provider only accepts the callback addresses registered with it, and Google/Facebook refuse
 * private IPs, so the phone app (which reaches the PC as 10.0.2.2 or a LAN IP) needs a public https address of
 * this backend (a real domain or an ngrok tunnel). When set, the callback and the start of the sign-in both use it.
 */
@Component
@RequiredArgsConstructor
public class SocialLoginSettings implements ClientRegistrationRepository {

    public record Provider(String id, String name, CommonOAuth2Provider common, String env, String[] scopes, String consoleUrl) { }

    public static final List<Provider> PROVIDERS = List.of(
            new Provider("google", "Google", CommonOAuth2Provider.GOOGLE, "GOOGLE", new String[]{"openid", "profile", "email"},
                    "https://console.cloud.google.com/apis/credentials"),
            new Provider("facebook", "Facebook", CommonOAuth2Provider.FACEBOOK, "FACEBOOK", new String[]{"public_profile", "email"},
                    "https://developers.facebook.com/apps"),
            new Provider("github", "GitHub", CommonOAuth2Provider.GITHUB, "GITHUB", new String[]{"read:user", "user:email"},
                    "https://github.com/settings/developers"));

    static final String PUBLIC_URL = "oauth.publicUrl";

    private final PlatformSettingRepository settings;
    private final SecretCipher cipher;
    private final Environment env;
    private final AdminAuditService audit;

    // ==================== ClientRegistrationRepository ====================

    @Override
    @Transactional(readOnly = true)
    public ClientRegistration findByRegistrationId(String registrationId) {
        Provider p = provider(registrationId);
        if (p == null) return null;
        Keys k = keys(p);
        if (!k.ready()) return null;
        String base = publicUrl();
        return p.common().getBuilder(p.id())
                .clientId(k.clientId())
                .clientSecret(k.secret())
                .scope(p.scopes())
                .redirectUri((base != null ? base : "{baseUrl}") + "/login/oauth2/code/{registrationId}")
                .build();
    }

    // ==================== what the login pages show ====================

    /** The providers that can be used right now, and where to start the sign-in (null = the API's own address). */
    @Transactional(readOnly = true)
    public Map<String, Object> publicInfo() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Provider p : PROVIDERS) {
            if (keys(p).ready()) list.add(Map.of("id", p.id(), "name", p.name()));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("providers", list);
        m.put("startBase", publicUrl());
        return m;
    }

    @Transactional(readOnly = true)
    public boolean ready(String id) {
        Provider p = provider(id);
        return p != null && keys(p).ready();
    }

    // ==================== admin page ====================

    @Transactional(readOnly = true)
    public Map<String, Object> config() {
        String base = publicUrl();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Provider p : PROVIDERS) {
            Keys k = keys(p);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("name", p.name());
            m.put("enabled", k.enabled());
            m.put("clientId", k.clientId());
            m.put("secretSet", k.secret() != null && !k.secret().isBlank());
            m.put("ready", k.ready());
            m.put("source", k.source());
            m.put("consoleUrl", p.consoleUrl());
            m.put("callbackUrl", (base != null ? base : "http://localhost:8081") + "/login/oauth2/code/" + p.id());
            list.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("publicUrl", base);
        r.put("providers", list);
        return r;
    }

    /** body: { publicUrl?, providers: [{ id, enabled, clientId, clientSecret? }] } */
    @Transactional
    public Map<String, Object> update(Map<String, Object> body) {
        if (body.containsKey("publicUrl")) {
            String url = str(body.get("publicUrl"));
            if (url != null) {
                url = url.replaceAll("/+$", "");
                if (!url.matches("https://[A-Za-z0-9.-]+(:\\d{2,5})?(/[^\\s]*)?|http://(localhost|127\\.0\\.0\\.1)(:\\d{2,5})?")) {
                    throw bad("Địa chỉ công khai phải bắt đầu bằng https:// (ví dụ https://abc.ngrok-free.app)");
                }
            }
            put(PUBLIC_URL, url);
        }
        Object raw = body.get("providers");
        List<String> changed = new ArrayList<>();
        if (raw instanceof List<?> items) {
            for (Object o : items) {
                if (!(o instanceof Map<?, ?> item)) continue;
                Provider p = provider(str(item.get("id")));
                if (p == null) continue;
                String id = str(item.get("clientId"));
                if (id != null && (id.length() > 200 || !id.matches("[A-Za-z0-9._\\-]+"))) throw bad("Client ID của " + p.name() + " không hợp lệ");
                put(key(p, "enabled"), String.valueOf(!Boolean.FALSE.equals(item.get("enabled"))));
                put(key(p, "clientId"), id);
                String secret = str(item.get("clientSecret"));
                if (secret != null) {
                    if (secret.length() < 8 || secret.length() > 200) throw bad("Client secret của " + p.name() + " không hợp lệ");
                    put(key(p, "secret"), cipher.encrypt(secret));
                }
                if (id == null) put(key(p, "secret"), null);
                changed.add(p.name() + (Boolean.FALSE.equals(item.get("enabled")) ? " (tắt)" : ""));
            }
        }
        audit.log("UPDATE_SOCIAL_LOGIN", "SETTING", null, "Đăng nhập mạng xã hội", String.join(", ", changed));
        return config();
    }

    // ==================== helpers ====================

    record Keys(boolean enabled, String clientId, String secret, String source) {
        boolean ready() {
            return enabled && clientId != null && !clientId.isBlank() && secret != null && !secret.isBlank();
        }
    }

    private Keys keys(Provider p) {
        String adminId = get(key(p, "clientId"));
        boolean enabled = !"false".equals(get(key(p, "enabled")));
        if (adminId != null) return new Keys(enabled, adminId, cipher.decrypt(get(key(p, "secret"))), "ADMIN");
        String envId = env.getProperty(p.env() + "_CLIENT_ID");
        String envSecret = env.getProperty(p.env() + "_CLIENT_SECRET");
        if (envId != null && !envId.isBlank()) return new Keys(enabled, envId, envSecret, "ENV");
        return new Keys(enabled, null, null, "NONE");
    }

    /** The public https address of this backend, if one is configured. */
    public String publicUrl() {
        String s = get(PUBLIC_URL);
        if (s == null) s = str(env.getProperty("OAUTH2_PUBLIC_URL"));
        return s;
    }

    public static Provider provider(String id) {
        if (id == null) return null;
        return PROVIDERS.stream().filter(p -> p.id().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    private static String key(Provider p, String field) {
        return "oauth." + p.id() + "." + field;
    }

    private String get(String key) {
        return settings.findById(key).map(PlatformSetting::getValue).orElse(null);
    }

    private void put(String key, String value) {
        if (value == null) {
            settings.findById(key).ifPresent(settings::delete);
            return;
        }
        PlatformSetting st = settings.findById(key).orElseGet(PlatformSetting::new);
        st.setKey(key);
        st.setValue(value);
        st.setUpdatedAt(LocalDateTime.now());
        settings.save(st);
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
