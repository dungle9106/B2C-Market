package com.marketplace.backend.dto.request;

import com.marketplace.backend.entity.BusinessType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

// what a seller submits to get the "Da xac minh" badge; type-specific rules are in ShopVerificationService
@Data
public class VerificationRequest {
    @NotNull(message = "Vui lòng chọn loại hình kinh doanh")
    private BusinessType businessType;

    @NotBlank(message = "Vui lòng nhập họ tên / tên doanh nghiệp")
    @Size(max = 200, message = "Tên tối đa 200 ký tự")
    private String legalName;

    @NotBlank(message = "Vui lòng nhập số CCCD")
    @Pattern(regexp = "^\\d{12}$", message = "Số CCCD phải gồm 12 chữ số")
    private String idNumber;

    @Pattern(regexp = "^$|^\\d{10}(-\\d{3})?$", message = "Mã số thuế gồm 10 chữ số (hoặc 10 chữ số - 3 chữ số với chi nhánh)")
    private String taxCode;

    @Size(max = 30, message = "Số giấy phép tối đa 30 ký tự")
    private String licenseNumber;

    @Size(max = 300, message = "Địa chỉ tối đa 300 ký tự")
    private String businessAddress;

    @Size(max = 6, message = "Tối đa 6 ảnh giấy tờ")
    private List<String> documentUrls;

    @AssertTrue(message = "Vui lòng xác nhận thông tin cung cấp là chính xác")
    private boolean confirmed;
}
