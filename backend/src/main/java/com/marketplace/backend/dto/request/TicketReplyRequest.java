package com.marketplace.backend.dto.request;

import com.marketplace.backend.entity.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TicketReplyRequest {
    @NotBlank(message = "Vui lòng nhập nội dung")
    @Size(max = 4000, message = "Nội dung tối đa 4000 ký tự")
    private String content;

    @Size(max = 5, message = "Tối đa 5 tệp đính kèm")
    private List<String> attachmentUrls;

    // admin only: move the ticket to this status together with the reply
    private TicketStatus status;
}
