package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.ShoppiPayTransaction;
import com.marketplace.backend.entity.ShoppiPayWallet;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ShoppiPayTransactionRepository;
import com.marketplace.backend.repository.ShoppiPayWalletRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * "Vi ShoppiPay": a stored balance the buyer pays checkouts with, in one step and without a gateway.
 * Refunds of orders paid this way come back to the wallet at once. Top-ups are simulated, like the
 * other test payment methods (PaymentService), so no real money moves.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ShoppiPayService {

    public static final BigDecimal MIN_TOP_UP = new BigDecimal("10000");
    public static final BigDecimal MAX_TOP_UP = new BigDecimal("20000000");
    public static final BigDecimal MAX_BALANCE = new BigDecimal("100000000");

    private final ShoppiPayWalletRepository walletRepository;
    private final ShoppiPayTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public BigDecimal balance(Long userId) {
        return walletRepository.findById(userId).map(ShoppiPayWallet::getBalance).orElse(BigDecimal.ZERO);
    }

    /** Fails (rolling the checkout back) when the balance can't cover it. */
    public void pay(Long userId, BigDecimal amount, String checkoutCode) {
        if (amount.signum() <= 0) return;
        if (walletRepository.debit(userId, amount) == 0) throw bad("Số dư ví ShoppiPay không đủ để thanh toán");
        record(userId, ShoppiPayTransaction.Type.PAYMENT, amount.negate(), checkoutCode, "Thanh toán đơn hàng");
    }

    /** Puts the money of a refund back; returns the refund's reference. */
    public String refund(Order order, BigDecimal amount) {
        String reference = "WL-RF-" + token(8);
        Long buyerId = order.getBuyer().getId();
        walletRepository.credit(buyerId, amount);
        record(buyerId, ShoppiPayTransaction.Type.REFUND, amount, reference, "Hoàn tiền đơn " + order.getCode());
        return reference;
    }

    // ==================== buyer ====================

    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        return view(balance(CurrentUser.id()));
    }

    public Map<String, Object> topUp(BigDecimal amount) {
        if (amount == null || amount.stripTrailingZeros().scale() > 0) throw bad("Số tiền nạp không hợp lệ");
        if (amount.compareTo(MIN_TOP_UP) < 0 || amount.compareTo(MAX_TOP_UP) > 0) {
            throw bad("Mỗi lần nạp từ " + NotificationService.vnd(MIN_TOP_UP) + " đến " + NotificationService.vnd(MAX_TOP_UP));
        }
        Long userId = CurrentUser.id();
        walletRepository.credit(userId, BigDecimal.ZERO); // makes sure the wallet exists
        if (walletRepository.topUp(userId, amount, MAX_BALANCE) == 0) {
            throw bad("Số dư ví tối đa là " + NotificationService.vnd(MAX_BALANCE));
        }
        record(userId, ShoppiPayTransaction.Type.TOP_UP, amount, "TU" + token(12), "Nạp tiền vào ví (mô phỏng)");
        return view(balance(userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> history(int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by("id").descending());
        return PageResponse.of(transactionRepository.findByUserId(CurrentUser.id(), pageable), t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("type", t.getType().name());
            m.put("amount", t.getAmount());
            m.put("note", t.getNote());
            m.put("createdAt", t.getCreatedAt());
            return m;
        });
    }

    private static Map<String, Object> view(BigDecimal balance) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("balance", balance);
        m.put("minTopUp", MIN_TOP_UP);
        m.put("maxTopUp", MAX_TOP_UP);
        m.put("maxBalance", MAX_BALANCE);
        return m;
    }

    private void record(Long userId, ShoppiPayTransaction.Type type, BigDecimal amount, String ref, String note) {
        transactionRepository.save(ShoppiPayTransaction.builder()
                .userId(userId).type(type).amount(amount).ref(ref).note(note).build());
    }

    private static String token(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length).toUpperCase(Locale.ROOT);
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
