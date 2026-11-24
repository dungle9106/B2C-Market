package com.marketplace.backend.service;

import com.marketplace.backend.entity.ShippingMethod;
import com.marketplace.backend.util.TextNormalizer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simulated shipping rates: a fee table by method and by whether the shop and the buyer are in the same province,
 * plus a per-carrier surcharge and extra days. Delivery dates are counted from today. Carriers connected through
 * their real API (service/shipping/CarrierGateway: GHN, GHTK) re-price their options with it at checkout.
 */
@Service
public class ShippingService {

    /** surcharge in VND (may be negative: a cheaper carrier), extraDays added to the delivery estimate. */
    public record Carrier(String code, String name, int surcharge, int extraDays, Set<ShippingMethod> methods, String note) {
        /** Instant couriers (Ahamove, GrabExpress) only deliver within the same city. */
        public boolean instantOnly() {
            return methods.equals(Set.of(ShippingMethod.SAME_DAY));
        }
    }

    private static final Set<ShippingMethod> STANDARD = Set.of(ShippingMethod.ECONOMY, ShippingMethod.EXPRESS);
    private static final Set<ShippingMethod> ALL = Set.of(ShippingMethod.ECONOMY, ShippingMethod.EXPRESS, ShippingMethod.SAME_DAY);

    public static final List<Carrier> CARRIERS = List.of(
            new Carrier("GHTK", "Giao Hàng Tiết Kiệm", 0, 0, STANDARD, "Giá rẻ, phủ toàn quốc"),
            new Carrier("GHN", "Giao Hàng Nhanh", 3000, 0, ALL, "Nhanh, có giao hỏa tốc nội tỉnh"),
            new Carrier("JT", "J&T Express", 1000, 0, STANDARD, "Lấy hàng tận nơi cả cuối tuần"),
            new Carrier("VTP", "Viettel Post", 2000, 0, ALL, "Mạng lưới bưu cục rộng"),
            new Carrier("NJV", "Ninja Van", 1500, 0, STANDARD, "Giao tiêu chuẩn toàn quốc"),
            new Carrier("SPX", "SPX Express", -1000, 1, STANDARD, "Giá rẻ nhất, giao chậm hơn 1 ngày"),
            new Carrier("VNP", "VNPost (EMS)", 2500, 1, STANDARD, "Bưu điện Việt Nam, phủ cả vùng sâu vùng xa"),
            new Carrier("AHA", "Ahamove", 5000, 0, Set.of(ShippingMethod.SAME_DAY), "Giao hỏa tốc trong 1–2 giờ, chỉ nội tỉnh"),
            new Carrier("GRAB", "GrabExpress", 8000, 0, Set.of(ShippingMethod.SAME_DAY), "Giao hỏa tốc trong 1–2 giờ, chỉ nội tỉnh")
    );

    // [same province, other province] base fee in VND
    private static final Map<ShippingMethod, int[]> BASE_FEE = Map.of(
            ShippingMethod.ECONOMY, new int[]{15000, 30000},
            ShippingMethod.EXPRESS, new int[]{22000, 40000},
            ShippingMethod.SAME_DAY, new int[]{40000, 0});
    // [same province, other province] delivery days {from, to}
    private static final Map<ShippingMethod, int[][]> DAYS = Map.of(
            ShippingMethod.ECONOMY, new int[][]{{2, 3}, {4, 6}},
            ShippingMethod.EXPRESS, new int[][]{{1, 2}, {2, 3}},
            ShippingMethod.SAME_DAY, new int[][]{{0, 0}, {0, 0}});
    private static final Map<ShippingMethod, String> METHOD_NAMES = Map.of(
            ShippingMethod.ECONOMY, "Tiết kiệm",
            ShippingMethod.EXPRESS, "Nhanh",
            ShippingMethod.SAME_DAY, "Hỏa tốc");
    private static final int FREE_ITEMS = 2;           // first 2 items included in the fee
    private static final int PER_EXTRA_ITEM = 2000;    // then +2k per extra item

    @Data
    @AllArgsConstructor
    public static class ShippingOption {
        private String code;          // "GHN_EXPRESS" - what the checkout request sends back
        private String carrier;
        private String carrierName;
        private ShippingMethod method;
        private String methodName;
        private BigDecimal fee;
        private LocalDate etaFrom;
        private LocalDate etaTo;
    }

    /** All options for one shop's parcel, cheapest first. */
    public List<ShippingOption> options(String shopProvince, String buyerProvince, int itemCount) {
        return options(shopProvince, buyerProvince, itemCount, carrierCodes(null), 1);
    }

    /**
     * Options limited to the carriers the shop ships with. handlingDays (the
     * shop's packing time, 1-3) pushes the delivery dates back: the base table
     * already assumes one day.
     */
    public List<ShippingOption> options(String shopProvince, String buyerProvince, int itemCount,
                                        List<String> carriers, int handlingDays) {
        boolean same = sameProvince(shopProvince, buyerProvince);
        int extra = Math.max(0, itemCount - FREE_ITEMS) * PER_EXTRA_ITEM;
        LocalDate today = LocalDate.now().plusDays(Math.max(0, handlingDays - 1));
        List<ShippingOption> list = new ArrayList<>();
        for (Carrier c : CARRIERS) {
            if (!carriers.contains(c.code())) continue;
            for (ShippingMethod m : ShippingMethod.values()) {
                if (!c.methods().contains(m)) continue;
                if (m == ShippingMethod.SAME_DAY && !same) continue; // same-day only within a province
                int fee = BASE_FEE.get(m)[same ? 0 : 1] + c.surcharge() + extra;
                int[] days = DAYS.get(m)[same ? 0 : 1];
                int late = m == ShippingMethod.SAME_DAY ? 0 : c.extraDays();
                list.add(new ShippingOption(c.code() + "_" + m.name(), c.code(), c.name(), m, METHOD_NAMES.get(m),
                        BigDecimal.valueOf(fee), today.plusDays(days[0] + late), today.plusDays(days[1] + late)));
            }
        }
        // only instant couriers picked and the buyer is in another province: offer the standard carriers instead
        if (list.isEmpty() && !carriers.containsAll(carrierCodes(null))) {
            return options(shopProvince, buyerProvince, itemCount, carrierCodes(null).stream()
                    .filter(code -> CARRIERS.stream().anyMatch(c -> c.code().equals(code) && !c.instantOnly())).toList(), handlingDays);
        }
        list.sort((a, b) -> a.getFee().compareTo(b.getFee()));
        return list;
    }

    /** Cheapest economy fee: what a "freeship" product covers. */
    public BigDecimal economyFee(List<ShippingOption> options) {
        return options.stream().filter(o -> o.getMethod() == ShippingMethod.ECONOMY)
                .map(ShippingOption::getFee).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    /** The shop's stored "GHN,JT" as codes; null / blank / nothing valid = every carrier. */
    public static List<String> carrierCodes(String csv) {
        List<String> all = CARRIERS.stream().map(Carrier::code).toList();
        if (csv == null || csv.isBlank()) return all;
        List<String> picked = all.stream().filter(code -> List.of(csv.split(",")).contains(code)).toList();
        return picked.isEmpty() ? all : picked;
    }

    public static boolean isCarrier(String code) {
        return CARRIERS.stream().anyMatch(c -> c.code().equals(code));
    }

    public static String carrierName(String code) {
        return CARRIERS.stream().filter(c -> c.code().equals(code)).map(Carrier::name).findFirst().orElse(code);
    }

    public static String methodName(ShippingMethod m) {
        return m == null ? null : METHOD_NAMES.get(m);
    }

    // tolerant compare: "TP. Hồ Chí Minh" == "Hồ Chí Minh" == "tp ho chi minh"
    public static boolean sameProvince(String a, String b) {
        String x = simplify(a);
        return x != null && x.equals(simplify(b));
    }

    private static String simplify(String province) {
        String n = TextNormalizer.normalize(province);
        if (n == null || n.isEmpty()) return null;
        return n.replaceAll("^(tp\\.?|thanh pho|tinh)\\s*", "").replace(".", "").trim();
    }
}
