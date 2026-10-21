package com.marketplace.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ShopRequest {
    @NotBlank(message = "Vui lòng nhập tên shop")
    @Size(min = 3, max = 60, message = "Tên shop từ 3 đến 60 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả shop tối đa 1000 ký tự")
    private String description;

    private String logoUrl;
    private String coverUrl;

    @Size(max = 255, message = "Địa chỉ lấy hàng quá dài")
    private String pickupAddress;

    // optional for shops created before the field existed; checked when given
    @Pattern(regexp = "^$|^(0|\\+84)\\d{9,10}$", message = "Số điện thoại lấy hàng không hợp lệ")
    private String pickupPhone;

    @Size(max = 80, message = "Tên quận/huyện quá dài")
    private String pickupDistrict;
    @Size(max = 80, message = "Tên phường/xã quá dài")
    private String pickupWard;

    // must be one of util.Provinces.ALL (checked in ShopService)
    private String province;
}
