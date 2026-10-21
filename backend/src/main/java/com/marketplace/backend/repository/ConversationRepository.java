package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByBuyerIdAndShopId(Long buyerId, Long shopId);

    // threads with no message yet (opened from "Chat ngay" but never used) are left out
    @Query("SELECT c FROM Conversation c JOIN FETCH c.shop WHERE c.buyer.id = :buyerId AND c.lastMessage IS NOT NULL "
            + "ORDER BY c.lastMessageAt DESC")
    List<Conversation> forBuyer(@Param("buyerId") Long buyerId);

    @Query("SELECT c FROM Conversation c JOIN FETCH c.buyer WHERE c.shop.id = :shopId AND c.lastMessage IS NOT NULL "
            + "ORDER BY c.lastMessageAt DESC")
    List<Conversation> forShop(@Param("shopId") Long shopId);

    @Query("SELECT COALESCE(SUM(c.buyerUnread), 0) FROM Conversation c WHERE c.buyer.id = :buyerId")
    long buyerUnread(@Param("buyerId") Long buyerId);

    @Query("SELECT COALESCE(SUM(c.shopUnread), 0) FROM Conversation c WHERE c.shop.id = :shopId")
    long shopUnread(@Param("shopId") Long shopId);

    long countByShopIdAndShopUnreadGreaterThan(Long shopId, int unread);

    long countByShopIdAndAwaitingReplyTrue(Long shopId);
}
