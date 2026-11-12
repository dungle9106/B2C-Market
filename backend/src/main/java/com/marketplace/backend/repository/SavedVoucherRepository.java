package com.marketplace.backend.repository;

import com.marketplace.backend.entity.SavedVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SavedVoucherRepository extends JpaRepository<SavedVoucher, Long> {

    boolean existsByUserIdAndVoucherId(Long userId, Long voucherId);

    @Query("SELECT s FROM SavedVoucher s JOIN FETCH s.voucher v LEFT JOIN FETCH v.shop WHERE s.user.id = :userId ORDER BY s.savedAt DESC")
    List<SavedVoucher> findMine(@Param("userId") Long userId);

    @Query("SELECT s.voucher.id FROM SavedVoucher s WHERE s.user.id = :userId")
    List<Long> savedIds(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM SavedVoucher s WHERE s.user.id = :userId AND s.voucher.id = :voucherId")
    int deleteMine(@Param("userId") Long userId, @Param("voucherId") Long voucherId);

    // saved, still running, ending within the window, not reminded yet
    @Query("SELECT s FROM SavedVoucher s JOIN FETCH s.voucher v WHERE s.remindedAt IS NULL AND v.active = true "
            + "AND v.endAt > :now AND v.endAt <= :until AND (v.startAt IS NULL OR v.startAt <= :now)")
    List<SavedVoucher> endingSoon(@Param("now") LocalDateTime now, @Param("until") LocalDateTime until);
}
