package kr.fintarget.api.domain.ai.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 정책 1건에 대한 AI 질문 대화 (#125). 마지막 질문 후 TTL이 지나면 만료되고, 매일 정리 스케줄러가 삭제한다.
 * policyId는 FK로 두지 않는다: 정책 동기화(PolicySyncWriter)가 목록에서 빠진 정책을 삭제할 때 대화 때문에 막히지 않도록.
 * 정책이 삭제된 뒤의 대화는 조회 시 404로 처리한다.
 */
@Entity
@Table(name = "ai_conversation", indexes = {
        @Index(name = "idx_ai_conversation_expires_at", columnList = "expires_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "conversation_id", columnDefinition = "uuid")
    private UUID conversationId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "policy_id", nullable = false, columnDefinition = "uuid")
    private UUID policyId;

    // 완료된 질문·답변 쌍의 수
    @Column(name = "turn_count", nullable = false)
    private int turnCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 같은 대화에 동시에 보낸 질문이 턴 상한을 넘기지 않도록
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static AiConversation start(UUID userId, UUID policyId, LocalDateTime now, Duration ttl) {
        AiConversation conversation = new AiConversation();
        conversation.userId = userId;
        conversation.policyId = policyId;
        conversation.turnCount = 0;
        conversation.createdAt = now;
        conversation.expiresAt = now.plus(ttl);
        return conversation;
    }

    public boolean isAccessibleBy(UUID userId, UUID policyId, LocalDateTime now) {
        return this.userId.equals(userId) && this.policyId.equals(policyId) && isActiveAt(now);
    }

    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }

    public boolean isActiveAt(LocalDateTime now) {
        return expiresAt.isAfter(now);
    }

    public void completeTurn(LocalDateTime now, Duration ttl) {
        this.turnCount++;
        this.expiresAt = now.plus(ttl);
    }
}
