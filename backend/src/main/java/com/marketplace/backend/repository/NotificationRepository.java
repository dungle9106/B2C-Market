package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Notification;
import com.marketplace.backend.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDescIdDesc(Long userId, NotificationType type, Pageable pageable);

    // [type, count] of unread notifications, for the badges
    @Query("SELECT n.type, COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.read = false GROUP BY n.type")
    List<Object[]> countUnreadByType(@Param("userId") Long userId);

    // an unread notification pointing at the same place (e.g. one chat thread) is updated instead of duplicated
    Optional<Notification> findFirstByUserIdAndLinkAndReadFalseOrderByIdDesc(Long userId, String link);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
    int markAllRead(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.type = :type AND n.read = false")
    int markAllRead(@Param("userId") Long userId, @Param("type") NotificationType type);

    // opening a chat thread / ticket also clears its notifications
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.link = :link AND n.read = false")
    int markReadByLink(@Param("userId") Long userId, @Param("link") String link);
}
