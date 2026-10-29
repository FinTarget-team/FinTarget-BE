package kr.fintarget.api.domain.ai.chat.service;

import kr.fintarget.api.domain.ai.chat.dto.AiChatResponse;
import kr.fintarget.api.domain.ai.chat.entity.AiConversation;
import kr.fintarget.api.domain.ai.chat.entity.AiConversationMessage;
import kr.fintarget.api.domain.ai.chat.repository.AiConversationMessageRepository;
import kr.fintarget.api.domain.ai.chat.repository.AiConversationRepository;
import kr.fintarget.api.domain.ai.client.AiCallFailedException;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiMessage;
import kr.fintarget.api.domain.ai.client.AiModelTier;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AiTextClient만 목으로 두고 H2에 실제로 저장한다.
 * AI 호출 동안 트랜잭션을 잡지 않는 구조(읽기, 저장을 각각 커밋)를 그대로 검증하려고 테스트 트랜잭션을 끈다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-chat-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@Import({AiChatService.class, AiChatStore.class, PolicyQaPrompt.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(OutputCaptureExtension.class)
class AiChatServiceTest {

    @Autowired
    private AiChatService service;
    @Autowired
    private AiChatStore store;
    @Autowired
    private PolicyRepository policyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AiConversationRepository conversationRepository;
    @Autowired
    private AiConversationMessageRepository messageRepository;

    @MockitoBean
    private AiTextClient aiTextClient;

    private UUID userId;
    private UUID otherUserId;
    private UUID policyId;
    private UUID otherPolicyId;
    private UUID limitedPolicyId;

    @BeforeEach
    void setUp() {
        userId = UUID.fromString(userRepository.save(User.builder().provider("kakao").providerId("p-1")
                .birthYear(2000).region("SEOUL").housingStatus("HOMELESS").employmentType("EMPLOYED").build()).getId());
        otherUserId = UUID.fromString(userRepository.save(User.builder().provider("kakao").providerId("p-2").build()).getId());
        PolicyText text = PolicyText.builder().supportContent("월 최대 20만원 월세 지원. ".repeat(10)).build();
        policyId = policyRepository.save(Policy.create("청년 월세 지원", "청년 월세 지원 사업", 19, 34, null, null,
                PolicyType.HOUSING, null, "P-1", "YOUTH_CENTER", text)).getPolicyId();
        otherPolicyId = policyRepository.save(Policy.create("다른 정책", "설명", null, null, null, null,
                PolicyType.ETC, null, "P-2", "YOUTH_CENTER", text)).getPolicyId();
        limitedPolicyId = policyRepository.save(Policy.create("원문 없는 정책", "짧은 설명", null, null, null, null,
                PolicyType.ETC, null, "P-3", "YOUTH_CENTER", null)).getPolicyId();

        when(aiTextClient.generate(any())).thenAnswer(invocation -> {
            AiTextRequest request = invocation.getArgument(0);
            String last = request.messages().get(request.messages().size() - 1).content();
            return new AiTextResult("답변: " + last, "gpt-6.1-sol", 100, 20, 5);
        });
    }

    @AfterEach
    void tearDown() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        policyRepository.deleteAll();
        userRepository.deleteAll();
    }

    private AiTextRequest lastRequest() {
        ArgumentCaptor<AiTextRequest> captor = ArgumentCaptor.forClass(AiTextRequest.class);
        verify(aiTextClient, atLeastOnce()).generate(captor.capture());
        return captor.getValue();
    }

    @Test
    void 새_대화를_만들고_질문과_답변을_저장한다() {
        AiChatResponse response = service.ask(userId, policyId, "신청할 수 있나요?", null);

        assertThat(response.answer()).isEqualTo("답변: 신청할 수 있나요?");
        assertThat(response.remainingTurns()).isEqualTo(9);
        assertThat(response.sourceLimited()).isFalse();
        AiConversation conversation = conversationRepository.findById(response.conversationId()).orElseThrow();
        assertThat(conversation.getTurnCount()).isEqualTo(1);
        assertThat(messageRepository.findByConversationIdOrderBySeqAsc(response.conversationId()))
                .extracting(AiConversationMessage::getRole, AiConversationMessage::getContent)
                .containsExactly(
                        tuple(AiConversationMessage.Role.USER, "신청할 수 있나요?"),
                        tuple(AiConversationMessage.Role.ASSISTANT, "답변: 신청할 수 있나요?"));
    }

    @Test
    void 시스템_프롬프트에_원문과_사용자_정보를_넣고_항상_DEFAULT_등급으로_호출한다() {
        service.ask(userId, policyId, "질문", null);

        AiTextRequest request = lastRequest();
        assertThat(request.tier()).isEqualTo(AiModelTier.DEFAULT);
        assertThat(request.systemPrompt())
                .contains("정책명: 청년 월세 지원", "[지원 내용]", "- 거주 지역: 서울", "- 주택 보유: 무주택",
                        "- 취업 상태: 직장인", "- 세전 월 소득: 확인 필요");
        assertThat(request.messages()).containsExactly(AiMessage.user("질문"));
    }

    @Test
    void 대화를_이어가면_이전_질문과_답변을_순서대로_보낸다() {
        UUID conversationId = service.ask(userId, policyId, "첫 질문", null).conversationId();

        AiChatResponse second = service.ask(userId, policyId, "둘째 질문", conversationId);

        assertThat(second.conversationId()).isEqualTo(conversationId);
        assertThat(second.remainingTurns()).isEqualTo(8);
        assertThat(lastRequest().messages()).containsExactly(
                AiMessage.user("첫 질문"), AiMessage.assistant("답변: 첫 질문"), AiMessage.user("둘째 질문"));
    }

    @Test
    void 이전_대화는_최근_6턴만_보낸다() {
        UUID conversationId = service.ask(userId, policyId, "q1", null).conversationId();
        for (int i = 2; i <= 8; i++) {
            service.ask(userId, policyId, "q" + i, conversationId);
        }

        service.ask(userId, policyId, "q9", conversationId);

        List<AiMessage> messages = lastRequest().messages();
        assertThat(messages).hasSize(AiChatService.HISTORY_TURNS * 2 + 1);
        assertThat(messages.get(0)).isEqualTo(AiMessage.user("q3"));
        assertThat(messages.get(messages.size() - 1)).isEqualTo(AiMessage.user("q9"));
    }

    @Test
    void 열_번째_질문까지_받고_그_다음은_409이며_AI를_호출하지_않는다() {
        UUID conversationId = service.ask(userId, policyId, "q1", null).conversationId();
        AiChatResponse last = null;
        for (int i = 2; i <= AiChatService.MAX_TURNS; i++) {
            last = service.ask(userId, policyId, "q" + i, conversationId);
        }
        assertThat(last.remainingTurns()).isZero();
        clearInvocations(aiTextClient);

        assertThatThrownBy(() -> service.ask(userId, policyId, "q11", conversationId))
                .isInstanceOf(AiChatConflictException.class)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(AiChatConflictException.TURN_LIMIT_EXCEEDED);
        verifyNoInteractions(aiTextClient);
    }

    @Test
    void 다른_사용자의_대화는_404() {
        UUID conversationId = service.ask(userId, policyId, "q", null).conversationId();
        clearInvocations(aiTextClient);

        assertThatThrownBy(() -> service.ask(otherUserId, policyId, "q", conversationId))
                .isInstanceOf(AiChatNotFoundException.class)
                .hasMessage(AiChatNotFoundException.CONVERSATION_NOT_FOUND);
        verifyNoInteractions(aiTextClient);
    }

    @Test
    void 경로의_정책과_다른_정책의_대화는_소유권_검사와_같은_404() {
        UUID conversationId = service.ask(userId, policyId, "q", null).conversationId();
        clearInvocations(aiTextClient);

        assertThatThrownBy(() -> service.ask(userId, otherPolicyId, "q", conversationId))
                .isInstanceOf(AiChatNotFoundException.class)
                .hasMessage(AiChatNotFoundException.CONVERSATION_NOT_FOUND);
        verifyNoInteractions(aiTextClient);
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getTurnCount()).isEqualTo(1);
    }

    @Test
    void 없는_대화와_만료된_대화는_404() {
        AiConversation expired = conversationRepository.save(AiConversation.start(userId, policyId,
                LocalDateTime.now().minusDays(3), Duration.ofHours(24)));

        assertThatThrownBy(() -> service.ask(userId, policyId, "q", UUID.randomUUID()))
                .isInstanceOf(AiChatNotFoundException.class);
        assertThatThrownBy(() -> service.ask(userId, policyId, "q", expired.getConversationId()))
                .isInstanceOf(AiChatNotFoundException.class)
                .hasMessage(AiChatNotFoundException.CONVERSATION_NOT_FOUND);
        verifyNoInteractions(aiTextClient);
    }

    @Test
    void 없는_정책은_404() {
        assertThatThrownBy(() -> service.ask(userId, UUID.randomUUID(), "q", null))
                .isInstanceOf(AiChatNotFoundException.class)
                .hasMessage(AiChatNotFoundException.POLICY_NOT_FOUND);
        verifyNoInteractions(aiTextClient);
    }

    @Test
    void AI_호출이_실패하면_예외를_그대로_전파하고_아무것도_저장하지_않는다() {
        doThrow(new AiUnavailableException(AiClientException.Reason.DISABLED)).when(aiTextClient).generate(any());
        assertThatThrownBy(() -> service.ask(userId, policyId, "q", null)).isInstanceOf(AiUnavailableException.class);
        assertThat(conversationRepository.count()).isZero();

        doReturn(new AiTextResult("a", "m", 1, 1, 0)).when(aiTextClient).generate(any());
        UUID conversationId = service.ask(userId, policyId, "q1", null).conversationId();
        doThrow(new AiCallFailedException(AiClientException.Reason.TIMEOUT)).when(aiTextClient).generate(any());

        assertThatThrownBy(() -> service.ask(userId, policyId, "q2", conversationId))
                .isInstanceOf(AiCallFailedException.class);
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getTurnCount()).isEqualTo(1);
        assertThat(messageRepository.findByConversationIdOrderBySeqAsc(conversationId)).hasSize(2);
    }

    @Test
    void 원문이_부족한_정책은_첫_답변에만_안내_문구를_붙인다() {
        AiChatResponse first = service.ask(userId, limitedPolicyId, "q1", null);
        AiChatResponse second = service.ask(userId, limitedPolicyId, "q2", first.conversationId());

        assertThat(first.sourceLimited()).isTrue();
        assertThat(first.answer()).isEqualTo("답변: q1" + AiChatService.SOURCE_LIMITED_NOTICE);
        assertThat(second.sourceLimited()).isTrue();
        assertThat(second.answer()).isEqualTo("답변: q2");
    }

    @Test
    void AI_호출_중에_같은_대화에_다른_질문이_저장되면_409() {
        UUID conversationId = service.ask(userId, policyId, "q1", null).conversationId();
        // AI 응답을 기다리는 사이 다른 요청이 같은 대화에 턴을 저장한 상황
        doAnswer(invocation -> {
            AiChatStore.Context other = store.load(userId, policyId, conversationId, LocalDateTime.now());
            store.saveTurn(userId, policyId, other.conversation(), "동시 질문", "동시 답변",
                    LocalDateTime.now(), Duration.ofHours(24));
            return new AiTextResult("a", "m", 1, 1, 0);
        }).when(aiTextClient).generate(any());

        assertThatThrownBy(() -> service.ask(userId, policyId, "q2", conversationId))
                .isInstanceOf(AiChatConflictException.class)
                .hasMessage(AiChatConflictException.CONCURRENT_QUESTION);
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getTurnCount()).isEqualTo(2);
        assertThat(messageRepository.findByConversationIdOrderBySeqAsc(conversationId))
                .extracting(AiConversationMessage::getContent).doesNotContain("q2");
    }

    @Test
    void 대화_종료는_본인_대화만_삭제하고_아니면_404() {
        UUID conversationId = service.ask(userId, policyId, "q", null).conversationId();

        assertThatThrownBy(() -> service.deleteConversation(otherUserId, conversationId))
                .isInstanceOf(AiChatNotFoundException.class);
        assertThat(conversationRepository.existsById(conversationId)).isTrue();

        service.deleteConversation(userId, conversationId);

        assertThat(conversationRepository.existsById(conversationId)).isFalse();
        assertThat(messageRepository.findByConversationIdOrderBySeqAsc(conversationId)).isEmpty();
    }

    @Test
    void 만료된_대화와_메시지만_정리한다() {
        UUID active = service.ask(userId, policyId, "q", null).conversationId();
        AiConversation expired = conversationRepository.save(AiConversation.start(userId, policyId,
                LocalDateTime.now().minusDays(3), Duration.ofHours(24)));
        messageRepository.save(new AiConversationMessage(expired.getConversationId(), 0,
                AiConversationMessage.Role.USER, "오래된 질문", LocalDateTime.now().minusDays(3)));

        int deleted = service.deleteExpiredConversations();

        assertThat(deleted).isEqualTo(1);
        assertThat(conversationRepository.findAll()).extracting(AiConversation::getConversationId).containsExactly(active);
        assertThat(messageRepository.findAll()).extracting(AiConversationMessage::getContent).doesNotContain("오래된 질문");
    }

    @Test
    void 로그에는_토큰_수와_프롬프트_버전만_남기고_질문과_답변_본문은_남기지_않는다(CapturedOutput output) {
        service.ask(userId, policyId, "비밀-질문-본문", null);

        assertThat(output.getAll())
                .contains("정책 AI 질문 응답", "promptVersion=policy-qa-v1", "inputTokens=100", "outputTokens=20")
                .doesNotContain("비밀-질문-본문", "답변: 비밀");
    }
}
