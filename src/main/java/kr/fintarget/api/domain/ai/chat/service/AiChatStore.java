package kr.fintarget.api.domain.ai.chat.service;

import kr.fintarget.api.domain.ai.chat.entity.AiConversation;
import kr.fintarget.api.domain.ai.chat.entity.AiConversationMessage;
import kr.fintarget.api.domain.ai.chat.repository.AiConversationMessageRepository;
import kr.fintarget.api.domain.ai.chat.repository.AiConversationRepository;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AI 질문의 DB 작업. AI 호출(수 초~수십 초) 동안 트랜잭션과 커넥션을 잡지 않도록
 * AiChatService가 "읽기 → AI 호출 → 저장"을 이 클래스의 메서드 두 번으로 나눠 부른다.
 */
@Component
@RequiredArgsConstructor
class AiChatStore {

    private final PolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final AiConversationRepository conversationRepository;
    private final AiConversationMessageRepository messageRepository;

    /**
     * @param conversationId null이면 새 대화
     * @throws AiChatNotFoundException 정책 없음, 대화 없음·다른 사용자·다른 정책·만료
     * @throws AiChatConflictException 턴 상한 초과
     */
    @Transactional(readOnly = true)
    public Context load(UUID userId, UUID policyId, UUID conversationId, LocalDateTime now) {
        Policy policy = policyRepository.findById(policyId).orElseThrow(AiChatNotFoundException::policy);
        User user = userRepository.findById(userId.toString())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (conversationId == null) {
            return new Context(policy, user, null, List.of());
        }
        AiConversation conversation = conversationRepository.findById(conversationId)
                .filter(found -> found.isAccessibleBy(userId, policyId, now))
                .orElseThrow(AiChatNotFoundException::conversation);
        if (conversation.getTurnCount() >= AiChatService.MAX_TURNS) {
            throw AiChatConflictException.turnLimitExceeded();
        }
        return new Context(policy, user, conversation,
                messageRepository.findByConversationIdOrderBySeqAsc(conversationId));
    }

    /**
     * 질문과 답변을 저장하고 턴을 늘린다. 대화가 없으면 이때 만든다 (AI 호출이 실패하면 아무것도 남지 않음).
     * 같은 대화에 동시에 보낸 질문은 @Version 충돌로 하나만 저장된다.
     *
     * @return 저장된 대화
     */
    @Transactional
    public AiConversation saveTurn(UUID userId, UUID policyId, AiConversation loaded, String question, String answer,
                                   LocalDateTime now, Duration ttl) {
        AiConversation conversation;
        int nextSeq;
        if (loaded == null) {
            conversation = conversationRepository.save(AiConversation.start(userId, policyId, now, ttl));
            nextSeq = 0;
        } else {
            // load 이후 다른 요청이 턴을 늘렸으면 version이 달라 커밋 시 충돌한다
            conversation = conversationRepository.findById(loaded.getConversationId())
                    .filter(found -> found.getVersion() == loaded.getVersion())
                    .orElseThrow(AiChatConflictException::concurrentQuestion);
            if (conversation.getTurnCount() >= AiChatService.MAX_TURNS) {
                throw AiChatConflictException.turnLimitExceeded();
            }
            nextSeq = conversation.getTurnCount() * 2;
        }

        messageRepository.save(new AiConversationMessage(conversation.getConversationId(), nextSeq,
                AiConversationMessage.Role.USER, question, now));
        messageRepository.save(new AiConversationMessage(conversation.getConversationId(), nextSeq + 1,
                AiConversationMessage.Role.ASSISTANT, answer, now));
        conversation.completeTurn(now, ttl);
        return conversationRepository.saveAndFlush(conversation);
    }

    /**
     * @throws AiChatNotFoundException 대화가 없거나 다른 사용자의 대화
     */
    @Transactional
    public void delete(UUID userId, UUID conversationId) {
        AiConversation conversation = conversationRepository.findById(conversationId)
                .filter(found -> found.isOwnedBy(userId))
                .orElseThrow(AiChatNotFoundException::conversation);
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(conversation);
    }

    /**
     * @return 삭제한 대화 수
     */
    @Transactional
    public int deleteExpired(LocalDateTime now) {
        messageRepository.deleteOfExpiredConversations(now);
        return conversationRepository.deleteExpired(now);
    }

    record Context(Policy policy, User user, AiConversation conversation, List<AiConversationMessage> messages) {
    }
}
