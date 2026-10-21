package com.marketplace.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddressRequest {
    @NotBlank(message = "Vui lòng nhập tên người nhận")
    @Size(max = 60, message = "Tên người nhận tối đa 60 ký tự")
    private String recipientName;
    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "0\\d{9}", message = "Số điện thoại gồm 10 số, bắt đầu bằng 0")
    private String phone;
    @NotBlank(message = "Vui lòng nhập địa chỉ cụ thể")
    @Size(max = 200, message = "Địa chỉ tối đa 200 ký tự")
    private String addressLine;
    private String ward;
    private String district;
    private String province;
    // explicit name: Lombok's isDefault()/setDefault() would make Jackson expect "default"
    @JsonProperty("isDefault")
    private boolean isDefault;
}
