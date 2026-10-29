package kr.fintarget.api.domain.ai.chat.repository;

import kr.fintarget.api.domain.ai.chat.entity.AiConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AiConversationMessageRepository extends JpaRepository<AiConversationMessage, UUID> {

    List<AiConversationMessage> findByConversationIdOrderBySeqAsc(UUID conversationId);

    @Modifying
    @Query("delete from AiConversationMessage m where m.conversationId = :conversationId")
    void deleteByConversationId(UUID conversationId);

    @Modifying
    @Query("delete from AiConversationMessage m where m.conversationId in "
            + "(select c.conversationId from AiConversation c where c.expiresAt <= :now)")
    int deleteOfExpiredConversations(LocalDateTime now);
}
