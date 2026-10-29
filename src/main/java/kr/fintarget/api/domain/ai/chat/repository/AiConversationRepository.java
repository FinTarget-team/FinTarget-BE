package kr.fintarget.api.domain.ai.chat.repository;

import kr.fintarget.api.domain.ai.chat.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    @Modifying
    @Query("delete from AiConversation c where c.expiresAt <= :now")
    int deleteExpired(LocalDateTime now);
}
