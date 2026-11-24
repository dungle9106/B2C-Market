package com.marketplace.backend.config;

import com.marketplace.backend.entity.Category;
import com.marketplace.backend.entity.PlatformSetting;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.Voucher;
import com.marketplace.backend.entity.VoucherScope;
import com.marketplace.backend.entity.VoucherType;
import com.marketplace.backend.util.TextNormalizer;
import com.marketplace.backend.repository.CategoryRepository;
import com.marketplace.backend.repository.PlatformSettingRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.VoucherRepository;
import com.marketplace.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Seeds a default admin account and a few top-level categories on first run,
// so the app is usable immediately after `mvn spring-boot:run` without
// manually inserting rows. Safe to run every startup: it checks before inserting.
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProductRepository productRepository;
    private final VoucherRepository voucherRepository;
    private final PlatformSettingRepository settingRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (!userRepository.existsByEmail("admin@marketplace.local")) {
            User admin = User.builder()
                    .email("admin@marketplace.local")
                    .password(passwordEncoder.encode("Admin@123"))
                    .fullName("Quản trị viên Shoppi")
                    .role(Role.ADMIN)
                    .enabled(true)
                    .superAdmin(true)
                    .build();
            userRepository.save(admin);
            System.out.println(">> Seeded default admin: admin@marketplace.local / Admin@123");
        }
        // databases from before staff permissions: the first admin becomes the owner (super admin)
        if (userRepository.countByRoleAndSuperAdminTrueAndDeletedAtIsNull(Role.ADMIN) == 0) {
            userRepository.findByRoleAndDeletedAtIsNullOrderByIdAsc(Role.ADMIN).stream().findFirst().ifPresent(a -> {
                a.setSuperAdmin(true);
                userRepository.save(a);
                System.out.println(">> " + a.getEmail() + " is now the super admin");
            });
        }

        if (categoryRepository.count() == 0) {
            List<String> defaults = List.copyOf(ACCENTED_CATEGORY_NAMES.values());
            defaults.forEach(name -> categoryRepository.save(Category.builder().name(name).build()));
            System.out.println(">> Seeded default categories: " + defaults);
        } else {
            // databases seeded by older versions still have the unaccented names
            categoryRepository.findAll().forEach(c -> {
                String accented = ACCENTED_CATEGORY_NAMES.get(c.getName());
                if (accented != null) {
                    c.setName(accented);
                    categoryRepository.save(c);
                }
            });
        }

        seedSubCategories();
        seedExtendedCatalog();
        seedPlatformVouchers();

        // products created before search normalisation existed
        productRepository.findBySearchTextIsNull().forEach(p -> {
            p.setSearchText(TextNormalizer.normalize(p.getName()));
            productRepository.save(p);
        });
    }

    // two "Shoppi Voucher" examples so checkout has something to apply on a fresh install
    private void seedPlatformVouchers() {
        if (!voucherRepository.findByScopeOrderByCreatedAtDesc(VoucherScope.PLATFORM).isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        voucherRepository.save(Voucher.builder().code("SHOPPI10").scope(VoucherScope.PLATFORM)
                .type(VoucherType.PERCENT).discountValue(new BigDecimal("10")).maxDiscountAmount(new BigDecimal("30000"))
                .minOrderAmount(new BigDecimal("100000")).usageLimit(1000).perUserLimit(3)
                .startAt(now).endAt(now.plusYears(1)).active(true).build());
        voucherRepository.save(Voucher.builder().code("FREESHIP15").scope(VoucherScope.PLATFORM)
                .type(VoucherType.SHIPPING).discountValue(new BigDecimal("15000"))
                .minOrderAmount(new BigDecimal("0")).usageLimit(0).perUserLimit(5)
                .startAt(now).endAt(now.plusYears(1)).active(true).build());
        System.out.println(">> Seeded platform vouchers: SHOPPI10, FREESHIP15");
    }

    // second level of the category tree, added once under each default category
    private void seedSubCategories() {
        SUB_CATEGORIES.forEach((parentName, children) ->
                categoryRepository.findByParentIsNull().stream()
                        .filter(c -> c.getName().equals(parentName))
                        .findFirst()
                        .filter(parent -> categoryRepository.findByParentId(parent.getId()).isEmpty())
                        .ifPresent(parent -> children.forEach(name ->
                                categoryRepository.save(Category.builder().name(name).parent(parent).build()))));
    }

    private static final Map<String, List<String>> SUB_CATEGORIES = new LinkedHashMap<>();
    static {
        SUB_CATEGORIES.put("Thời trang", List.of("Áo nam", "Áo nữ", "Quần", "Váy đầm", "Giày dép", "Túi ví & Phụ kiện"));
        SUB_CATEGORIES.put("Điện tử", List.of("Điện thoại", "Máy tính & Laptop", "Tai nghe & Loa", "Phụ kiện điện tử"));
        SUB_CATEGORIES.put("Đồ gia dụng", List.of("Nhà bếp", "Phòng ngủ", "Trang trí nhà cửa", "Dụng cụ vệ sinh"));
        SUB_CATEGORIES.put("Mỹ phẩm", List.of("Chăm sóc da", "Trang điểm", "Chăm sóc tóc", "Nước hoa"));
        SUB_CATEGORIES.put("Sách & Văn phòng phẩm", List.of("Sách", "Văn phòng phẩm", "Dụng cụ học tập"));
    }

    // old unaccented seed name -> display name
    // more top-level categories (with their sub-categories), added once: a key in platform_settings marks it done,
    // so categories the admin deletes or renames later are not brought back on the next start
    private static final String EXTENDED_CATALOG_KEY = "seed.categories.extended.v1";

    private void seedExtendedCatalog() {
        if (settingRepository.existsById(EXTENDED_CATALOG_KEY)) return;
        int order = categoryRepository.findByParentIsNull().size();
        for (Map.Entry<String, List<String>> e : EXTENDED_CATALOG.entrySet()) {
            String name = e.getKey();
            Category parent = categoryRepository.findByParentIsNull().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(name)).findFirst()
                    .orElseGet(() -> categoryRepository.save(Category.builder().name(name).build()));
            if (parent.getSortOrder() == null || parent.getSortOrder() == 0) {
                parent.setSortOrder(order++);
                categoryRepository.save(parent);
            }
            List<Category> existing = categoryRepository.findByParentId(parent.getId());
            for (String child : e.getValue()) {
                if (existing.stream().noneMatch(c -> c.getName().equalsIgnoreCase(child)))
                    categoryRepository.save(Category.builder().name(child).parent(parent).build());
            }
        }
        settingRepository.save(new PlatformSetting(EXTENDED_CATALOG_KEY, "done", LocalDateTime.now(), null));
        System.out.println(">> Seeded extended categories: " + EXTENDED_CATALOG.keySet());
    }

    private static final Map<String, List<String>> EXTENDED_CATALOG = new LinkedHashMap<>();
    static {
        EXTENDED_CATALOG.put("Thời trang", List.of());
        EXTENDED_CATALOG.put("Điện tử", List.of());
        EXTENDED_CATALOG.put("Máy ảnh & Máy quay phim", List.of("Máy ảnh", "Camera hành trình & Action cam", "Camera giám sát", "Ống kính & Phụ kiện máy ảnh"));
        EXTENDED_CATALOG.put("Đồng hồ", List.of("Đồng hồ nam", "Đồng hồ nữ", "Đồng hồ thông minh", "Phụ kiện đồng hồ"));
        EXTENDED_CATALOG.put("Thiết bị điện gia dụng", List.of("Đồ điện nhà bếp", "Máy lọc không khí & Quạt", "Máy hút bụi", "Bàn ủi & Máy may"));
        EXTENDED_CATALOG.put("Đồ gia dụng", List.of());
        EXTENDED_CATALOG.put("Mỹ phẩm", List.of());
        EXTENDED_CATALOG.put("Sức khỏe", List.of("Thực phẩm chức năng", "Vật tư y tế", "Chăm sóc cá nhân", "Dụng cụ hỗ trợ sức khỏe"));
        EXTENDED_CATALOG.put("Mẹ & Bé", List.of("Sữa & Đồ ăn dặm", "Tã bỉm", "Đồ dùng cho bé", "Đồ chơi cho bé"));
        EXTENDED_CATALOG.put("Thời trang trẻ em", List.of("Quần áo bé trai", "Quần áo bé gái", "Giày dép trẻ em"));
        EXTENDED_CATALOG.put("Balo & Túi ví", List.of("Balo", "Túi xách", "Ví", "Vali & Túi du lịch"));
        EXTENDED_CATALOG.put("Trang sức & Phụ kiện", List.of("Nhẫn", "Dây chuyền & Vòng cổ", "Bông tai", "Kính mắt"));
        EXTENDED_CATALOG.put("Thể thao & Du lịch", List.of("Dụng cụ tập luyện", "Quần áo thể thao", "Đồ cắm trại & Dã ngoại", "Xe đạp thể thao"));
        EXTENDED_CATALOG.put("Ô tô & Xe máy & Xe đạp", List.of("Phụ kiện ô tô", "Phụ kiện xe máy", "Mũ bảo hiểm", "Dầu nhớt & Chăm sóc xe"));
        EXTENDED_CATALOG.put("Bách hóa online", List.of("Đồ ăn vặt", "Đồ uống", "Gia vị & Nguyên liệu", "Đồ khô"));
        EXTENDED_CATALOG.put("Sách & Văn phòng phẩm", List.of());
        EXTENDED_CATALOG.put("Đồ chơi", List.of("Mô hình & Đồ sưu tầm", "Đồ chơi giáo dục", "Board game & Xếp hình"));
        EXTENDED_CATALOG.put("Chăm sóc thú cưng", List.of("Thức ăn thú cưng", "Phụ kiện thú cưng", "Vệ sinh cho thú cưng"));
        EXTENDED_CATALOG.put("Giặt giũ & Chăm sóc nhà cửa", List.of("Nước giặt & Nước xả", "Nước rửa chén", "Chất tẩy rửa", "Giấy & Khăn giấy"));
        EXTENDED_CATALOG.put("Dụng cụ & Thiết bị tiện ích", List.of("Dụng cụ cầm tay", "Dụng cụ điện", "Thiết bị chiếu sáng", "Thiết bị điện & Ổ cắm"));
        EXTENDED_CATALOG.put("Game & Giải trí", List.of("Máy chơi game", "Tay cầm & Phụ kiện game", "Nhạc cụ"));
    }

    private static final Map<String, String> ACCENTED_CATEGORY_NAMES = new LinkedHashMap<>();
    static {
        ACCENTED_CATEGORY_NAMES.put("Thoi trang", "Thời trang");
        ACCENTED_CATEGORY_NAMES.put("Dien tu", "Điện tử");
        ACCENTED_CATEGORY_NAMES.put("Do gia dung", "Đồ gia dụng");
        ACCENTED_CATEGORY_NAMES.put("My pham", "Mỹ phẩm");
        ACCENTED_CATEGORY_NAMES.put("Sach & Van phong pham", "Sách & Văn phòng phẩm");
    }
}
