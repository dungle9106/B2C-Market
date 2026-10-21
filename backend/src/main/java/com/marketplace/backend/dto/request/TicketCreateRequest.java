package com.marketplace.backend.dto.request;

import com.marketplace.backend.entity.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TicketCreateRequest {
    @NotNull(message = "Vui lòng chọn chủ đề")
    private TicketCategory category;

    @NotBlank(message = "Vui lòng nhập tiêu đề")
    @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
    private String subject;

    @NotBlank(message = "Vui lòng mô tả vấn đề")
    @Size(max = 4000, message = "Nội dung tối đa 4000 ký tự")
    private String content;

    private Long orderId;

    private Long shopId;

    @Size(max = 5, message = "Tối đa 5 tệp đính kèm")
    private List<String> attachmentUrls;
}
