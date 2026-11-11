package kr.fintarget.api.domain.ai.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 대화의 질문·답변 메시지. 대화가 만료되면 대화와 함께 삭제된다 (질문 내용을 오래 보관하지 않음).
 */
@Entity
// (conversation_id, seq) 유니크: 동시에 보낸 질문이 같은 순번으로 저장되지 않도록
@Table(name = "ai_conversation_message", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ai_conversation_message_seq", columnNames = {"conversation_id", "seq"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiConversationMessage {

    public enum Role {
        USER,
        ASSISTANT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "message_id", columnDefinition = "uuid")
    private UUID messageId;

    @Column(name = "conversation_id", nullable = false, columnDefinition = "uuid")
    private UUID conversationId;

    // 대화 안에서의 순서 (0부터)
    @Column(name = "seq", nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AiConversationMessage(UUID conversationId, int seq, Role role, String content, LocalDateTime createdAt) {
        this.conversationId = conversationId;
        this.seq = seq;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }
}
