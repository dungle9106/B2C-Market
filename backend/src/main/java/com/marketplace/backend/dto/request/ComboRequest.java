package com.marketplace.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

// "Combo khuyen mai": buy N+ of these products together, get X% off
@Data
public class ComboRequest {
    @NotBlank(message = "Vui lòng nhập tên combo")
    @Size(min = 3, max = 100, message = "Tên combo từ 3 đến 100 ký tự")
    private String name;

    @NotNull(message = "Vui lòng chọn thời gian bắt đầu")
    private LocalDateTime startAt;

    @NotNull(message = "Vui lòng chọn thời gian kết thúc")
    private LocalDateTime endAt;

    @Valid
    @NotEmpty(message = "Vui lòng thêm ít nhất một mức giảm")
    @Size(max = 3, message = "Tối đa 3 mức giảm")
    private List<TierRequest> tiers;

    @NotEmpty(message = "Vui lòng chọn sản phẩm cho combo")
    @Size(max = 100, message = "Tối đa 100 sản phẩm")
    private List<Long> productIds;

    @Data
    public static class TierRequest {
        @NotNull
        @Min(value = 2, message = "Số lượng mua tối thiểu từ 2")
        @Max(value = 100, message = "Số lượng mua tối đa 100")
        private Integer minQuantity;

        @NotNull
        @Min(value = 1, message = "Mức giảm từ 1%")
        @Max(value = 50, message = "Mức giảm tối đa 50%")
        private Integer discountPercent;
    }
}
