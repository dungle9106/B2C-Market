package com.marketplace.backend.service;

import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Simulated payment gateway for E_WALLET / CARD / BANK_TRANSFER (no real money,
 * no real gateway). One payment covers every order of a checkout. Unpaid
 * checkouts are cancelled after PAYMENT_WINDOW_MINUTES, which restocks them.
 *
 * Card details are only validated (Luhn, expiry, CVV format) and never stored:
 * the Payment row keeps the brand and the last 4 digits. Test card
 * 4000 0000 0000 0002 is always declined so the failure path can be demoed.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {

    private static final String DECLINED_TEST_CARD = "4000000000000002";

    private final VnpayService vnpayService;
    private final PlatformConfig config;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final TrackingService trackingService;

    @Transactional(readOnly = true)
    public Map<String, Object> get(String checkoutCode) {
        List<Order> orders = myCheckout(checkoutCode);
        Order first = orders.get(0);
        Payment payment = first.getPayment();
        BigDecimal amount = orders.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("checkoutCode", checkoutCode);
        r.put("method", payment.getMethod());
        r.put("provider", payment.getProvider());
        r.put("status", summaryStatus(orders));
        r.put("amount", amount);
        r.put("expiresAt", first.getCreatedAt().plusMinutes(config.paymentWindowMinutes()));
        r.put("orders", orders.stream().map(o -> Map.of(
                "id", o.getId(), "code", o.getCode(), "shopName", o.getShop().getName(), "total", o.getTotal())).toList());
        // the last VNPay attempt's answer ("Bạn đã hủy giao dịch"...)
        if (payment.getGatewayMessage() != null) r.put("gatewayMessage", payment.getGatewayMessage());
        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER) {
            // demo receiving account; the transfer note lets an accountant match the payment
            r.put("bank", Map.of(
                    "bankName", "Vietcombank (tài khoản demo)",
                    "accountNumber", "0123 456 789",
                    "accountName", "CONG TY TNHH SHOPPI",
                    "transferNote", checkoutCode));
        }
        return r;
    }

    // noRollbackFor: an expired checkout is cancelled inside requireAwaiting() *and* reported as an
    // error; the cancellation must still be committed
    @Transactional(noRollbackFor = ApiException.class)
    public Map<String, Object> pay(String checkoutCode, Map<String, String> body) {
        List<Order> orders = myCheckout(checkoutCode);
        requireAwaiting(orders);
        Payment first = orders.get(0).getPayment();
        if (first.getMethod() == PaymentMethod.VNPAY) throw bad("Đơn hàng này thanh toán qua cổng VNPay");

        String last4 = null;
        String brand = first.getProvider();
        if (first.getMethod() == PaymentMethod.CARD) {
            String number = digits(body.get("cardNumber"));
            validateCard(number, body.get("expiry"), body.get("cvv"), body.get("cardHolder"));
            if (DECLINED_TEST_CARD.equals(number)) {
                // orders stay AWAITING_PAYMENT so the buyer can retry with another card
                throw new ApiException("Thẻ bị từ chối bởi ngân hàng phát hành. Vui lòng dùng thẻ khác.", HttpStatus.PAYMENT_REQUIRED);
            }
            last4 = number.substring(number.length() - 4);
            brand = cardBrand(number);
        }

        String txn = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();
        for (Order o : orders) {
            Payment p = o.getPayment();
            p.setStatus(PaymentStatus.PAID);
            p.setPaidAt(now);
            p.setTransactionId(txn);
            p.setProvider(brand);
            p.setCardLast4(last4);
            o.setStatus(OrderStatus.PENDING); // now visible to the seller as a new order
            orderRepository.save(o);
            trackingService.add(o, "PAID", "Thanh toán thành công qua " + paymentName(p), null);
        }
        return get(checkoutCode);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Map<String, Object> cancel(String checkoutCode) {
        List<Order> orders = myCheckout(checkoutCode);
        requireAwaiting(orders);
        orders.forEach(o -> orderService.cancel(o, "BUYER", "Người mua hủy giao dịch thanh toán"));
        return get(checkoutCode);
    }

    // every minute: give stock back for checkouts that were never paid
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void expireUnpaid() {
        List<Order> stale = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.AWAITING_PAYMENT, LocalDateTime.now().minusMinutes(config.paymentWindowMinutes()));
        if (stale.isEmpty()) return;
        // a VNPay checkout may have been paid without us hearing back (no IPN): ask VNPay before cancelling
        stale = stale.stream().filter(o -> {
            if (o.getPayment() == null || o.getPayment().getMethod() != PaymentMethod.VNPAY || o.getPayment().getGatewayTxnRef() == null) return true;
            try {
                vnpayService.query(o);
            } catch (Exception e) {
                log.warn("VNPay query failed for {}: {}", o.getCode(), e.getMessage());
            }
            return o.getStatus() == OrderStatus.AWAITING_PAYMENT;
        }).toList();
        if (stale.isEmpty()) return;
        stale.forEach(o -> orderService.cancel(o, "SYSTEM", "Quá thời hạn thanh toán " + config.paymentWindowMinutes() + " phút"));
        log.info("Cancelled {} unpaid order(s) after {} minutes", stale.size(), config.paymentWindowMinutes());
    }

    private List<Order> myCheckout(String checkoutCode) {
        List<Order> orders = orderRepository.findByCheckoutCodeAndBuyerId(checkoutCode, CurrentUser.id());
        if (orders.isEmpty()) throw new ApiException("Không tìm thấy giao dịch", HttpStatus.NOT_FOUND);
        return orders;
    }

    private void requireAwaiting(List<Order> orders) {
        Order first = orders.get(0);
        if (first.getPayment().getMethod() == PaymentMethod.COD) {
            throw new ApiException("Đơn hàng thanh toán khi nhận hàng, không cần thanh toán trước", HttpStatus.BAD_REQUEST);
        }
        if (orders.stream().anyMatch(o -> o.getStatus() != OrderStatus.AWAITING_PAYMENT)) {
            throw new ApiException("Giao dịch đã được xử lý hoặc đã hết hạn", HttpStatus.BAD_REQUEST);
        }
        if (first.getCreatedAt().plusMinutes(config.paymentWindowMinutes()).isBefore(LocalDateTime.now())) {
            orders.forEach(o -> orderService.cancel(o, "SYSTEM", "Quá thời hạn thanh toán " + config.paymentWindowMinutes() + " phút"));
            throw new ApiException("Đã quá thời hạn thanh toán, đơn hàng đã bị hủy", HttpStatus.BAD_REQUEST);
        }
    }

    private static String paymentName(Payment p) {
        String provider = p.getProvider() != null ? p.getProvider() : "";
        return switch (p.getMethod()) {
            case E_WALLET -> "ví " + provider;
            case CARD -> "thẻ " + provider + (p.getCardLast4() != null ? " •••• " + p.getCardLast4() : "");
            case BANK_TRANSFER -> "chuyển khoản ngân hàng";
            case VNPAY -> "VNPay";
            default -> p.getMethod().name();
        };
    }

    private static String summaryStatus(List<Order> orders) {
        if (orders.stream().allMatch(o -> o.getStatus() == OrderStatus.CANCELLED)) return "CANCELLED";
        if (orders.stream().anyMatch(o -> o.getStatus() == OrderStatus.AWAITING_PAYMENT)) return "AWAITING_PAYMENT";
        return "PAID";
    }

    private static void validateCard(String number, String expiry, String cvv, String holder) {
        if (holder == null || holder.isBlank()) throw bad("Vui lòng nhập tên chủ thẻ");
        if (number.length() < 13 || number.length() > 19 || !luhn(number)) throw bad("Số thẻ không hợp lệ");
        if (cardBrand(number) == null) throw bad("Chỉ hỗ trợ thẻ Visa, Mastercard hoặc JCB");
        if (expiry == null || !expiry.matches("(0[1-9]|1[0-2])/\\d{2}")) throw bad("Ngày hết hạn không hợp lệ (MM/YY)");
        YearMonth exp = YearMonth.of(2000 + Integer.parseInt(expiry.substring(3)), Integer.parseInt(expiry.substring(0, 2)));
        if (exp.isBefore(YearMonth.now())) throw bad("Thẻ đã hết hạn");
        if (cvv == null || !cvv.matches("\\d{3,4}")) throw bad("Mã CVV không hợp lệ");
    }

    static boolean luhn(String number) {
        int sum = 0;
        boolean dbl = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int d = number.charAt(i) - '0';
            if (dbl && (d *= 2) > 9) d -= 9;
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    static String cardBrand(String number) {
        if (number.startsWith("4")) return "VISA";
        int two = Integer.parseInt(number.substring(0, 2));
        int four = Integer.parseInt(number.substring(0, 4));
        if ((two >= 51 && two <= 55) || (four >= 2221 && four <= 2720)) return "MASTERCARD";
        if (four >= 3528 && four <= 3589) return "JCB";
        return null;
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
