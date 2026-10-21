package com.marketplace.backend.entity;

// What a staff admin may do. Each one maps to a group of /api/admin/** paths (SecurityConfig)
// and to a section of the admin console. A super admin has all of them.
public enum AdminPermission {
    USERS("Quản lý người dùng"),
    SHOPS("Duyệt & quản lý shop"),
    PRODUCTS("Sản phẩm & danh mục"),
    ORDERS("Đơn hàng & tranh chấp"),
    FINANCE("Hoàn tiền & đối soát"),
    VOUCHERS("Marketing toàn sàn"),
    SUPPORT("Hỗ trợ khách hàng"),
    BROADCAST("Gửi thông báo"),
    REPORTS("Thống kê & báo cáo"),
    SETTINGS("Cấu hình & vận hành hệ thống"),
    STAFF("Nhân viên & phân quyền");

    private final String label;

    AdminPermission(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
