package com.marketplace.backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** The e-mails Shoppi sends: subject + HTML (inline styles: e-mail clients ignore style sheets). */
final class EmailTemplates {

    record Mail(String subject, String html) {
    }

    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private EmailTemplates() {
    }

    static Mail test() {
        return new Mail("[Shoppi] Email thử nghiệm", page("Cấu hình email hoạt động",
                p("Đây là email thử nghiệm từ trang quản trị Shoppi. Nếu bạn nhận được email này, máy chủ SMTP đã được cấu hình đúng."), null, null));
    }

    static Mail verifyEmail(String name, String link) {
        return new Mail("[Shoppi] Xác thực địa chỉ email của bạn", page("Xác thực email",
                p("Chào " + esc(name) + ",") + p("Cảm ơn bạn đã đăng ký Shoppi. Nhấn nút bên dưới để xác thực địa chỉ email này. Liên kết có hiệu lực trong 24 giờ."),
                "Xác thực email", link) + note("Nếu bạn không đăng ký tài khoản Shoppi, hãy bỏ qua email này."));
    }

    static Mail resetPassword(String name, String link, String ip) {
        return new Mail("[Shoppi] Đặt lại mật khẩu", page("Đặt lại mật khẩu",
                p("Chào " + esc(name) + ",") + p("Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn (từ địa chỉ " + esc(ip)
                        + "). Liên kết có hiệu lực trong 30 phút và chỉ dùng được một lần."), "Đặt mật khẩu mới", link)
                + note("Nếu bạn không yêu cầu, hãy bỏ qua email này: mật khẩu của bạn không thay đổi."));
    }

    static Mail security(String type, String name, String device, String ip, Object minutes) {
        String now = LocalDateTime.now().format(T);
        return switch (type) {
            case "NEW_DEVICE_LOGIN" -> new Mail("[Shoppi] Đăng nhập từ thiết bị mới", page("Đăng nhập từ thiết bị mới",
                    p("Chào " + esc(name) + ", tài khoản của bạn vừa đăng nhập từ một thiết bị mới:") + facts(List.of("Thiết bị", esc(device),
                            "Địa chỉ IP", esc(ip), "Thời gian", now)) + p("Nếu không phải bạn, hãy đổi mật khẩu ngay và đăng xuất khỏi các thiết bị khác."),
                    "Kiểm tra bảo mật tài khoản", "/account/security"));
            case "ACCOUNT_TEMP_LOCKED" -> new Mail("[Shoppi] Tài khoản tạm khóa đăng nhập", page("Nhiều lần đăng nhập sai",
                    p("Chào " + esc(name) + ", tài khoản của bạn có nhiều lần nhập sai mật khẩu liên tiếp nên đã được tạm khóa đăng nhập trong "
                            + minutes + " phút.") + facts(List.of("Từ thiết bị", esc(device), "Địa chỉ IP", esc(ip), "Thời gian", now))
                            + p("Nếu đó không phải bạn, hãy đặt lại mật khẩu."), "Đặt lại mật khẩu", "/forgot-password"));
            case "PASSWORD_CHANGED", "PASSWORD_RESET" -> new Mail("[Shoppi] Mật khẩu đã được thay đổi", page("Mật khẩu đã thay đổi",
                    p("Chào " + esc(name) + ", mật khẩu tài khoản Shoppi của bạn vừa được " + ("PASSWORD_RESET".equals(type) ? "đặt lại" : "thay đổi")
                            + " lúc " + now + (device != null ? " từ " + esc(device) : "") + ". Các thiết bị khác đã được đăng xuất.")
                            + p("Nếu bạn không thực hiện thay đổi này, hãy đặt lại mật khẩu ngay và liên hệ bộ phận hỗ trợ."), "Đặt lại mật khẩu", "/forgot-password"));
            case "TWO_FACTOR_ENABLED" -> new Mail("[Shoppi] Đã bật xác thực 2 lớp", page("Xác thực 2 lớp đã bật",
                    p("Chào " + esc(name) + ", từ bây giờ mỗi lần đăng nhập bạn cần nhập mã từ ứng dụng xác thực. Hãy cất giữ các mã dự phòng ở nơi an toàn."), null, null));
            case "TWO_FACTOR_DISABLED" -> new Mail("[Shoppi] Đã tắt xác thực 2 lớp", page("Xác thực 2 lớp đã tắt",
                    p("Chào " + esc(name) + ", xác thực 2 lớp vừa được tắt lúc " + now + (device != null ? " từ " + esc(device) : "")
                            + ". Nếu không phải bạn, hãy đổi mật khẩu ngay."), "Bảo mật tài khoản", "/account/security"));
            default -> null;
        };
    }

    static Mail order(String stage, String name, String code, BigDecimal total, String shop, String detail, String link) {
        String money = total == null ? "" : NotificationService.vnd(total);
        return switch (stage) {
            case "PLACED" -> new Mail("[Shoppi] Đặt hàng thành công - " + code, page("Cảm ơn bạn đã đặt hàng!",
                    p("Chào " + esc(name) + ", đơn hàng của bạn đã được gửi đến người bán.") + facts(List.of("Mã đơn hàng", code, "Shop", esc(shop),
                            "Tổng thanh toán", money)), "Xem đơn hàng", link));
            case "PAID" -> new Mail("[Shoppi] Thanh toán thành công - " + code, page("Đã nhận thanh toán",
                    p("Chào " + esc(name) + ", Shoppi đã nhận thanh toán " + money + " cho đơn " + code + ".") + p(esc(detail)), "Xem đơn hàng", link));
            case "PICKED_UP" -> new Mail("[Shoppi] Đơn hàng đang được giao - " + code, page("Đơn hàng đang trên đường đến bạn",
                    p("Chào " + esc(name) + ", đơn " + code + " của " + esc(shop) + " đã được giao cho đơn vị vận chuyển.") + p(esc(detail)),
                    "Theo dõi đơn hàng", link));
            case "DELIVERED" -> new Mail("[Shoppi] Giao hàng thành công - " + code, page("Đơn hàng đã được giao",
                    p("Chào " + esc(name) + ", đơn " + code + " đã giao thành công. Hãy đánh giá sản phẩm để giúp người mua khác nhé!"),
                    "Đánh giá sản phẩm", link));
            case "CANCELLED" -> new Mail("[Shoppi] Đơn hàng đã bị hủy - " + code, page("Đơn hàng đã bị hủy",
                    p("Chào " + esc(name) + ", đơn " + code + " đã bị hủy.") + p("Lý do: " + esc(detail)), "Xem đơn hàng", link));
            case "REFUNDED" -> new Mail("[Shoppi] Hoàn tiền - " + code, page("Hoàn tiền cho đơn " + code,
                    p("Chào " + esc(name) + ", " + esc(detail)), "Xem chi tiết", link));
            default -> null;
        };
    }

    // ==================== layout ====================

    private static String page(String title, String body, String button, String link) {
        String btn = button == null ? "" : "<p style=\"margin:24px 0\"><a href=\"" + attr(link) + "\" style=\"background:#ee4d2d;color:#ffffff;"
                + "text-decoration:none;padding:12px 22px;border-radius:4px;font-weight:bold;display:inline-block\">" + esc(button) + "</a></p>";
        return "<!doctype html><html><body style=\"margin:0;background:#f5f5f5;font-family:Arial,Helvetica,sans-serif;color:#222\">"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f5f5f5;padding:24px 0\"><tr><td align=\"center\">"
                + "<table width=\"560\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#ffffff;border-radius:6px;overflow:hidden\">"
                + "<tr><td style=\"background:#ee4d2d;padding:18px 24px;color:#ffffff;font-size:22px;font-weight:bold\">Shoppi</td></tr>"
                + "<tr><td style=\"padding:24px\"><h2 style=\"margin:0 0 16px;font-size:20px\">" + esc(title) + "</h2>" + body + btn + "</td></tr>"
                + "<tr><td style=\"padding:14px 24px;background:#fafafa;color:#888;font-size:12px\">Email tự động từ Shoppi, vui lòng không trả lời. "
                + "Shoppi không bao giờ hỏi mật khẩu hay mã xác thực của bạn.</td></tr></table></td></tr></table></body></html>";
    }

    private static String p(String s) {
        return "<p style=\"margin:0 0 12px;line-height:1.6;font-size:15px\">" + s + "</p>";
    }

    private static String note(String s) {
        return "<p style=\"margin:0;color:#888;font-size:13px\">" + esc(s) + "</p>";
    }

    // label/value pairs
    private static String facts(List<String> kv) {
        StringBuilder sb = new StringBuilder("<table cellpadding=\"6\" style=\"border-collapse:collapse;margin:8px 0 14px;font-size:14px\">");
        for (int i = 0; i < kv.size(); i += 2) {
            sb.append("<tr><td style=\"color:#888\">").append(kv.get(i)).append("</td><td><b>").append(kv.get(i + 1)).append("</b></td></tr>");
        }
        return sb.append("</table>").toString();
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String attr(String s) {
        return esc(s);
    }
}
