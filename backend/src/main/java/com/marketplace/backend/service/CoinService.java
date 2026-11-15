package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.CoinTransactionRepository;
import com.marketplace.backend.repository.CoinWalletRepository;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.RefundRepository;
import com.marketplace.backend.repository.ReturnRequestRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * "Shoppi Xu": the platform's loyalty coins, 1 xu = 1 VND off at checkout.
 *  - earned: daily check-in, and a % of what the buyer paid for the goods. The reward is fixed when the order is
 *    placed but only credited once the return window has passed, so a returned order earns nothing;
 *  - spent: up to a % of the checkout total, split across its orders. Shoppi funds the discount, so the shop's
 *    payout is unchanged (SettlementService never looks at it);
 *  - given back: when an order is cancelled or returned.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CoinService {

    public static final long[] CHECK_IN_REWARDS = {100, 100, 200, 100, 100, 100, 500};
    // the check-in day is the buyer's day, whatever timezone the server runs in
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<ReturnStatus> OPEN_RETURN = EnumSet.of(ReturnStatus.REQUESTED, ReturnStatus.ESCALATED);

    private final CoinWalletRepository walletRepository;
    private final CoinTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final ReturnRequestRepository returnRepository;
    private final PlatformConfig config;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public long balance(Long userId) {
        return walletRepository.findById(userId).map(CoinWallet::getBalance).orElse(0L);
    }

    /** Reward for paying `paid` VND of goods, with the buyer's membership bonus (100 = none). */
    public int earnedFor(BigDecimal paid, int multiplierPercent) {
        return paid.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(config.coinEarnPercent()))
                .multiply(BigDecimal.valueOf(multiplierPercent))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.DOWN).intValue();
    }

    /** Fails (rolling the checkout back) if the balance dropped since the preview. */
    public void spend(Long userId, long amount, String checkoutCode) {
        if (walletRepository.debit(userId, amount) == 0) {
            throw bad("Số dư Shoppi Xu đã thay đổi, vui lòng kiểm tra lại đơn hàng");
        }
        record(userId, CoinTransaction.Type.ORDER_SPEND, -amount, checkoutCode, "Dùng Xu thanh toán đơn hàng");
    }

    /** Gives back coins used on an order; ref must be unique per refund event. */
    public void giveBack(Order order, long amount, String ref, String note) {
        if (amount <= 0) return;
        Long buyerId = order.getBuyer().getId();
        walletRepository.credit(buyerId, amount);
        record(buyerId, CoinTransaction.Type.ORDER_REFUND, amount, ref, note);
    }

    // ==================== rewards of completed orders ====================

    // orders with no reward fixed at checkout (older than this feature) are never picked up
    @Scheduled(fixedDelay = 60 * 60_000, initialDelay = 120_000)
    public void awardDelivered() {
        LocalDateTime now = LocalDateTime.now();
        for (Order o : orderRepository.coinsDue(OrderStatus.DELIVERED, now.minusDays(config.settlementHoldDays()))) {
            if (returnRepository.existsByOrderIdAndStatusIn(o.getId(), OPEN_RETURN)) continue;
            if (orderRepository.markCoinsAwarded(o.getId(), now) == 0) continue; // another instance got there first
            long coins = rewardAfterRefunds(o);
            if (coins <= 0) continue;
            Long buyerId = o.getBuyer().getId();
            walletRepository.credit(buyerId, coins);
            record(buyerId, CoinTransaction.Type.ORDER_EARN, coins, o.getCode(), "Hoàn Xu đơn hàng " + o.getCode());
            notificationService.notify(buyerId, NotificationType.ORDER, "Bạn vừa nhận " + coins + " Shoppi Xu",
                    "Đơn hàng " + o.getCode() + " đã hoàn tất. Dùng Xu để giảm giá ở lần mua tiếp theo nhé!", "/account/coins");
        }
    }

    // a compromise refund leaves the order delivered: the reward shrinks with the money kept
    private long rewardAfterRefunds(Order o) {
        BigDecimal refunded = refundRepository.findByOrderIdOrderByIdAsc(o.getId()).stream()
                .filter(r -> r.getStatus() != Refund.Status.FAILED)
                .map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refunded.signum() == 0) return o.getCoinsToEarn();
        if (o.getTotal().signum() <= 0 || refunded.compareTo(o.getTotal()) >= 0) return 0;
        return o.getTotal().subtract(refunded).multiply(BigDecimal.valueOf(o.getCoinsToEarn()))
                .divide(o.getTotal(), 0, RoundingMode.DOWN).longValue();
    }

    // ==================== buyer ====================

    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        Long userId = CurrentUser.id();
        Optional<CoinWallet> wallet = walletRepository.findById(userId);
        return view(wallet.map(CoinWallet::getBalance).orElse(0L),
                wallet.map(CoinWallet::getStreak).orElse(0), wallet.map(CoinWallet::getLastCheckIn).orElse(null));
    }

    public Map<String, Object> checkIn() {
        Long userId = CurrentUser.id();
        LocalDate today = LocalDate.now(VIETNAM);
        walletRepository.credit(userId, 0); // makes sure the wallet exists
        CoinWallet wallet = walletRepository.findById(userId).orElseThrow();
        int streak = nextStreak(wallet.getStreak(), wallet.getLastCheckIn(), today);
        if (walletRepository.markCheckIn(userId, streak, today) == 0) throw bad("Hôm nay bạn đã điểm danh rồi");

        long reward = CHECK_IN_REWARDS[streak - 1];
        walletRepository.credit(userId, reward);
        record(userId, CoinTransaction.Type.CHECK_IN, reward, today.toString(), "Điểm danh ngày " + streak + " liên tiếp");
        Map<String, Object> m = view(wallet.getBalance() + reward, streak, today);
        m.put("reward", reward);
        return m;
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

    private Map<String, Object> view(long balance, int streak, LocalDate last) {
        LocalDate today = LocalDate.now(VIETNAM);
        boolean done = today.equals(last);
        // a broken (or finished) streak shows an empty week
        boolean alive = done || (today.minusDays(1).equals(last) && streak < CHECK_IN_REWARDS.length);
        int next = done ? streak : nextStreak(streak, last, today);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("balance", balance);
        m.put("checkedInToday", done);
        m.put("streak", alive ? streak : 0);
        m.put("todayReward", CHECK_IN_REWARDS[next - 1]);
        m.put("rewards", CHECK_IN_REWARDS);
        m.put("earnPercent", config.coinEarnPercent());
        m.put("maxSpendPercent", config.coinMaxSpendPercent());
        return m;
    }

    private static int nextStreak(int streak, LocalDate last, LocalDate today) {
        return today.minusDays(1).equals(last) && streak < CHECK_IN_REWARDS.length ? streak + 1 : 1;
    }

    /** Coins given by a platform activity (lucky spin...): credited and written in the history. */
    public void grant(Long userId, long amount, CoinTransaction.Type type, String ref, String note) {
        if (amount <= 0) return;
        walletRepository.credit(userId, amount);
        record(userId, type, amount, ref, note);
    }

    private void record(Long userId, CoinTransaction.Type type, long amount, String ref, String note) {
        transactionRepository.save(CoinTransaction.builder()
                .userId(userId).type(type).amount(amount).ref(ref).note(note).build());
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
