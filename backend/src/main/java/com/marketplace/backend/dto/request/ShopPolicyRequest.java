package com.marketplace.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

// "Cai dat chinh sach shop": returns + shipping
@Data
public class ShopPolicyRequest {
    // 0 = no returns; otherwise 3 / 7 / 15 / 30 (checked in ShopService)
    @NotNull(message = "Vui lòng chọn thời hạn đổi trả")
    private Integer defaultReturnDays;

    @Size(max = 1000, message = "Điều kiện đổi trả tối đa 1000 ký tự")
    private String returnConditions;

    // also overwrite the return window of every existing product
    private boolean applyReturnDaysToAllProducts;

    @NotEmpty(message = "Vui lòng bật ít nhất một đơn vị vận chuyển")
    private List<String> carriers;

    @NotNull
    @Min(value = 1, message = "Thời gian chuẩn bị hàng từ 1 đến 3 ngày")
    @Max(value = 3, message = "Thời gian chuẩn bị hàng từ 1 đến 3 ngày")
    private Integer handlingDays;

    // null / 0 = shop doesn't sponsor shipping
    @PositiveOrZero(message = "Giá trị đơn tối thiểu không hợp lệ")
    private BigDecimal freeShipMinOrder;
}
