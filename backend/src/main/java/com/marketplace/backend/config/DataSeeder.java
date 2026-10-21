package com.marketplace.backend.config;

import com.marketplace.backend.entity.Category;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.Voucher;
import com.marketplace.backend.entity.VoucherScope;
import com.marketplace.backend.entity.VoucherType;
import com.marketplace.backend.util.TextNormalizer;
import com.marketplace.backend.repository.CategoryRepository;
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
    private static final Map<String, String> ACCENTED_CATEGORY_NAMES = new LinkedHashMap<>();
    static {
        ACCENTED_CATEGORY_NAMES.put("Thoi trang", "Thời trang");
        ACCENTED_CATEGORY_NAMES.put("Dien tu", "Điện tử");
        ACCENTED_CATEGORY_NAMES.put("Do gia dung", "Đồ gia dụng");
        ACCENTED_CATEGORY_NAMES.put("My pham", "Mỹ phẩm");
        ACCENTED_CATEGORY_NAMES.put("Sach & Van phong pham", "Sách & Văn phòng phẩm");
    }
}
