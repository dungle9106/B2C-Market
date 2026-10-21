package com.marketplace.backend.dto.response;

import com.marketplace.backend.service.ShippingService.ShippingOption;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Everything the checkout page shows before "Dat hang": computed by the backend
// so the numbers on screen are exactly what the order will charge.
@Data
public class CheckoutQuote {

    private List<ShopQuote> shops = new ArrayList<>();

    private VoucherResponse platformVoucher;
    private String platformVoucherError;   // why the typed code can't be used (preview only)

    private BigDecimal merchandiseTotal = BigDecimal.ZERO;
    private BigDecimal shippingFeeTotal = BigDecimal.ZERO;
    private BigDecimal shippingDiscountTotal = BigDecimal.ZERO;
    private BigDecimal shopDiscountTotal = BigDecimal.ZERO;
    private BigDecimal comboDiscountTotal = BigDecimal.ZERO;
    private BigDecimal platformDiscountTotal = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private int itemCount;

    @Data
    public static class ShopQuote {
        private Long shopId;
        private String shopName;
        private String shopProvince;
        private List<CartItemResponse> items = new ArrayList<>();
        private BigDecimal subtotal = BigDecimal.ZERO;
        private int quantity;

        private List<ShippingOption> shippingOptions = new ArrayList<>();
        private ShippingOption shipping;           // the selected option
        private BigDecimal shippingFee = BigDecimal.ZERO;
        private BigDecimal shippingDiscount = BigDecimal.ZERO;
        private boolean freeShippingProducts;      // every item is a freeship product
        private BigDecimal freeShipMinOrder;       // shop policy: shop pays shipping from this subtotal (null = off)
        private boolean freeShippingByShop;        // ...and this order reached it

        private VoucherResponse shopVoucher;
        private String shopVoucherError;
        private BigDecimal shopDiscount = BigDecimal.ZERO;
        private BigDecimal shopVoucherDiscount = BigDecimal.ZERO;  // what the shop voucher gave (goods or shipping)
        private BigDecimal comboDiscount = BigDecimal.ZERO;
        // [{ comboId, name, quantity, percent, nextMinQuantity?, nextPercent? }] for "mua them ... de giam ..."
        private List<Map<String, Object>> combos = new ArrayList<>();
        // problems the buyer must fix before ordering (flash sale limits); preview only
        private List<String> notices = new ArrayList<>();
        private BigDecimal platformDiscount = BigDecimal.ZERO;

        private BigDecimal total = BigDecimal.ZERO;
    }
}
