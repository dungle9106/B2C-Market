package com.marketplace.backend.service;

import com.marketplace.backend.entity.NotificationType;
import com.marketplace.backend.entity.PolicyVersion;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.PolicyVersionRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * "Chinh sach chung": the platform's terms, privacy, return, seller and prohibited-goods policies. Each
 * publication is a new version (the old ones are kept); readers always see the newest. Until an admin
 * publishes one, a built-in first version is shown.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PolicyService {

    record Builtin(String title, String audience, String content) {
    }

    // key -> default title, who it concerns, starter text
    static final Map<String, Builtin> POLICIES = new LinkedHashMap<>();

    static {
        POLICIES.put("TERMS", new Builtin("Điều khoản sử dụng", "ALL", """
                1. Shoppi là sàn thương mại điện tử kết nối người mua và người bán. Shoppi không trực tiếp bán các sản phẩm do người bán đăng.
                2. Người dùng chịu trách nhiệm bảo mật tài khoản và mọi hoạt động diễn ra trên tài khoản của mình.
                3. Nghiêm cấm gian lận đơn hàng, đánh giá ảo, giao dịch ngoài sàn và đăng nội dung vi phạm pháp luật.
                4. Shoppi có quyền khóa tài khoản, gỡ sản phẩm hoặc hủy đơn hàng vi phạm các điều khoản này.
                5. Tranh chấp giữa người mua và người bán được Shoppi xem xét dựa trên bằng chứng của hai bên; quyết định của Shoppi là cuối cùng."""));
        POLICIES.put("PRIVACY", new Builtin("Chính sách bảo mật", "ALL", """
                1. Shoppi thu thập họ tên, email, số điện thoại, địa chỉ giao hàng và lịch sử mua sắm để xử lý đơn hàng và cải thiện dịch vụ.
                2. Thông tin giao hàng chỉ được chia sẻ cho người bán và đơn vị vận chuyển của đơn hàng đó.
                3. Shoppi không lưu số thẻ thanh toán đầy đủ (chỉ 4 số cuối) và không bán dữ liệu cá nhân cho bên thứ ba.
                4. Người dùng có thể yêu cầu xóa tài khoản; dữ liệu đơn hàng được giữ theo quy định kế toán."""));
        POLICIES.put("RETURN", new Builtin("Chính sách đổi trả & hoàn tiền", "BUYER", """
                1. Người mua được yêu cầu trả hàng/hoàn tiền trong thời hạn đổi trả của sản phẩm, tính từ khi nhận hàng.
                2. Yêu cầu cần kèm hình ảnh hoặc video làm bằng chứng. Người bán phản hồi yêu cầu; nếu bị từ chối, người mua có thể khiếu nại lên Shoppi.
                3. Đơn thanh toán online được hoàn về phương thức thanh toán ban đầu; đơn COD được Shoppi chuyển khoản trong 3-5 ngày làm việc.
                4. Shoppi có thể hoàn toàn bộ, hoàn một phần hoặc từ chối hoàn tiền sau khi xem xét khiếu nại."""));
        POLICIES.put("SELLER", new Builtin("Quy định dành cho người bán", "SELLER", """
                1. Người bán cung cấp thông tin shop chính xác, xử lý đơn hàng đúng hạn và giao hàng đúng mô tả.
                2. Shoppi thu phí cố định trên giá trị hàng của đơn thành công (sau khuyến mãi do shop tài trợ); mức phí theo ngành hàng được công bố trong Kênh Người Bán.
                3. Tiền hàng được đối soát sau khi đơn giao thành công và hết thời gian giữ tiền; khoản hoàn tiền cho người mua được trừ vào kỳ đối soát.
                4. Shop vi phạm (hàng giả, gian lận, nhiều khiếu nại) có thể bị gỡ sản phẩm hoặc đình chỉ."""));
        POLICIES.put("PROHIBITED", new Builtin("Danh mục hàng cấm & hạn chế", "SELLER", """
                1. Hàng giả, hàng nhái, hàng vi phạm quyền sở hữu trí tuệ.
                2. Vũ khí, chất cháy nổ, ma túy và chất kích thích bị cấm.
                3. Thuốc kê đơn, động vật hoang dã và sản phẩm từ động vật quý hiếm.
                4. Sản phẩm có nội dung đồi trụy, bạo lực hoặc trái thuần phong mỹ tục."""));
    }

    private final PolicyVersionRepository repository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;

    /** Every policy in force (newest version), without the text. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        POLICIES.forEach((key, b) -> {
            Map<String, Object> m = view(key, repository.findFirstByPolicyKeyOrderByVersionDesc(key).orElse(null), false);
            m.put("versions", repository.findByPolicyKeyOrderByVersionDesc(key).size());
            out.add(m);
        });
        return out;
    }

    /** The policy in force, or one version (null = newest). */
    @Transactional(readOnly = true)
    public Map<String, Object> get(String key, Integer version) {
        String k = key(key);
        // version 1 is the built-in text (null = shown from POLICIES)
        PolicyVersion v = version == null ? repository.findFirstByPolicyKeyOrderByVersionDesc(k).orElse(null)
                : version == 1 ? null
                : repository.findByPolicyKeyAndVersion(k, version).orElseThrow(() -> new ApiException("Phiên bản không tồn tại", HttpStatus.NOT_FOUND));
        Map<String, Object> m = view(k, v, true);
        List<Map<String, Object>> history = new ArrayList<>(repository.findByPolicyKeyOrderByVersionDesc(k).stream().map(x -> AdminUserService.map(
                "version", x.getVersion(), "title", x.getTitle(), "changeNote", x.getChangeNote(), "publishedAt", x.getPublishedAt(),
                "publishedByName", x.getPublishedByName())).toList());
        if (!history.isEmpty()) {
            history.add(AdminUserService.map("version", 1, "title", POLICIES.get(k).title(), "changeNote", "Nội dung ban đầu",
                    "publishedAt", null, "publishedByName", null));
        }
        m.put("history", history);
        m.put("current", version == null || history.isEmpty() || ((Number) history.get(0).get("version")).intValue() == version);
        return m;
    }

    /** Publishes a new version; notify = tell the users it concerns. */
    public Map<String, Object> publish(String key, String title, String content, String changeNote, boolean notify) {
        String k = key(key);
        String t = title == null ? "" : title.trim();
        String c = content == null ? "" : content.strip();
        if (t.length() < 3 || t.length() > 200) throw bad("Tiêu đề từ 3 đến 200 ký tự");
        if (c.length() < 20) throw bad("Nội dung chính sách quá ngắn");
        if (c.length() > 50_000) throw bad("Nội dung tối đa 50.000 ký tự");
        String note = changeNote == null || changeNote.isBlank() ? null : changeNote.trim();
        if (note != null && note.length() > 500) throw bad("Ghi chú thay đổi tối đa 500 ký tự");
        PolicyVersion current = repository.findFirstByPolicyKeyOrderByVersionDesc(k).orElse(null);
        String curTitle = current != null ? current.getTitle() : POLICIES.get(k).title();
        String curContent = current != null ? current.getContent() : POLICIES.get(k).content().strip();
        if (curTitle.equals(t) && curContent.equals(c)) throw bad("Nội dung không thay đổi so với phiên bản hiện tại");
        int version = current == null ? 2 : current.getVersion() + 1;   // version 1 is the built-in text
        var me = userRepository.findById(CurrentUser.id()).orElseThrow();
        PolicyVersion v = repository.save(PolicyVersion.builder().policyKey(k).version(version).title(t).content(c).changeNote(note)
                .publishedBy(me.getId()).publishedByName(me.getFullName()).publishedAt(LocalDateTime.now()).build());
        int notified = 0;
        if (notify) {
            String audience = POLICIES.get(k).audience();
            List<Long> ids = new ArrayList<>();
            if (!audience.equals("SELLER")) ids.addAll(userRepository.findEnabledIdsByRole(Role.BUYER));
            if (!audience.equals("BUYER")) ids.addAll(userRepository.findEnabledIdsByRole(Role.SELLER));
            notified = notificationService.notifyMany(ids, NotificationType.SYSTEM, "Shoppi cập nhật " + t,
                    note != null ? note : "Phiên bản mới có hiệu lực từ hôm nay. Vui lòng xem chi tiết.", "/policies/" + k.toLowerCase());
        }
        auditService.log("PUBLISH_POLICY", "POLICY", v.getId(), t, "Phiên bản " + version + (note != null ? " – " + note : "")
                + (notify ? " · đã thông báo " + notified + " người dùng" : ""));
        Map<String, Object> m = view(k, v, true);
        m.put("notified", notified);
        return m;
    }

    private Map<String, Object> view(String key, PolicyVersion v, boolean withContent) {
        Builtin b = POLICIES.get(key);
        Map<String, Object> m = AdminUserService.map("key", key, "slug", key.toLowerCase(), "audience", b.audience(),
                "title", v == null ? b.title() : v.getTitle(), "version", v == null ? 1 : v.getVersion(),
                "changeNote", v == null ? null : v.getChangeNote(), "publishedAt", v == null ? null : v.getPublishedAt(),
                "publishedByName", v == null ? null : v.getPublishedByName(), "builtin", v == null);
        if (withContent) m.put("content", v == null ? b.content() : v.getContent());
        return m;
    }

    private static String key(String key) {
        String k = key == null ? "" : key.toUpperCase(Locale.ROOT);
        if (!POLICIES.containsKey(k)) throw new ApiException("Chính sách không tồn tại", HttpStatus.NOT_FOUND);
        return k;
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
