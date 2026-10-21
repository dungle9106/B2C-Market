package com.marketplace.backend.security;

import com.marketplace.backend.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Set;

/** The rules for a new password (registration, change, reset, new staff account). */
public final class PasswordPolicy {

    // the most used passwords (and their obvious variants) are refused
    private static final Set<String> COMMON = Set.of("12345678", "123456789", "1234567890", "password", "password1", "password123",
            "qwerty123", "abc12345", "abcd1234", "11111111", "iloveyou", "admin123", "admin@123", "matkhau123", "123456aa",
            "aa123456", "a1234567", "zxcvbnm1", "shoppi123", "12345678a", "qwertyuiop", "1q2w3e4r", "1qaz2wsx");

    private PasswordPolicy() {
    }

    /** Throws a 400 with the reason when the password is too weak. */
    public static void check(String password, String email) {
        if (password == null || password.length() < 8) throw bad("Mật khẩu tối thiểu 8 ký tự");
        if (password.length() > 100) throw bad("Mật khẩu tối đa 100 ký tự");
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) throw bad("Mật khẩu cần có cả chữ và số");
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) throw bad("Mật khẩu quá phổ biến, vui lòng chọn mật khẩu khác");
        if (email != null) {
            String local = email.toLowerCase(Locale.ROOT).split("@")[0];
            if (local.length() >= 4 && lower.contains(local)) throw bad("Mật khẩu không được chứa email của bạn");
        }
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
