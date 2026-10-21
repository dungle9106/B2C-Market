package com.marketplace.backend.dto.request;

import com.marketplace.backend.entity.NotificationType;
import com.marketplace.backend.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BroadcastRequest {
    @NotNull
    private NotificationType type;

    // BUYER | SELLER | null = everyone
    private Role audience;

    @NotBlank(message = "Vui lòng nhập tiêu đề")
    @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
    private String title;

    @Size(max = 1000, message = "Nội dung tối đa 1000 ký tự")
    private String content;

    // in-app route only, e.g. /search?q=ao
    @Size(max = 300)
    @Pattern(regexp = "^(/.*)?$", message = "Đường dẫn phải bắt đầu bằng /")
    private String link;
}
