package com.marketplace.backend.dto.request;

import com.marketplace.backend.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// One checkout = several shops' items; the backend creates one Order per shop.
// The same body is used for POST /api/orders/preview (quote only) and
// POST /api/orders/checkout (place the orders).
@Data
public class CheckoutRequest {

    @NotEmpty(message = "Vui lòng chọn sản phẩm để đặt hàng")
    @Valid
    private List<ShopPart> shops = new ArrayList<>();

    private Long addressId;               // required to place, optional for a preview

    private PaymentMethod paymentMethod;  // required to place
    private String paymentProvider;       // E_WALLET: MOMO | ZALOPAY | VNPAY

    private String platformVoucherCode;   // "Shoppi Voucher"

    @Data
    public static class ShopPart {
        @NotEmpty
        private List<Long> cartItemIds = new ArrayList<>();
        private String voucherCode;       // shop voucher
        private String shippingOption;    // ShippingService option code, e.g. GHN_EXPRESS; null = cheapest
        @Size(max = 500, message = "Lời nhắn tối đa 500 ký tự")
        private String note;
    }
}
