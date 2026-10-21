package com.marketplace.backend.dto.request;

import com.marketplace.backend.entity.VoucherType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Seller (shop voucher) or admin (platform voucher) creates a voucher.
@Data
public class VoucherRequest {

    @NotBlank(message = "Vui lòng nhập mã voucher")
    @Pattern(regexp = "[A-Za-z0-9]{4,20}", message = "Mã voucher gồm 4-20 chữ cái hoặc số, không dấu, không khoảng trắng")
    private String code;

    @NotNull(message = "Vui lòng chọn loại voucher")
    private VoucherType type;

    @NotNull(message = "Vui lòng nhập mức giảm")
    @Positive(message = "Mức giảm phải lớn hơn 0")
    private BigDecimal value;

    @Positive(message = "Mức giảm tối đa phải lớn hơn 0")
    private BigDecimal maxDiscount;

    @PositiveOrZero(message = "Giá trị đơn tối thiểu không hợp lệ")
    private BigDecimal minOrder;

    @PositiveOrZero(message = "Tổng lượt sử dụng không hợp lệ")
    private Integer usageLimit;     // 0 / null = unlimited

    @PositiveOrZero(message = "Lượt dùng mỗi người không hợp lệ")
    private Integer perUserLimit;   // 0 = unlimited, null = 1

    @NotNull(message = "Vui lòng chọn thời gian bắt đầu")
    private LocalDateTime startAt;

    @NotNull(message = "Vui lòng chọn thời gian kết thúc")
    private LocalDateTime endAt;

    // false = hidden voucher: not listed to buyers, works when the code is typed (null = true)
    private Boolean publicVisible;

    // shop vouchers only: the products it applies to; empty = the whole shop
    @Size(max = 100, message = "Tối đa 100 sản phẩm")
    private List<Long> productIds;

    // ---- Shoppi Vouchers only ----
    @Size(max = 50, message = "Tối đa 50 ngành hàng")
    private List<Long> categoryIds;    // empty = every category
    private Boolean newBuyerOnly;       // only buyers without a previous order
    private BigDecimal budget;          // Shoppi's spending cap, null = none
    private Long campaignId;            // the programme it belongs to
}
