package com.marketplace.backend.service.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.PlatformSettingRepository;
import com.marketplace.backend.security.SecretCipher;
import com.marketplace.backend.service.AdminAuditService;
import com.marketplace.backend.service.AdminUserService;
import com.marketplace.backend.service.ShippingService.ShippingOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giao Hang Tiet Kiem (GHTK) shipping API, staging or production (docs: api.ghtk.vn):
 *  - adjustOptions(): GHTK's options at checkout are priced by GET /services/shipment/fee
 *    (economy = road, express = air "fly"); an address GHTK doesn't serve drops its options;
 *  - createOrder(): POST /services/shipment/order when the seller prepares the order (tracking number = GHTK label);
 *  - cancel(): POST /services/shipment/cancel/{label} before pick-up.
 * Headers: Token (API token) + X-Client-Source (partner code). GHTK takes the address as names (province, district,
 * ward), so there is no master data to map. Status callbacks: GhtkWebhookService.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class GhtkService implements CarrierGateway {

    static final String TOKEN = "ghtk.token", PARTNER = "ghtk.partnerCode", API_URL = "ghtk.apiUrl", ENABLED = "ghtk.enabled",
            WEBHOOK_KEY = "ghtk.webhookKey";
    static final String STAGING_API = "https://services-staging.ghtklab.com";
    public static final String CODE = "GHTK";
    static final int GRAMS_PER_ITEM = 300;
    private static final long FEE_TTL_MS = 10 * 60_000L;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PlatformSettingRepository settings;
    private final SecretCipher cipher;
    private final AdminAuditService auditService;
    private final WebClient webClient;

    @Value("${GHTK_TOKEN:}")
    private String envToken;
    @Value("${GHTK_PARTNER_CODE:}")
    private String envPartner;

    record Settings(boolean enabled, String token, String partnerCode, String apiUrl) {
        boolean ready() {
            return enabled && token != null && !token.isBlank() && partnerCode != null && !partnerCode.isBlank();
        }
    }

    private record Quote(BigDecimal fee, boolean delivers, long at) {
    }

    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean ready() {
        return settings().ready();
    }

    // ==================== checkout ====================

    @Override
    @Transactional(readOnly = true)
    public List<ShippingOption> adjustOptions(List<ShippingOption> options, Shop shop, Address to, int quantity, BigDecimal value) {
        if (to == null || options.stream().noneMatch(o -> CODE.equals(o.getCarrier()))) return options;
        Settings s = settings();
        if (!s.ready() || blank(shop.getPickupDistrict()) || blank(to.getDistrict())) return options;
        List<ShippingOption> list = new ArrayList<>();
        for (ShippingOption o : options) {
            if (!CODE.equals(o.getCarrier())) {
                list.add(o);
                continue;
            }
            Quote q;
            try {
                q = quote(s, shop, to, quantity, value, o.getMethod() == ShippingMethod.EXPRESS ? "fly" : "road");
            } catch (Exception e) {
                log.warn("GHTK fee failed for shop {}: {}", shop.getId(), e.getMessage());
                q = null;
            }
            if (q == null) list.add(o);                 // GHTK silent: keep the table price
            else if (q.delivers()) {
                o.setFee(q.fee());
                list.add(o);
            }                                           // GHTK doesn't deliver there: the option goes
        }
        if (list.isEmpty()) return options;            // never leave the shop without a way to ship
        list.sort(Comparator.comparing(ShippingOption::getFee));
        return list;
    }

    Quote quote(Settings s, Shop shop, Address to, int quantity, BigDecimal value, String transport) {
        int weight = Math.max(1, quantity) * GRAMS_PER_ITEM;
        long insured = value == null ? 0 : Math.max(0, value.longValue());
        String key = String.join("|", shop.getProvince(), shop.getPickupDistrict(), String.valueOf(shop.getPickupWard()),
                to.getProvince(), to.getDistrict(), String.valueOf(to.getWard()), String.valueOf(weight), String.valueOf(insured), transport);
        Quote c = quotes.get(key);
        if (c != null && System.currentTimeMillis() - c.at() < FEE_TTL_MS) return c;
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/services/shipment/fee")
                .queryParam("pick_province", shop.getProvince())
                .queryParam("pick_district", shop.getPickupDistrict())
                .queryParam("province", to.getProvince())
                .queryParam("district", to.getDistrict())
                .queryParam("weight", weight)
                .queryParam("value", insured)
                .queryParam("transport", transport);
        if (!blank(shop.getPickupWard())) uri.queryParam("pick_ward", shop.getPickupWard());
        if (!blank(shop.getPickupAddress())) uri.queryParam("pick_address", shop.getPickupAddress());
        if (!blank(to.getWard())) uri.queryParam("ward", to.getWard());
        if (!blank(to.getAddressLine())) uri.queryParam("address", to.getAddressLine());
        JsonNode fee = call(s, HttpMethod.GET, uri.build().encode().toUriString(), null).path("fee");
        long total = fee.path("fee").asLong() + fee.path("insurance_fee").asLong();
        Quote q = new Quote(BigDecimal.valueOf(total), fee.path("delivery").asBoolean(true) && total > 0, System.currentTimeMillis());
        if (quotes.size() > 5000) quotes.clear();
        quotes.put(key, q);
        return q;
    }

    // ==================== booking ====================

    @Override
    @Transactional(readOnly = true)
    public boolean handles(Order o) {
        return CODE.equals(o.getCarrier()) && o.getRecipientPhone() != null && ready();
    }

    @Override
    public Booking createOrder(Order o) {
        Settings s = settings();
        Shop shop = o.getShop();
        if (blank(shop.getPickupDistrict())) {
            throw bad("GHTK cần quận/huyện lấy hàng của shop. Vui lòng cập nhật đầy đủ địa chỉ lấy hàng trong Hồ sơ shop");
        }
        if (blank(o.getShippingDistrict()) || blank(o.getShippingWard())) {
            throw bad("GHTK cần phường/xã và quận/huyện của người nhận. Vui lòng liên hệ người mua để xác nhận địa chỉ");
        }
        boolean cod = o.getPayment() != null && o.getPayment().getMethod() == PaymentMethod.COD;
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", o.getCode());
        order.put("pick_name", shop.getName());
        order.put("pick_money", cod ? o.getTotal().longValue() : 0);   // what GHTK collects from the buyer
        order.put("pick_address", shop.getPickupAddress());
        order.put("pick_province", shop.getProvince());
        order.put("pick_district", shop.getPickupDistrict());
        if (!blank(shop.getPickupWard())) order.put("pick_ward", shop.getPickupWard());
        order.put("pick_tel", shop.getPickupPhone());
        order.put("name", o.getRecipientName());
        order.put("address", o.getShippingAddressSnapshot());
        order.put("province", o.getShippingProvince());
        order.put("district", o.getShippingDistrict());
        order.put("ward", o.getShippingWard());
        order.put("hamlet", "Khác");                   // GHTK wants a street or a hamlet; the street is in the address line
        order.put("tel", o.getRecipientPhone());
        order.put("is_freeship", 1);                   // the buyer paid the fee at checkout: collect only pick_money
        order.put("note", cut(o.getNote(), 120));
        order.put("value", o.getSubtotal() == null ? 0 : o.getSubtotal().longValue());
        order.put("transport", o.getShippingMethod() == ShippingMethod.EXPRESS ? "fly" : "road");
        order.put("pick_option", "DROPOFF".equals(o.getPickupMethod()) ? "post" : "cod");
        order.put("weight_option", "gram");
        List<Map<String, Object>> products = o.getItems().stream().map(i -> AdminUserService.map(
                "name", cut(i.getProductNameSnapshot() + (i.getVariantNameSnapshot() != null ? " - " + i.getVariantNameSnapshot() : ""), 200),
                "weight", GRAMS_PER_ITEM * i.getQuantity(), "quantity", i.getQuantity(),
                "product_code", String.valueOf(i.getId()))).toList();
        order.put("total_weight", products.stream().mapToInt(p -> (Integer) p.get("weight")).sum());

        JsonNode r = null;
        for (int attempt = 1; r == null; attempt++) {
            // GHTK keeps each partner id for good: after GHTK cancelled a booking, the next one needs a new id
            order.put("id", attempt == 1 ? o.getCode() : o.getCode() + "-" + attempt);
            try {
                r = call(s, HttpMethod.POST, "/services/shipment/order?ver=1.5", Map.of("products", products, "order", order));
            } catch (GhtkRejected e) {
                JsonNode error = e.body.path("error");
                String existing = error.path("ghtk_label").asText(null);
                if (!"ORDER_ID_EXIST".equals(error.path("code").asText()) || existing == null) throw bad("GHTK từ chối: " + e.getMessage());
                boolean cancelled = error.path("status").asText("").trim().equals("-1");
                // booked before and still alive (e.g. a retry after a timeout): reuse that label
                if (!cancelled) return new Booking(existing, null, null, "2");
                if (attempt >= 5) throw bad("GHTK từ chối: mã đơn đã tồn tại");
            }
        }
        JsonNode data = r.path("order");
        String label = data.path("label").asText(null);
        if (label == null || label.isBlank()) throw bad("GHTK không trả về mã vận đơn, vui lòng thử lại");
        long fee = money(data.path("fee")) + money(data.path("insurance_fee"));
        return new Booking(label, fee > 0 ? BigDecimal.valueOf(fee) : null, null, data.path("status_id").asText("2"));
    }

    @Override
    public void cancel(String label) {
        try {
            call(settings(), HttpMethod.POST, "/services/shipment/cancel/" + label, Map.of());
        } catch (Exception e) {
            log.warn("GHTK cancel {} failed: {}", label, e.getMessage());
        }
    }

    // ==================== HTTP ====================

    /** GHTK answered success:false: the message, and the body (for error codes). */
    static class GhtkRejected extends RuntimeException {
        final transient JsonNode body;

        GhtkRejected(String message, JsonNode body) {
            super(message);
            this.body = body;
        }
    }

    JsonNode call(Settings s, HttpMethod method, String path, Object body) {
        if (!s.ready()) throw new ApiException("Chưa kết nối Giao Hàng Tiết Kiệm", HttpStatus.SERVICE_UNAVAILABLE);
        String url = s.apiUrl().replaceAll("/+$", "") + path;
        String raw;
        try {
            WebClient.RequestBodySpec spec = webClient.method(method).uri(java.net.URI.create(url))
                    .header("Token", s.token()).header("X-Client-Source", s.partnerCode());
            raw = (body == null ? spec : spec.contentType(MediaType.APPLICATION_JSON).bodyValue(body))
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(20));
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 403) throw bad("GHTK từ chối token (403). Kiểm tra lại API token và mã đối tác");
            raw = e.getResponseBodyAsString();
            if (raw == null || raw.isBlank()) throw bad("GHTK lỗi " + e.getStatusCode().value());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Không kết nối được GHTK: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        JsonNode r;
        try {
            r = JSON.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (Exception e) {
            throw new ApiException("GHTK trả về dữ liệu không hợp lệ", HttpStatus.BAD_GATEWAY);
        }
        if (!r.path("success").asBoolean(false)) {
            String msg = r.path("message").asText("");
            if (msg.isBlank()) msg = r.path("error_message").asText("lỗi không xác định");
            throw new GhtkRejected(msg, r);
        }
        return r;
    }

    // ==================== settings (admin) ====================

    Settings settings() {
        boolean admin = get(TOKEN) != null;
        return new Settings(!"false".equals(get(ENABLED)), admin ? cipher.decrypt(get(TOKEN)) : envToken,
                admin ? get(PARTNER) : envPartner, Objects.requireNonNullElse(get(API_URL), STAGING_API));
    }

    /** The secret in the webhook URL (GHTK's callbacks carry no signature). */
    public String webhookKey() {
        String k = get(WEBHOOK_KEY);
        if (k == null) {
            byte[] b = new byte[18];
            new SecureRandom().nextBytes(b);
            k = HexFormat.of().formatHex(b);
            put(WEBHOOK_KEY, k);
        }
        return k;
    }

    public boolean webhookKeyMatches(String key) {
        String k = get(WEBHOOK_KEY);
        return k != null && key != null && MessageDigest.isEqual(k.getBytes(), key.getBytes());
    }

    @Override
    public Map<String, Object> config() {
        Settings s = settings();
        return AdminUserService.map("enabled", s.enabled(), "partnerCode", s.partnerCode(), "tokenSet", !blank(s.token()),
                "apiUrl", s.apiUrl(), "ready", s.ready(), "sandbox", s.apiUrl().contains("staging") || s.apiUrl().contains("ghtklab"),
                "webhookPath", "/api/shipping/ghtk/webhook?key=" + webhookKey(),
                "source", get(TOKEN) != null ? "ADMIN" : !blank(envToken) ? "ENV" : "NONE");
    }

    // body: { enabled, partnerCode?, token?, apiUrl? }
    @Override
    public Map<String, Object> update(Map<String, Object> body) {
        String partner = str(body.get("partnerCode"));
        if (partner != null && !partner.matches("[A-Za-z0-9_.-]{1,64}")) throw bad("Mã đối tác (X-Client-Source) không hợp lệ");
        String api = str(body.get("apiUrl"));
        if (api != null && !api.matches("https://[^\\s]{4,300}|http://(localhost|127\\.0\\.0\\.1)(:\\d{2,5})?(/[^\\s]{0,200})?")) {
            throw bad("Địa chỉ API GHTK phải là https://");
        }
        String token = str(body.get("token"));
        if (token != null && (token.length() < 10 || token.length() > 200)) throw bad("API token GHTK không hợp lệ");
        if (token == null && get(TOKEN) == null && partner != null) throw bad("Vui lòng nhập API token GHTK");
        if (token != null && partner == null) throw bad("Vui lòng nhập mã đối tác GHTK (X-Client-Source)");
        put(ENABLED, String.valueOf(!Boolean.FALSE.equals(body.get("enabled"))));
        put(PARTNER, partner);
        put(API_URL, api);
        if (token != null) put(TOKEN, cipher.encrypt(token));
        if (partner == null && token == null) put(TOKEN, null);   // both emptied: disconnect
        quotes.clear();
        auditService.log("UPDATE_SHIPPING_GATEWAY", "SETTING", null, "Giao Hàng Tiết Kiệm",
                (Boolean.FALSE.equals(body.get("enabled")) ? "Tắt" : "Bật") + (partner != null ? " · mã đối tác " + partner : "") + (token != null ? " · đổi token" : ""));
        return config();
    }

    /** Checks the token with a sample quote (Hà Nội → TP. Hồ Chí Minh, 500 g). */
    @Override
    public Map<String, Object> test() {
        Settings s = settings();
        if (!s.ready()) throw bad("Vui lòng nhập API token và mã đối tác GHTK trước");
        String uri = UriComponentsBuilder.fromPath("/services/shipment/fee")
                .queryParam("pick_province", "Hà Nội").queryParam("pick_district", "Quận Ba Đình")
                .queryParam("province", "TP. Hồ Chí Minh").queryParam("district", "Quận 1")
                .queryParam("weight", 500).queryParam("value", 0).build().encode().toUriString();
        JsonNode fee;
        try {
            fee = call(s, HttpMethod.GET, uri, null).path("fee");
        } catch (GhtkRejected e) {
            throw bad("GHTK từ chối: " + e.getMessage());
        }
        return AdminUserService.map("ok", true, "message", "Kết nối GHTK thành công (phí mẫu Hà Nội → TP.HCM 500 g: "
                + String.format("%,d", fee.path("fee").asLong()).replace(',', '.') + " đ)");
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

    /** GHTK sends amounts as numbers or as text ("30400"). */
    static long money(JsonNode n) {
        String digits = n.asText("").replaceAll("[^0-9]", "");
        return digits.isEmpty() || digits.length() > 15 ? 0 : Long.parseLong(digits);
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
