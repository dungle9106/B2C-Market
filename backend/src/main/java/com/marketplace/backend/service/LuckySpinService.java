package com.marketplace.backend.service;

import com.marketplace.backend.entity.CoinTransaction;
import com.marketplace.backend.entity.LuckySpin;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.LuckySpinRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Vong quay may man": one free turn a day for each buyer. The server draws the prize (SecureRandom, weighted);
 * the page only animates the wheel to the slot it is told. Coins come from a daily budget for the whole platform:
 * a prize that no longer fits is replaced by the biggest one that does, so a lucky burst cannot cost more than planned.
 */
@Service
@RequiredArgsConstructor
public class LuckySpinService {

    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String VOUCHER_CODE = "FREESHIP15";

    /** slot of the wheel; weights add up to 10 000 (= 100.00 %) */
    public record Prize(String code, String label, long coins, int weight, String color) {}

    static final List<Prize> PRIZES = List.of(
            new Prize("X10", "10 Xu", 10, 3000, "#ffd7c9"),
            new Prize("X100", "100 Xu", 100, 900, "#ee4d2d"),
            new Prize("X20", "20 Xu", 20, 2500, "#ffe9a8"),
            new Prize("FREESHIP", "Freeship 15K", 0, 800, "#26aa99"),
            new Prize("X50", "50 Xu", 50, 1800, "#ffd7c9"),
            new Prize("X2000", "2.000 Xu", 2000, 20, "#f5a623"),
            new Prize("NONE", "Chúc may mắn lần sau", 0, 830, "#eeeeee"),
            new Prize("X500", "500 Xu", 500, 150, "#ff7337"));
    static final Map<String, Prize> BY_CODE = PRIZES.stream().collect(Collectors.toMap(Prize::code, Function.identity()));

    private final SecureRandom random = new SecureRandom();
    private final LuckySpinRepository spinRepository;
    private final UserRepository userRepository;
    private final CoinService coinService;
    private final VoucherWalletService voucherWalletService;
    private final PlatformConfig config;

    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        Long userId = CurrentUser.id();
        LocalDate today = LocalDate.now(VIETNAM);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("prizes", PRIZES.stream().map(p -> Map.of("code", p.code(), "label", p.label(), "color", p.color(),
                "chance", p.weight() / 100.0)).toList());
        Optional<LuckySpin> mine = spinRepository.findByUserIdAndSpinDate(userId, today);
        m.put("spunToday", mine.isPresent());
        m.put("todayPrize", mine.map(this::view).orElse(null));
        m.put("history", spinRepository.findTop10ByUserIdOrderByIdDesc(userId).stream().map(this::view).toList());
        m.put("winners", winners());
        m.put("coinBalance", coinService.balance(userId));
        return m;
    }

    @Transactional
    public Map<String, Object> spin() {
        Long userId = CurrentUser.id();
        LocalDate today = LocalDate.now(VIETNAM);
        if (spinRepository.findByUserIdAndSpinDate(userId, today).isPresent()) throw bad("Hôm nay bạn đã quay rồi, quay lại vào ngày mai nhé!");

        spinRepository.lockBudget();
        Prize drawn = draw(random.nextInt(10_000));
        Prize prize = fitBudget(drawn, config.spinDailyBudget() - spinRepository.coinsGivenOn(today));

        LuckySpin s;
        try {
            s = spinRepository.saveAndFlush(LuckySpin.builder().userId(userId).spinDate(today).prizeCode(prize.code())
                    .coins(prize.coins()).drawnCode(drawn == prize ? null : drawn.code()).createdAt(LocalDateTime.now()).build());
        } catch (DataIntegrityViolationException e) {
            throw bad("Hôm nay bạn đã quay rồi, quay lại vào ngày mai nhé!");   // two taps at once: the second one loses
        }
        if (prize.coins() > 0) {
            coinService.grant(userId, prize.coins(), CoinTransaction.Type.LUCKY_SPIN, "LS" + s.getId(), "Vòng quay may mắn: " + prize.label());
        } else if (prize.code().equals("FREESHIP")) {
            try {
                voucherWalletService.saveByCode(VOUCHER_CODE);
                s.setVoucherCode(VOUCHER_CODE);
            } catch (ApiException e) {
                // the voucher ended: give coins instead of nothing
                Prize instead = BY_CODE.get("X20");
                s.setPrizeCode(instead.code());
                s.setCoins(instead.coins());
                s.setDrawnCode("FREESHIP");
                coinService.grant(userId, instead.coins(), CoinTransaction.Type.LUCKY_SPIN, "LS" + s.getId(), "Vòng quay may mắn: " + instead.label());
            }
        }
        Map<String, Object> m = new LinkedHashMap<>(view(s));
        m.put("slot", PRIZES.indexOf(BY_CODE.get(s.getPrizeCode())));
        m.put("coinBalance", coinService.balance(userId));
        return m;
    }

    /** weighted draw: r in [0, 10 000) */
    static Prize draw(int r) {
        int acc = 0;
        for (Prize p : PRIZES) {
            acc += p.weight();
            if (r < acc) return p;
        }
        return BY_CODE.get("NONE");
    }

    /** the drawn prize, or the biggest coin prize still inside today's budget, or "no luck" */
    static Prize fitBudget(Prize drawn, long left) {
        if (drawn.coins() <= left) return drawn;
        return PRIZES.stream().filter(p -> p.coins() > 0 && p.coins() <= left)
                .max(Comparator.comparingLong(Prize::coins)).orElse(BY_CODE.get("NONE"));
    }

    private Map<String, Object> view(LuckySpin s) {
        Prize p = BY_CODE.get(s.getPrizeCode());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", s.getPrizeCode());
        m.put("label", p == null ? s.getPrizeCode() : p.label());
        m.put("coins", s.getCoins());
        m.put("voucherCode", s.getVoucherCode());
        m.put("date", s.getSpinDate());
        m.put("createdAt", s.getCreatedAt());
        return m;
    }

    /** recent big wins, names masked ("Ng***h") */
    private List<Map<String, Object>> winners() {
        List<LuckySpin> wins = spinRepository.findTop10ByCoinsGreaterThanEqualOrderByIdDesc(500);
        Map<Long, String> names = userRepository.findAllById(wins.stream().map(LuckySpin::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u.getFullName() == null ? "" : u.getFullName()));
        return wins.stream().map(w -> Map.<String, Object>of("name", mask(names.getOrDefault(w.getUserId(), "")),
                "label", BY_CODE.get(w.getPrizeCode()).label(), "createdAt", w.getCreatedAt())).toList();
    }

    static String mask(String name) {
        String n = name.trim();
        if (n.length() <= 2) return n.isEmpty() ? "***" : n.charAt(0) + "***";
        return n.substring(0, 2) + "***" + n.charAt(n.length() - 1);
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
