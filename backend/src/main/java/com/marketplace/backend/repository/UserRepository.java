package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Role;
import com.marketplace.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    // accounts under the automatic lock after too many wrong passwords
    List<User> findByLoginLockedUntilAfter(LocalDateTime now);
    boolean existsByEmail(String email);

    @Query("SELECT u.id FROM User u WHERE u.role = :role AND u.enabled = true")
    List<Long> findEnabledIdsByRole(@Param("role") Role role);

    // ---- admin: user management ----

    // status: ALL (not deleted), ACTIVE, LOCKED, DELETED; q (lower case) matches email, name or phone
    @Query("SELECT u FROM User u WHERE (:role IS NULL OR u.role = :role) "
            + "AND ((:status = 'ALL' AND u.deletedAt IS NULL) "
            + "  OR (:status = 'ACTIVE' AND u.deletedAt IS NULL AND u.enabled = true) "
            + "  OR (:status = 'LOCKED' AND u.deletedAt IS NULL AND u.enabled = false) "
            + "  OR (:status = 'DELETED' AND u.deletedAt IS NOT NULL)) "
            + "AND (:q = '' OR LOWER(u.email) LIKE CONCAT('%', :q, '%') OR LOWER(u.fullName) LIKE CONCAT('%', :q, '%') "
            + "  OR u.phone LIKE CONCAT('%', :q, '%'))")
    Page<User> search(@Param("role") Role role, @Param("status") String status, @Param("q") String q, Pageable pageable);

    long countByRoleAndDeletedAtIsNull(Role role);

    long countByEnabledFalseAndDeletedAtIsNull();

    long countByDeletedAtIsNotNull();

    // temporary locks whose end has passed
    List<User> findByEnabledFalseAndDeletedAtIsNullAndLockedUntilBefore(LocalDateTime now);

    List<User> findByRoleAndDeletedAtIsNullOrderByIdAsc(Role role);

    long countByRoleAndSuperAdminTrueAndDeletedAtIsNull(Role role);

    // platform analytics: [createdAt, role] of accounts opened in [from, to) (staff excluded)
    @Query("SELECT u.createdAt, u.role FROM User u WHERE u.createdAt >= :from AND u.createdAt < :to AND u.role <> :admin")
    List<Object[]> signups(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("admin") Role admin);

    long countByCreatedAtBeforeAndRoleNot(LocalDateTime before, Role role);
}
