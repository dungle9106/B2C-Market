package com.marketplace.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.PlatformSettingRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.security.RequestInfo;
import com.marketplace.backend.security.SecretCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * VNPay payment gateway (API 2.1.0), sandbox or production:
 *  - payUrl(): the signed URL the buyer is sent to (HMAC-SHA512 over the sorted, URL-encoded parameters);
 *  - handleReturn(): VNPay sends the buyer back to the shop with the result (signature checked);
 *  - handleIpn(): VNPay's server confirms the payment to ours (signature, order, amount checked; idempotent);
 *  - query(): asks VNPay for a transaction's status (querydr), used before cancelling an unpaid checkout;
 *  - refund(): full or partial refund through VNPay's API.
 * Settings (TMN code, hash secret encrypted, URLs) come from the admin page, falling back to VNPAY_* variables.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class VnpayService {

    static final String TMN = "vnpay.tmnCode", SECRET = "vnpay.hashSecret", PAY_URL = "vnpay.payUrl", API_URL = "vnpay.apiUrl", ENABLED = "vnpay.enabled";
    static final String SANDBOX_PAY = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    static final String SANDBOX_API = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ObjectMapper JSON = new ObjectMapper();

    // VNPay answer codes the buyer may see
    static final Map<String, String> RESPONSE = Map.ofEntries(
            Map.entry("00", "Giao dịch thành công"),
            Map.entry("07", "Trừ tiền thành công nhưng giao dịch bị nghi ngờ"),
            Map.entry("09", "Thẻ/tài khoản chưa đăng ký dịch vụ Internet Banking"),
            Map.entry("10", "Xác thực thông tin thẻ/tài khoản không đúng quá 3 lần"),
            Map.entry("11", "Đã hết hạn chờ thanh toán"),
            Map.entry("12", "Thẻ/tài khoản bị khóa"),
            Map.entry("13", "Nhập sai mật khẩu xác thực giao dịch (OTP)"),
            Map.entry("24", "Bạn đã hủy giao dịch"),
            Map.entry("51", "Tài khoản không đủ số dư"),
            Map.entry("65", "Tài khoản đã vượt quá hạn mức giao dịch trong ngày"),
            Map.entry("75", "Ngân hàng thanh toán đang bảo trì"),
            Map.entry("79", "Nhập sai mật khẩu thanh toán quá số lần quy định"),
            Map.entry("99", "Lỗi không xác định"));

    private final OrderRepository orderRepository;
    private final PlatformSettingRepository settings;
    private final SecretCipher cipher;
    private final TrackingService trackingService;
    private final PlatformConfig config;
    private final AdminAuditService auditService;
    private final WebClient webClient;

    @Value("${VNPAY_TMN_CODE:}")
    private String envTmn;
    @Value("${VNPAY_HASH_SECRET:}")
    private String envSecret;
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    record Settings(boolean enabled, String tmnCode, String secret, String payUrl, String apiUrl) {
        boolean ready() {
            return enabled && tmnCode != null && !tmnCode.isBlank() && secret != null && !secret.isBlank();
        }
    }

    // ==================== paying ====================

    /** The VNPay page for this checkout (a new transaction reference each attempt). */
    public Map<String, Object> payUrl(String checkoutCode) {
        Settings s = settings();
        if (!s.ready()) throw new ApiException("Cổng thanh toán VNPay chưa được cấu hình", HttpStatus.SERVICE_UNAVAILABLE);
        List<Order> orders = orderRepository.findByCheckoutCodeAndBuyerId(checkoutCode, CurrentUser.id());
        if (orders.isEmpty()) throw new ApiException("Không tìm thấy giao dịch", HttpStatus.NOT_FOUND);
        Order first = orders.get(0);
        if (first.getPayment().getMethod() != PaymentMethod.VNPAY) throw bad("Đơn hàng không thanh toán qua VNPay");
        if (orders.stream().anyMatch(o -> o.getStatus() != OrderStatus.AWAITING_PAYMENT)) throw bad("Giao dịch đã được xử lý hoặc đã hết hạn");
        LocalDateTime expires = first.getCreatedAt().plusMinutes(config.paymentWindowMinutes());
        if (expires.isBefore(LocalDateTime.now())) throw bad("Đã quá thời hạn thanh toán");

        BigDecimal amount = total(orders);
        String txnRef = checkoutCode + "-" + (System.currentTimeMillis() % 100000);
        orders.forEach(o -> o.getPayment().setGatewayTxnRef(txnRef));
        String now = LocalDateTime.now(VN).format(TS);
        TreeMap<String, String> p = new TreeMap<>();
        p.put("vnp_Version", "2.1.0");
        p.put("vnp_Command", "pay");
        p.put("vnp_TmnCode", s.tmnCode());
        p.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        p.put("vnp_CurrCode", "VND");
        p.put("vnp_TxnRef", txnRef);
        p.put("vnp_OrderInfo", ascii("Thanh toan don hang " + checkoutCode));
        p.put("vnp_OrderType", "other");
        p.put("vnp_Locale", "vn");
        p.put("vnp_ReturnUrl", frontendUrl + "/payment/vnpay-return");
        p.put("vnp_IpAddr", RequestInfo.current().ip());
        p.put("vnp_CreateDate", now);
        p.put("vnp_ExpireDate", expires.atZone(ZoneId.systemDefault()).withZoneSameInstant(VN).format(TS));
        String query = encode(p);
        String url = s.payUrl() + "?" + query + "&vnp_SecureHash=" + hmac(s.secret(), query);
        return AdminUserService.map("url", url, "txnRef", txnRef, "amount", amount, "expiresAt", expires);
    }

    /** The buyer comes back from VNPay: check the signature, record the result. */
    public Map<String, Object> handleReturn(Map<String, String> params) {
        Settings s = settings();
        if (!verify(s, params)) throw bad("Chữ ký không hợp lệ: kết quả thanh toán có thể đã bị sửa");
        List<Order> orders = byTxnRef(params.get("vnp_TxnRef"));
        if (orders.isEmpty()) throw new ApiException("Không tìm thấy giao dịch", HttpStatus.NOT_FOUND);
        String code = params.getOrDefault("vnp_ResponseCode", "99");
        boolean success = "00".equals(code) && "00".equals(params.getOrDefault("vnp_TransactionStatus", "00"));
        // the IPN is the authority, but VNPay can't reach a server on localhost: the signed return confirms too
        if (success && amountMatches(orders, params)) confirm(orders, params);
        else if (!success) orders.forEach(o -> o.getPayment().setGatewayMessage(cut(RESPONSE.getOrDefault(code, "Mã lỗi " + code), 120)));
        Order first = orders.get(0);
        return AdminUserService.map("success", first.getPayment().getStatus() == PaymentStatus.PAID, "code", code,
                "message", RESPONSE.getOrDefault(code, "Giao dịch không thành công (mã " + code + ")"),
                "checkoutCode", first.getCheckoutCode(), "amount", total(orders), "transactionNo", params.get("vnp_TransactionNo"),
                "bankCode", params.get("vnp_BankCode"), "orders", orders.stream().map(o -> AdminUserService.map("id", o.getId(), "code", o.getCode())).toList(),
                "canRetry", orders.stream().allMatch(o -> o.getStatus() == OrderStatus.AWAITING_PAYMENT));
    }

    /** VNPay's server-to-server confirmation; answers in VNPay's format. */
    public Map<String, String> handleIpn(Map<String, String> params) {
        try {
            if (!verify(settings(), params)) return ipn("97", "Invalid Checksum");
            List<Order> orders = byTxnRef(params.get("vnp_TxnRef"));
            if (orders.isEmpty()) return ipn("01", "Order not Found");
            if (!amountMatches(orders, params)) return ipn("04", "Invalid amount");
            if (orders.stream().anyMatch(o -> o.getPayment().getStatus() == PaymentStatus.PAID || o.getStatus() != OrderStatus.AWAITING_PAYMENT)) {
                return ipn("02", "Order already confirmed");
            }
            if ("00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"))) confirm(orders, params);
            else orders.forEach(o -> o.getPayment().setGatewayMessage(cut(RESPONSE.getOrDefault(params.get("vnp_ResponseCode"), "Thanh toán thất bại"), 120)));
            return ipn("00", "Confirm Success");
        } catch (Exception e) {
            log.warn("VNPay IPN failed: {}", e.getMessage());
            return ipn("99", "Unknow error");
        }
    }

    private void confirm(List<Order> orders, Map<String, String> params) {
        LocalDateTime now = LocalDateTime.now();
        for (Order o : orders) {
            Payment p = o.getPayment();
            if (p.getStatus() == PaymentStatus.PAID) continue;   // return + IPN both arrive: once is enough
            p.setStatus(PaymentStatus.PAID);
            p.setPaidAt(now);
            p.setProvider("VNPAY");
            p.setTransactionId(params.get("vnp_TransactionNo"));
            p.setGatewayTransactionNo(params.get("vnp_TransactionNo"));
            p.setGatewayPayDate(params.get("vnp_PayDate"));
            p.setGatewayBankCode(params.get("vnp_BankCode"));
            if (o.getStatus() != OrderStatus.AWAITING_PAYMENT) {
                // the money arrived after the checkout was cancelled: keep the order cancelled, the payment
                // shows up in "Doi soat thanh toan" (paid but cancelled) so finance refunds it
                p.setGatewayMessage("Thanh toán đến sau khi đơn đã hủy – cần hoàn tiền");
                continue;
            }
            p.setGatewayMessage(null);
            o.setStatus(OrderStatus.PENDING);
            trackingService.add(o, "PAID", "Thanh toán thành công qua VNPay" + (params.get("vnp_BankCode") != null ? " (" + params.get("vnp_BankCode") + ")" : ""), null);
        }
    }

    // ==================== query & refund (server to server) ====================

    /** Asks VNPay whether the transaction of this order was paid; returns the transaction status code or null. */
    // a refusal is reported to the caller, which decides; it must not doom the caller's transaction
    @Transactional(noRollbackFor = Exception.class)
    public String query(Order o) {
        Settings s = settings();
        Payment p = o.getPayment();
        if (!s.ready() || p.getGatewayTxnRef() == null) return null;
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String createDate = LocalDateTime.now(VN).format(TS);
        String txnDate = o.getCreatedAt().atZone(ZoneId.systemDefault()).withZoneSameInstant(VN).format(TS);
        String ip = "127.0.0.1", info = ascii("Kiem tra giao dich " + o.getCheckoutCode());
        String hash = hmac(s.secret(), String.join("|", requestId, "2.1.0", "querydr", s.tmnCode(), p.getGatewayTxnRef(), txnDate, createDate, ip, info));
        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", "2.1.0");
        body.put("vnp_Command", "querydr");
        body.put("vnp_TmnCode", s.tmnCode());
        body.put("vnp_TxnRef", p.getGatewayTxnRef());
        body.put("vnp_OrderInfo", info);
        body.put("vnp_TransactionDate", txnDate);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", ip);
        body.put("vnp_SecureHash", hash);
        JsonNode r = post(s.apiUrl(), body);
        if (r == null || !"00".equals(r.path("vnp_ResponseCode").asText())) return null;
        String status = r.path("vnp_TransactionStatus").asText();
        List<Order> sameTxn = byTxnRef(p.getGatewayTxnRef());
        // paid, for the whole checkout's amount
        if ("00".equals(status) && r.path("vnp_Amount").asText().equals(total(sameTxn).multiply(BigDecimal.valueOf(100)).toBigInteger().toString())) {
            Map<String, String> params = new HashMap<>();
            params.put("vnp_TransactionNo", r.path("vnp_TransactionNo").asText(null));
            params.put("vnp_PayDate", r.path("vnp_PayDate").asText(null));
            params.put("vnp_BankCode", r.path("vnp_BankCode").asText(null));
            confirm(sameTxn, params);
        }
        return status;
    }

    /** Refunds through VNPay; returns VNPay's refund transaction number, or throws with VNPay's message. */
    // a refusal is reported to the caller, which decides; it must not doom the caller's transaction
    @Transactional(noRollbackFor = Exception.class)
    public String refund(Order o, BigDecimal amount, String note) {
        Settings s = settings();
        Payment p = o.getPayment();
        if (!s.ready()) throw new IllegalStateException("VNPay chưa được cấu hình");
        if (p.getGatewayTransactionNo() == null) throw new IllegalStateException("Thiếu mã giao dịch VNPay");
        List<Order> sameTxn = byTxnRef(p.getGatewayTxnRef());
        boolean full = amount.compareTo(total(sameTxn)) >= 0;
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String createDate = LocalDateTime.now(VN).format(TS);
        String amt = amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString();
        String by = "shoppi", ip = "127.0.0.1", info = ascii("Hoan tien don " + o.getCode());
        String type = full ? "02" : "03";
        String hash = hmac(s.secret(), String.join("|", requestId, "2.1.0", "refund", s.tmnCode(), type, p.getGatewayTxnRef(), amt,
                p.getGatewayTransactionNo(), p.getGatewayPayDate(), by, createDate, ip, info));
        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", "2.1.0");
        body.put("vnp_Command", "refund");
        body.put("vnp_TmnCode", s.tmnCode());
        body.put("vnp_TransactionType", type);
        body.put("vnp_TxnRef", p.getGatewayTxnRef());
        body.put("vnp_Amount", amt);
        body.put("vnp_OrderInfo", info);
        body.put("vnp_TransactionNo", p.getGatewayTransactionNo());
        body.put("vnp_TransactionDate", p.getGatewayPayDate());
        body.put("vnp_CreateBy", by);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", ip);
        body.put("vnp_SecureHash", hash);
        JsonNode r = post(s.apiUrl(), body);
        if (r == null) throw new IllegalStateException("VNPay không phản hồi");
        if (!"00".equals(r.path("vnp_ResponseCode").asText())) {
            throw new IllegalStateException("VNPay từ chối hoàn tiền (mã " + r.path("vnp_ResponseCode").asText() + "): " + r.path("vnp_Message").asText(""));
        }
        return "VNP-RF-" + r.path("vnp_TransactionNo").asText(requestId);
    }

    private JsonNode post(String url, Map<String, String> body) {
        try {
            String raw = webClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).bodyValue(body).retrieve()
                    .bodyToMono(String.class).block(Duration.ofSeconds(30));
            return JSON.readTree(raw);
        } catch (Exception e) {
            log.warn("VNPay API call failed: {}", e.getMessage());
            return null;
        }
    }

    // ==================== admin settings ====================

    @Transactional(readOnly = true)
    public Map<String, Object> config() {
        Settings s = settings();
        return AdminUserService.map("enabled", s.enabled(), "tmnCode", s.tmnCode(), "secretSet", s.secret() != null && !s.secret().isBlank(),
                "payUrl", s.payUrl(), "apiUrl", s.apiUrl(), "ready", s.ready(), "sandbox", s.payUrl().contains("sandbox"),
                "returnUrl", frontendUrl + "/payment/vnpay-return", "ipnUrl", "<địa chỉ backend công khai>/api/payments/vnpay/ipn",
                "source", get(TMN) != null ? "ADMIN" : envTmn != null && !envTmn.isBlank() ? "ENV" : "NONE");
    }

    /** body: { enabled, tmnCode, hashSecret?, payUrl?, apiUrl? } */
    public Map<String, Object> update(Map<String, Object> body) {
        String tmn = str(body.get("tmnCode"));
        if (tmn != null && !tmn.matches("[A-Za-z0-9]{4,20}")) throw bad("Mã website (TMN code) không hợp lệ");
        String pay = str(body.get("payUrl")), api = str(body.get("apiUrl"));
        for (String u : new String[]{pay, api}) {
            if (u != null && !u.matches("https://[^\\s]{4,300}|http://(localhost|127\\.0\\.0\\.1)(:\\d{2,5})?/[^\\s]{0,200}")) throw bad("Địa chỉ VNPay phải là https://");
        }
        put(ENABLED, String.valueOf(!Boolean.FALSE.equals(body.get("enabled"))));
        put(TMN, tmn);
        put(PAY_URL, pay);
        put(API_URL, api);
        String secret = str(body.get("hashSecret"));
        if (secret != null) {
            if (secret.length() < 8 || secret.length() > 100) throw bad("Chuỗi bí mật (hash secret) không hợp lệ");
            put(SECRET, cipher.encrypt(secret));
        }
        auditService.log("UPDATE_PAYMENT_GATEWAY", "SETTING", null, "Cổng VNPay",
                (Boolean.FALSE.equals(body.get("enabled")) ? "Tắt" : "Bật") + (tmn != null ? " · TMN " + tmn : "") + (secret != null ? " · đổi hash secret" : ""));
        return config();
    }

    /** Is VNPay offered at checkout? */
    @Transactional(readOnly = true)
    public boolean available() {
        return settings().ready();
    }

    // ==================== signatures ====================

    boolean verify(Settings s, Map<String, String> params) {
        String given = params.get("vnp_SecureHash");
        if (given == null || !s.ready()) return false;
        TreeMap<String, String> p = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType") && v != null && !v.isEmpty()) p.put(k, v);
        });
        return java.security.MessageDigest.isEqual(hmac(s.secret(), encode(p)).getBytes(StandardCharsets.US_ASCII),
                given.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    // key=value&... sorted by key, URL-encoded (VNPay signs exactly this string)
    static String encode(SortedMap<String, String> p) {
        StringBuilder sb = new StringBuilder();
        p.forEach((k, v) -> {
            if (v == null || v.isEmpty()) return;
            if (!sb.isEmpty()) sb.append('&');
            sb.append(URLEncoder.encode(k, StandardCharsets.US_ASCII)).append('=').append(URLEncoder.encode(v, StandardCharsets.US_ASCII));
        });
        return sb.toString();
    }

    static String hmac(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ==================== helpers ====================

    Settings settings() {
        boolean admin = get(TMN) != null;
        return new Settings(!"false".equals(get(ENABLED)), admin ? get(TMN) : envTmn,
                admin ? cipher.decrypt(get(SECRET)) : envSecret,
                Objects.requireNonNullElse(get(PAY_URL), SANDBOX_PAY), Objects.requireNonNullElse(get(API_URL), SANDBOX_API));
    }

    private List<Order> byTxnRef(String txnRef) {
        if (txnRef == null || !txnRef.contains("-")) return List.of();
        String checkout = txnRef.substring(0, txnRef.lastIndexOf('-'));
        return orderRepository.findByCheckoutCode(checkout).stream().filter(o -> txnRef.equals(o.getPayment().getGatewayTxnRef())).toList();
    }

    private static boolean amountMatches(List<Order> orders, Map<String, String> params) {
        return total(orders).multiply(BigDecimal.valueOf(100)).toBigInteger().toString().equals(params.get("vnp_Amount"));
    }

    private static BigDecimal total(List<Order> orders) {
        return orders.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<String, String> ipn(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }

    // VNPay asks for an order description without Vietnamese accents
    static String ascii(String s) {
        return Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9 ]", " ");
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

    private static String cut(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
