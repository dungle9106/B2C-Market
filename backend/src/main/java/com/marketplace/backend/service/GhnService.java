package com.marketplace.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.PlatformSettingRepository;
import com.marketplace.backend.security.SecretCipher;
import com.marketplace.backend.service.ShippingService.ShippingOption;
import com.marketplace.backend.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giao Hang Nhanh (GHN) shipping API, sandbox or production:
 *  - master data (province / district / ward) to turn the address texts into GHN's ids, cached;
 *  - adjustOptions(): the GHN option at checkout is priced by GHN's fee API (the table is the fallback);
 *  - createOrder(): books the parcel when the seller prepares the order (tracking number = GHN order_code);
 *  - cancel(): cancels the booking when the order is cancelled before pick-up.
 * GHN's status webhooks are handled by GhnWebhookService. Settings (token encrypted, shop id, URL) come from the
 * admin page, falling back to the GHN_TOKEN / GHN_SHOP_ID variables. Not configured = the simulated carrier.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class GhnService {

    static final String TOKEN = "ghn.token", SHOP_ID = "ghn.shopId", API_URL = "ghn.apiUrl", ENABLED = "ghn.enabled", WEBHOOK_KEY = "ghn.webhookKey";
    static final String SANDBOX_API = "https://dev-online-gateway.ghn.vn/shiip/public-api";
    static final String CODE = "GHN";
    // a parcel when the products carry no size: 300 g per item, a 20x15x10 cm box
    static final int GRAMS_PER_ITEM = 300;
    private static final long MASTER_TTL_MS = 12 * 3600_000L, FEE_TTL_MS = 10 * 60_000L;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PlatformSettingRepository settings;
    private final SecretCipher cipher;
    private final AdminAuditService auditService;
    private final WebClient webClient;

    @Value("${GHN_TOKEN:}")
    private String envToken;
    @Value("${GHN_SHOP_ID:}")
    private String envShopId;

    record Settings(boolean enabled, String token, String shopId, String apiUrl) {
        boolean ready() {
            return enabled && token != null && !token.isBlank() && shopId != null && !shopId.isBlank();
        }
    }

    /** A place in GHN's master data. */
    record Place(long id, String code, String name, List<String> aliases) {
    }

    record Loc(int districtId, String wardCode, String districtName, String wardName) {
    }

    private record Cached<T>(T value, long at) {
    }

    private final Map<String, Cached<List<Place>>> master = new ConcurrentHashMap<>();
    private final Map<String, Cached<BigDecimal>> fees = new ConcurrentHashMap<>();

    public boolean ready() {
        return settings().ready();
    }

    // ==================== checkout ====================

    /**
     * GHN connected and the shop ships with GHN: its option becomes GHN's real standard service, priced by GHN
     * (express / same-day stay with the simulated carriers). If GHN can't quote this address, nothing changes.
     */
    @Transactional(readOnly = true)
    public List<ShippingOption> adjustOptions(List<ShippingOption> options, Shop shop, Address to, int quantity, BigDecimal value) {
        if (to == null || options.stream().noneMatch(o -> CODE.equals(o.getCarrier()))) return options;
        Settings s = settings();
        if (!s.ready()) return options;
        BigDecimal fee;
        try {
            fee = quote(s, shop, to.getProvince(), to.getDistrict(), to.getWard(), quantity, value);
        } catch (Exception e) {
            log.warn("GHN fee failed for shop {}: {}", shop.getId(), e.getMessage());
            fee = null;
        }
        if (fee == null) return options;
        List<ShippingOption> list = new ArrayList<>();
        for (ShippingOption o : options) {
            if (!CODE.equals(o.getCarrier())) list.add(o);
            else if (o.getMethod() == ShippingMethod.ECONOMY) {
                o.setFee(fee);
                o.setMethodName("Chuẩn");
                list.add(o);
            }
        }
        list.sort(Comparator.comparing(ShippingOption::getFee));
        return list;
    }

    BigDecimal quote(Settings s, Shop shop, String province, String district, String ward, int quantity, BigDecimal value) {
        Loc from = resolve(s, shop.getProvince(), shop.getPickupDistrict(), shop.getPickupWard());
        Loc dest = resolve(s, province, district, ward);
        if (from == null || dest == null) return null;
        int weight = Math.max(1, quantity) * GRAMS_PER_ITEM;
        long insurance = insurance(value);
        String key = from.districtId() + "|" + from.wardCode() + "|" + dest.districtId() + "|" + dest.wardCode() + "|" + weight + "|" + insurance;
        Cached<BigDecimal> c = fees.get(key);
        if (c != null && System.currentTimeMillis() - c.at() < FEE_TTL_MS) return c.value();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service_type_id", 2);
        body.put("from_district_id", from.districtId());
        body.put("from_ward_code", from.wardCode());
        body.put("to_district_id", dest.districtId());
        body.put("to_ward_code", dest.wardCode());
        body.put("weight", weight);
        body.put("length", 20);
        body.put("width", 15);
        body.put("height", 10);
        body.put("insurance_value", insurance);
        JsonNode data = call(s, "/v2/shipping-order/fee", body);
        BigDecimal fee = BigDecimal.valueOf(data.path("total").asLong());
        if (fee.signum() <= 0) return null;
        if (fees.size() > 5000) fees.clear();
        fees.put(key, new Cached<>(fee, System.currentTimeMillis()));
        return fee;
    }

    // ==================== booking ====================

    /** Can this order be booked with GHN? (connected, a GHN parcel, address in parts) */
    @Transactional(readOnly = true)
    public boolean handles(Order o) {
        return CODE.equals(o.getCarrier()) && o.getRecipientPhone() != null && ready();
    }

    public record Booking(String orderCode, BigDecimal fee, LocalDateTime expectedDelivery) {
    }

    /** Books the parcel with GHN; the order_code becomes the tracking number. Throws with GHN's reason. */
    public Booking createOrder(Order o) {
        Settings s = settings();
        Shop shop = o.getShop();
        Loc from = resolve(s, shop.getProvince(), shop.getPickupDistrict(), shop.getPickupWard());
        if (from == null) {
            throw bad("GHN chưa nhận diện được địa chỉ lấy hàng của shop. Vui lòng cập nhật đầy đủ phường/xã, quận/huyện trong Hồ sơ shop");
        }
        Loc dest = resolve(s, o.getShippingProvince(), o.getShippingDistrict(), o.getShippingWard());
        if (dest == null) throw bad("GHN chưa nhận diện được địa chỉ người nhận (phường/xã, quận/huyện). Vui lòng liên hệ người mua để xác nhận địa chỉ");
        int qty = o.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        boolean cod = o.getPayment() != null && o.getPayment().getMethod() == PaymentMethod.COD;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payment_type_id", 1);                // the sender (Shoppi) pays GHN: the buyer paid the fee at checkout
        body.put("required_note", "CHOXEMHANGKHONGTHU");
        body.put("note", o.getNote() == null ? "" : o.getNote());
        body.put("client_order_code", o.getCode());
        body.put("from_name", shop.getName());
        body.put("from_phone", shop.getPickupPhone());
        body.put("from_address", shop.getPickupAddress());
        body.put("from_ward_name", from.wardName());
        body.put("from_district_name", from.districtName());
        body.put("from_province_name", shop.getProvince());
        body.put("to_name", o.getRecipientName());
        body.put("to_phone", o.getRecipientPhone());
        body.put("to_address", o.getShippingAddressSnapshot());
        body.put("to_ward_code", dest.wardCode());
        body.put("to_district_id", dest.districtId());
        body.put("cod_amount", cod ? o.getTotal().longValue() : 0);
        body.put("content", "Đơn hàng " + o.getCode());
        body.put("weight", Math.max(1, qty) * GRAMS_PER_ITEM);
        body.put("length", 20);
        body.put("width", 15);
        body.put("height", 10);
        body.put("insurance_value", insurance(o.getSubtotal()));
        body.put("service_type_id", 2);
        body.put("pick_station_id", null);
        body.put("items", o.getItems().stream().map(i -> AdminUserService.map(
                "name", i.getProductNameSnapshot() + (i.getVariantNameSnapshot() != null ? " - " + i.getVariantNameSnapshot() : ""),
                "quantity", i.getQuantity(), "price", i.getPriceAtPurchase().longValue(), "weight", GRAMS_PER_ITEM)).toList());
        JsonNode data = call(s, "/v2/shipping-order/create", body);
        String code = data.path("order_code").asText(null);
        if (code == null || code.isBlank()) throw bad("GHN không trả về mã vận đơn, vui lòng thử lại");
        LocalDateTime eta = null;
        String expected = data.path("expected_delivery_time").asText(null);
        if (expected != null && expected.length() >= 19) {
            try {
                eta = java.time.OffsetDateTime.parse(expected).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime();
            } catch (Exception ignored) {
                // GHN sends ISO dates; anything else is just not shown
            }
        }
        return new Booking(code, data.has("total_fee") ? BigDecimal.valueOf(data.path("total_fee").asLong()) : null, eta);
    }

    /** Cancels a GHN booking (order cancelled before pick-up); failures are only logged. */
    public void cancel(String orderCode) {
        try {
            call(settings(), "/v2/switch-status/cancel", Map.of("order_codes", List.of(orderCode)));
        } catch (Exception e) {
            log.warn("GHN cancel {} failed: {}", orderCode, e.getMessage());
        }
    }

    private static long insurance(BigDecimal value) {
        // GHN insures up to 5,000,000 đ without extra paperwork
        return value == null ? 0 : Math.min(5_000_000L, Math.max(0, value.longValue()));
    }

    // ==================== addresses ====================

    /** Address texts -> GHN district id + ward code; null if GHN doesn't know them. */
    Loc resolve(Settings s, String province, String district, String ward) {
        if (isBlank(district) || isBlank(ward)) return null;
        List<Place> provinces = list(s, "province", null);
        List<Place> candidates = provinces.stream().filter(p -> matches(p, province, PROVINCE_PREFIX)).toList();
        // after the 2025 merger a new province name may cover several of GHN's: then look in all of them
        if (candidates.isEmpty()) candidates = provinces;
        for (Place p : candidates) {
            Place d = list(s, "district", p.id()).stream().filter(x -> matches(x, district, DISTRICT_PREFIX)).findFirst().orElse(null);
            if (d == null) continue;
            Place w = list(s, "ward", d.id()).stream().filter(x -> matches(x, ward, WARD_PREFIX)).findFirst().orElse(null);
            if (w == null) continue;
            return new Loc((int) d.id(), w.code(), d.name(), w.name());
        }
        return null;
    }

    private static final String PROVINCE_PREFIX = "^(tp\\.?|thanh pho|tinh)\\s+";
    private static final String DISTRICT_PREFIX = "^(quan|huyen|thi xa|thanh pho|tp\\.?|q\\.?)\\s+";
    private static final String WARD_PREFIX = "^(phuong|xa|thi tran|p\\.?)\\s+";

    static boolean matches(Place p, String text, String prefix) {
        String want = simplify(text, prefix);
        if (want == null) return false;
        if (want.equals(simplify(p.name(), prefix))) return true;
        return p.aliases().stream().anyMatch(a -> want.equals(simplify(a, prefix)));
    }

    static String simplify(String text, String prefix) {
        String n = TextNormalizer.normalize(text);
        if (n == null || n.isBlank()) return null;
        n = n.replace(".", " ").replaceAll("\\s+", " ").trim();
        n = n.replaceAll(prefix, "").trim();
        // "Quận 01" == "Quận 1"
        return n.replaceAll("^0+(\\d)", "$1");
    }

    private List<Place> list(Settings s, String kind, Long parent) {
        String key = s.apiUrl() + "|" + kind + "|" + parent;
        Cached<List<Place>> c = master.get(key);
        if (c != null && System.currentTimeMillis() - c.at() < MASTER_TTL_MS) return c.value();
        JsonNode data = switch (kind) {
            case "province" -> call(s, "/master-data/province", null);
            case "district" -> call(s, "/master-data/district", Map.of("province_id", parent));
            default -> call(s, "/master-data/ward?district_id=" + parent, Map.of("district_id", parent));
        };
        List<Place> places = new ArrayList<>();
        for (JsonNode n : data) {
            List<String> aliases = new ArrayList<>();
            n.path("NameExtension").forEach(a -> aliases.add(a.asText()));
            switch (kind) {
                case "province" -> places.add(new Place(n.path("ProvinceID").asLong(), null, n.path("ProvinceName").asText(), aliases));
                case "district" -> places.add(new Place(n.path("DistrictID").asLong(), null, n.path("DistrictName").asText(), aliases));
                default -> places.add(new Place(0, n.path("WardCode").asText(), n.path("WardName").asText(), aliases));
            }
        }
        master.put(key, new Cached<>(places, System.currentTimeMillis()));
        return places;
    }

    // ==================== HTTP ====================

    /** POST (or GET without a body) to GHN; returns "data", throws with GHN's message when code != 200. */
    JsonNode call(Settings s, String path, Object body) {
        if (!s.ready()) throw new ApiException("Chưa kết nối Giao Hàng Nhanh", HttpStatus.SERVICE_UNAVAILABLE);
        String url = s.apiUrl().replaceAll("/+$", "") + path;
        String raw;
        try {
            WebClient.RequestHeadersSpec<?> spec = body == null
                    ? webClient.get().uri(url).header("Token", s.token()).header("ShopId", s.shopId())
                    : webClient.post().uri(url).header("Token", s.token()).header("ShopId", s.shopId())
                            .contentType(MediaType.APPLICATION_JSON).bodyValue(body);
            raw = spec.retrieve().bodyToMono(String.class).block(Duration.ofSeconds(20));
        } catch (WebClientResponseException e) {
            raw = e.getResponseBodyAsString();
            if (raw == null || raw.isBlank()) throw bad("GHN lỗi " + e.getStatusCode().value());
        } catch (Exception e) {
            throw new ApiException("Không kết nối được GHN: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        JsonNode r;
        try {
            r = JSON.readTree(raw == null ? "{}" : raw);
        } catch (Exception e) {
            throw new ApiException("GHN trả về dữ liệu không hợp lệ", HttpStatus.BAD_GATEWAY);
        }
        if (r.path("code").asInt() != 200) {
            String msg = r.path("code_message_value").asText(r.path("message").asText("lỗi không xác định"));
            throw bad("GHN từ chối: " + msg);
        }
        return r.path("data");
    }

    // ==================== settings (admin) ====================

    Settings settings() {
        boolean admin = get(TOKEN) != null;
        return new Settings(!"false".equals(get(ENABLED)), admin ? cipher.decrypt(get(TOKEN)) : envToken,
                admin ? get(SHOP_ID) : envShopId, Objects.requireNonNullElse(get(API_URL), SANDBOX_API));
    }

    /** The secret in GHN's webhook URL (GHN doesn't sign its callbacks). */
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

    public Map<String, Object> config() {
        Settings s = settings();
        return AdminUserService.map("enabled", s.enabled(), "shopId", s.shopId(), "tokenSet", s.token() != null && !s.token().isBlank(),
                "apiUrl", s.apiUrl(), "ready", s.ready(), "sandbox", s.apiUrl().contains("dev-online-gateway"),
                "webhookPath", "/api/shipping/ghn/webhook?key=" + webhookKey(),
                "source", get(TOKEN) != null ? "ADMIN" : envToken != null && !envToken.isBlank() ? "ENV" : "NONE");
    }

    // body: { enabled, shopId, token?, apiUrl? }
    public Map<String, Object> update(Map<String, Object> body) {
        String shopId = str(body.get("shopId"));
        if (shopId != null && !shopId.matches("\\d{1,12}")) throw bad("Mã cửa hàng GHN (ShopId) phải là số");
        String api = str(body.get("apiUrl"));
        if (api != null && !api.matches("https://[^\\s]{4,300}|http://(localhost|127\\.0\\.0\\.1)(:\\d{2,5})?(/[^\\s]{0,200})?")) {
            throw bad("Địa chỉ API GHN phải là https://");
        }
        String token = str(body.get("token"));
        if (token != null && (token.length() < 10 || token.length() > 100)) throw bad("Token GHN không hợp lệ");
        if (token == null && get(TOKEN) == null && shopId != null) throw bad("Vui lòng nhập token GHN");
        put(ENABLED, String.valueOf(!Boolean.FALSE.equals(body.get("enabled"))));
        put(SHOP_ID, shopId);
        put(API_URL, api);
        if (token != null) put(TOKEN, cipher.encrypt(token));
        if (shopId == null && token == null) put(TOKEN, null);
        master.clear();
        fees.clear();
        auditService.log("UPDATE_SHIPPING_GATEWAY", "SETTING", null, "Giao Hàng Nhanh",
                (Boolean.FALSE.equals(body.get("enabled")) ? "Tắt" : "Bật") + (shopId != null ? " · ShopId " + shopId : "") + (token != null ? " · đổi token" : ""));
        return config();
    }

    /** Checks the token by loading GHN's province list. */
    public Map<String, Object> test() {
        Settings s = settings();
        if (!s.ready()) throw bad("Vui lòng nhập token và ShopId GHN trước");
        master.clear();
        int n = list(s, "province", null).size();
        return AdminUserService.map("ok", true, "provinces", n, "message", "Kết nối GHN thành công (" + n + " tỉnh/thành)");
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
