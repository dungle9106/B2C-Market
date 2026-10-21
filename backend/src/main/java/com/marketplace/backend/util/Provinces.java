package com.marketplace.backend.util;

import java.util.List;

// Vietnam's 34 provincial-level units (after the July 2025 merger). Used for the
// shop's "ship from" location and the "Noi ban" (region) search filter.
// Keep in sync with frontend/src/utils/provinces.js.
public final class Provinces {

    private Provinces() {}

    public static final List<String> ALL = List.of(
            "Hà Nội", "TP. Hồ Chí Minh", "Hải Phòng", "Đà Nẵng", "Cần Thơ", "Huế",
            "An Giang", "Bắc Ninh", "Cà Mau", "Cao Bằng", "Đắk Lắk", "Điện Biên",
            "Đồng Nai", "Đồng Tháp", "Gia Lai", "Hà Tĩnh", "Hưng Yên", "Khánh Hòa",
            "Lai Châu", "Lâm Đồng", "Lạng Sơn", "Lào Cai", "Nghệ An", "Ninh Bình",
            "Phú Thọ", "Quảng Ngãi", "Quảng Ninh", "Quảng Trị", "Sơn La", "Tây Ninh",
            "Thái Nguyên", "Thanh Hóa", "Tuyên Quang", "Vĩnh Long"
    );

    public static boolean isValid(String province) {
        return province != null && ALL.contains(province);
    }
}
