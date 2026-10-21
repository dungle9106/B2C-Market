package com.marketplace.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Small one-off fixes that spring.jpa.hibernate.ddl-auto=update can't do by itself.
 * Every statement is idempotent, so this is safe to run on every startup.
 *
 * Hibernate creates a CHECK constraint listing the enum values when it first
 * creates a table, but never updates it afterwards. Once an enum gains a value
 * (e.g. OrderStatus.AWAITING_PAYMENT) inserts would be rejected, so the stale
 * constraints are dropped; the Java enum is the source of truth.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // before DataSeeder
@RequiredArgsConstructor
public class SchemaPatcher implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        exec("ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check");
        exec("ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_method_check");
        exec("ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check");

        // vouchers.discount_percent was replaced by type + discount_value
        if (columnExists("vouchers", "discount_percent")) {
            exec("ALTER TABLE vouchers ALTER COLUMN discount_percent DROP NOT NULL");
            exec("UPDATE vouchers SET discount_value = discount_percent, type = 'PERCENT' "
                    + "WHERE discount_value IS NULL AND discount_percent IS NOT NULL");
        }
    }

    private boolean columnExists(String table, String column) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = current_schema() "
                        + "AND table_name = ? AND column_name = ?", Integer.class, table, column);
        return n != null && n > 0;
    }

    private void exec(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception e) {
            log.warn("Schema patch skipped ({}): {}", sql, e.getMessage());
        }
    }
}
