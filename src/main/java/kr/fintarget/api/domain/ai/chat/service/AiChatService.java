package kr.fintarget.api.domain.ai.chat.service;

import kr.fintarget.api.domain.ai.chat.dto.AiChatResponse;
import kr.fintarget.api.domain.ai.chat.entity.AiConversation;
import kr.fintarget.api.domain.ai.chat.entity.AiConversationMessage;
import kr.fintarget.api.domain.ai.client.AiMessage;
import kr.fintarget.api.domain.ai.client.AiModelTier;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 정책 원문 기반 AI 질문 (#125). 정책 1건의 저장된 원문과 사용자 정보를 시스템 프롬프트에 넣고,
 * 같은 대화의 최근 질문·답변을 이어 붙여 답을 받는다. 벡터 검색은 쓰지 않는다.
 *
 * <p>AI 호출 실패(AiUnavailableException 503, AiCallFailedException 502)는 그대로 전파하며, 이때 질문은 저장하지 않는다.
 * 로그에는 질문·답변 본문을 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    public static final int MAX_TURNS = 10;
    public static final int MAX_QUESTION_LENGTH = 500;
    // 마지막 질문 후 이 시간이 지나면 대화가 만료된다 (질문 내용을 오래 보관하지 않음)
    static final Duration CONVERSATION_TTL = Duration.ofHours(24);
    // AI에 이어 붙이는 이전 턴 수. 원문과 사용자 정보는 매 턴 시스템 프롬프트에 다시 넣으므로 오래된 턴을 빼도 근거는 남는다
    static final int HISTORY_TURNS = 6;
    static final String SOURCE_LIMITED_NOTICE =
            "\n\n※ 이 정책은 저장된 원문 정보가 적어 답변이 부정확할 수 있습니다. 정확한 내용은 참고 링크나 담당 기관에서 확인해주세요.";

    private final AiChatStore store;
    private final AiTextClient aiTextClient;
    private final PolicyQaPrompt prompt;

    public AiChatResponse ask(UUID userId, UUID policyId, String question, UUID conversationId) {
        LocalDateTime now = now();
        AiChatStore.Context context = store.load(userId, policyId, conversationId, now);

        PolicyDocument document = PolicyDocument.of(context.policy());
        UserProfileSummary profile = UserProfileSummary.of(context.user(), Year.from(now));
        List<AiMessage> messages = new ArrayList<>(recentHistory(context.messages()));
        messages.add(AiMessage.user(question));

        // 모델은 설정의 기본 등급(openai.model.default)만 쓴다. 승급 기준은 docs/ai/eval-results-2026-10.md (#126), 구현은 별도 이슈
        AiTextResult result = aiTextClient.generate(
                new AiTextRequest(prompt.render(document, profile), messages, AiModelTier.DEFAULT));

        boolean firstTurn = context.conversation() == null;
        String answer = result.text().strip();
        if (document.sourceLimited() && firstTurn) {
            answer += SOURCE_LIMITED_NOTICE;
        }

        AiConversation saved;
        try {
            saved = store.saveTurn(userId, policyId, context.conversation(), question, answer, now(), CONVERSATION_TTL);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            throw AiChatConflictException.concurrentQuestion();
        }

        log.info("정책 AI 질문 응답 policyId={} conversationId={} turn={} promptVersion={} model={} inputTokens={} outputTokens={} sourceLimited={}",
                policyId, saved.getConversationId(), saved.getTurnCount(), PolicyQaPrompt.VERSION, result.model(),
                result.inputTokens(), result.outputTokens(), document.sourceLimited());
        return new AiChatResponse(saved.getConversationId(), answer, MAX_TURNS - saved.getTurnCount(),
                document.sourceLimited());
    }

    public void deleteConversation(UUID userId, UUID conversationId) {
        store.delete(userId, conversationId);
    }

    public int deleteExpiredConversations() {
        return store.deleteExpired(now());
    }

    private static List<AiMessage> recentHistory(List<AiConversationMessage> messages) {
        int from = Math.max(0, messages.size() - HISTORY_TURNS * 2);
        return messages.subList(from, messages.size()).stream()
                .map(message -> message.getRole() == AiConversationMessage.Role.USER
                        ? AiMessage.user(message.getContent())
                        : AiMessage.assistant(message.getContent()))
                .toList();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(BirthYearValidator.ZONE);
    }
}
