package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ChatMessage;
import com.marketplace.backend.entity.ChatSide;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // newest first; the service reverses them
    List<ChatMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    // "load older messages"
    List<ChatMessage> findByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId, Pageable pageable);

    // polling for new messages
    List<ChatMessage> findByConversationIdAndIdGreaterThanOrderByIdAsc(Long conversationId, Long afterId);

    long countByConversationId(Long conversationId);

    boolean existsByConversationIdAndAutoReplyTrueAndCreatedAtAfter(Long conversationId, LocalDateTime after);

    // the latest product card in a thread: what the buyer is probably asking about
    Optional<ChatMessage> findFirstByConversationIdAndProductIsNotNullOrderByIdDesc(Long conversationId);

    Optional<ChatMessage> findFirstByConversationIdAndSenderSideAndContentIsNotNullOrderByIdDesc(Long conversationId, ChatSide side);

    // response rate: [conversationId, senderSide, autoReply, createdAt] of the shop's threads, in order
    @Query("SELECT m.conversation.id, m.senderSide, m.autoReply, m.createdAt FROM ChatMessage m "
            + "WHERE m.conversation.shop.id = :shopId AND m.createdAt >= :since ORDER BY m.conversation.id, m.id")
    List<Object[]> timeline(@Param("shopId") Long shopId, @Param("since") LocalDateTime since);
}
