package com.marketplace.backend.dto.request;

import com.marketplace.backend.entity.ReturnReason;
import com.marketplace.backend.entity.ReturnType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReturnCreateRequest {

    @NotNull(message = "Vui lòng chọn loại yêu cầu")
    private ReturnType type;

    @NotNull(message = "Vui lòng chọn lý do")
    private ReturnReason reason;

    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;

    @Size(max = 6, message = "Tối đa 6 ảnh/video bằng chứng")
    private List<String> evidenceUrls = new ArrayList<>();

    @NotEmpty(message = "Vui lòng chọn sản phẩm cần trả")
    @Valid
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        @NotNull
        private Long orderItemId;

        @NotNull @Min(1)
        private Integer quantity;
    }
}
