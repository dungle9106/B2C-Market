package com.marketplace.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Every self-registration creates a BUYER account (like Shopee: you sign up
// as a regular member first). ADMIN accounts are seeded separately, and a
// user becomes a SELLER afterwards via a separate "become a seller" step
// (see ShopController#becomeSeller), not by picking a role here.
@Data
public class RegisterRequest {

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String password;

    @NotBlank
    private String fullName;

    private String phone;
}
