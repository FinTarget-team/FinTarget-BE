package kr.fintarget.api.domain.policy.rag;

import kr.fintarget.api.domain.ai.client.AiCallFailedException;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiMessage;
import kr.fintarget.api.domain.ai.client.AiModelTier;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
import kr.fintarget.api.domain.ai.usage.AiUsageFeature;
import kr.fintarget.api.domain.ai.usage.AiUsageLimitExceededException;
import kr.fintarget.api.domain.ai.usage.AiUsageLimiter;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.search.PolicySearchHit;
import kr.fintarget.api.domain.policy.search.PolicySearchService;
import kr.fintarget.api.domain.policy.service.PolicyService;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 통합 정책 AI 답변의 상태 분기, 근거 변환, 한도 차감·되돌리기 (#183). OpenAI 호출은 모킹한다.
 */
@ExtendWith(MockitoExtension.class)
class PolicyRagServiceTest {

    private static final String USER_ID = "user-1";
    private static final String QUESTION = "월세 지원받을 수 있는 정책 있어?";
    private static final AiUsageLimiter.Ticket TICKET =
            new AiUsageLimiter.Ticket(USER_ID, LocalDate.of(2026, 11, 5), AiUsageFeature.POLICY_RAG);

    @Mock
    private PolicyService policyService;

    @Mock
    private PolicySearchService searchService;

    @Mock
    private AiTextClient aiTextClient;

    @Mock
    private AiUsageLimiter usageLimiter;

    private final PolicyRagPrompt prompt = new PolicyRagPrompt();

    private PolicyRagService service() {
        return new PolicyRagService(policyService, searchService, aiTextClient, prompt, usageLimiter, 20, 1000);
    }

    private static User user(Integer birthYear, Long income) {
        return User.builder().id(USER_ID).provider(SocialProvider.KAKAO).providerId("provider-1")
                .birthYear(birthYear).income(income).region("SEOUL").build();
    }

    private static Policy policy(String name, String region) {
        Policy policy = Policy.create(name, "설명", null, null, null, null, PolicyType.HOUSING, region, null, null);
        ReflectionTestUtils.setField(policy, "policyId", UUID.randomUUID());
        return policy;
    }

    private static PolicySearchHit hit(Policy policy, String content) {
        return new PolicySearchHit(policy, UUID.randomUUID(), "전체", content, 0.1);
    }

    private static AiTextResult aiAnswer(String text) {
        return new AiTextResult(text, "gpt-6-luna", 3000, 120, 10);
    }

    /** 조건 정보가 있고 후보가 있는 사용자, 한도 차감까지 스텁 */
    private User readyUser(List<Policy> candidates) {
        User user = user(2000, 3_000_000L);
        when(policyService.findUser(USER_ID)).thenReturn(user);
        when(policyService.findCandidatePolicies(user, null)).thenReturn(candidates);
        when(usageLimiter.acquire(USER_ID, AiUsageFeature.POLICY_RAG, 20)).thenReturn(TICKET);
        return user;
    }

    @Test
    void 검색_결과가_있으면_청크를_번호로_넣어_답을_받고_근거_정책을_돌려준다() {
        Policy a = policy("청년 월세 지원", null);
        Policy b = policy("서울시 청년월세", "SEOUL");
        readyUser(List.of(a, b));
        when(searchService.searchCandidates(List.of(a, b), QUESTION))
                .thenReturn(List.of(hit(a, "정책명: 청년 월세 지원\n월 20만원"), hit(b, "정책명: 서울시 청년월세")));
        when(aiTextClient.generate(any())).thenReturn(aiAnswer(
                "**월세 지원 정책은 2개입니다.**\n* 서울시 청년월세를 신청할 수 있습니다 [2]\n* 청년 월세 지원은 월 20만원입니다 [1]."));

        PolicyRagResult result = service().answer(USER_ID, QUESTION);

        assertThat(result.status()).isEqualTo(PolicyRagStatus.ANSWERED);
        assertThat(result.answer()).isEqualTo(
                "월세 지원 정책은 2개입니다.\n- 서울시 청년월세를 신청할 수 있습니다\n- 청년 월세 지원은 월 20만원입니다.");
        assertThat(result.sources()).containsExactly(b, a);

        ArgumentCaptor<AiTextRequest> request = ArgumentCaptor.forClass(AiTextRequest.class);
        verify(aiTextClient).generate(request.capture());
        assertThat(request.getValue().systemPrompt())
                .contains("[1]\n정책명: 청년 월세 지원\n월 20만원\n\n[2]\n정책명: 서울시 청년월세", "- 거주 지역: 서울");
        assertThat(request.getValue().messages()).containsExactly(AiMessage.user(QUESTION));
        assertThat(request.getValue().tier()).isEqualTo(AiModelTier.DEFAULT);
        assertThat(request.getValue().maxOutputTokens()).isEqualTo(1000);
        verify(usageLimiter, never()).release(any());
    }

    @Test
    void 이름이_같은_정책은_지역이_다른_별도_근거로_돌려준다() {
        Policy busan = policy("전세보증금반환보증 보증료 지원", "BUSAN");
        Policy ulsan = policy("전세보증금반환보증 보증료 지원", "GYEONGNAM,ULSAN");
        readyUser(List.of(busan, ulsan));
        when(searchService.searchCandidates(anyList(), anyString()))
                .thenReturn(List.of(hit(busan, "부산 원문"), hit(ulsan, "경남·울산 원문")));
        when(aiTextClient.generate(any())).thenReturn(aiAnswer("경남·울산 거주자는 보증료를 지원받을 수 있습니다 [2]."));

        PolicyRagResult result = service().answer(USER_ID, QUESTION);

        assertThat(result.sources()).containsExactly(ulsan);
        assertThat(result.sources().getFirst().regionCodes()).containsExactly("GYEONGNAM", "ULSAN");
    }

    @Test
    void 나이나_소득이_없으면_AI를_호출하지_않고_조건_정보_부족이다() {
        when(policyService.findUser(USER_ID)).thenReturn(user(null, 3_000_000L));

        PolicyRagResult result = service().answer(USER_ID, QUESTION);

        assertThat(result.status()).isEqualTo(PolicyRagStatus.PROFILE_INCOMPLETE);
        assertThat(result.answer()).isEqualTo(PolicyRagResult.PROFILE_INCOMPLETE_ANSWER);
        assertThat(result.sources()).isEmpty();
        verifyNoInteractions(searchService, aiTextClient, usageLimiter);

        when(policyService.findUser(USER_ID)).thenReturn(user(2000, null));
        assertThat(service().answer(USER_ID, QUESTION).status()).isEqualTo(PolicyRagStatus.PROFILE_INCOMPLETE);
    }

    @Test
    void 조건에_맞는_후보_정책이_없으면_임베딩도_하지_않고_한도도_차감하지_않는다() {
        User user = user(2000, 3_000_000L);
        when(policyService.findUser(USER_ID)).thenReturn(user);
        when(policyService.findCandidatePolicies(user, null)).thenReturn(List.of());

        PolicyRagResult result = service().answer(USER_ID, QUESTION);

        assertThat(result.status()).isEqualTo(PolicyRagStatus.NO_EVIDENCE);
        verifyNoInteractions(searchService, aiTextClient, usageLimiter);
    }

    @Test
    void 검색_결과가_없으면_AI를_호출하지_않고_근거_없음이며_한도는_차감된다() {
        readyUser(List.of(policy("정책", null)));
        when(searchService.searchCandidates(anyList(), anyString())).thenReturn(List.of());

        PolicyRagResult result = service().answer(USER_ID, QUESTION);

        assertThat(result.status()).isEqualTo(PolicyRagStatus.NO_EVIDENCE);
        assertThat(result.answer()).isEqualTo(PolicyRagResult.NO_EVIDENCE_ANSWER);
        assertThat(result.sources()).isEmpty();
        verifyNoInteractions(aiTextClient);
        verify(usageLimiter).acquire(USER_ID, AiUsageFeature.POLICY_RAG, 20);
        verify(usageLimiter, never()).release(any());
    }

    @Test
    void 근거_번호가_없는_답변은_버리고_근거_없음_고정_문구로_바꾼다() {
        Policy a = policy("정책 A", null);
        readyUser(List.of(a));
        when(searchService.searchCandidates(anyList(), anyString())).thenReturn(List.of(hit(a, "원문")));
        when(aiTextClient.generate(any())).thenReturn(aiAnswer("일반적으로 청년은 월세 지원을 받을 수 있습니다."));

        PolicyRagResult result = service().answer(USER_ID, QUESTION);

        assertThat(result.status()).isEqualTo(PolicyRagStatus.NO_EVIDENCE);
        assertThat(result.answer()).isEqualTo(PolicyRagResult.NO_EVIDENCE_ANSWER);
        assertThat(result.sources()).isEmpty();
    }

    @Test
    void 검색_결과에_없는_번호는_버리고_유효한_번호만_근거로_쓴다() {
        Policy a = policy("정책 A", null);
        Policy b = policy("정책 B", null);
        readyUser(List.of(a, b));
        when(searchService.searchCandidates(anyList(), anyString())).thenReturn(List.of(hit(a, "A"), hit(b, "B")));
        when(aiTextClient.generate(any())).thenReturn(aiAnswer("정책 A가 해당합니다 [1][4]. 그 외 정책도 있습니다 [9]."));

        PolicyRagResult result = service().answer(USER_ID, QUESTION);

        assertThat(result.status()).isEqualTo(PolicyRagStatus.ANSWERED);
        assertThat(result.sources()).containsExactly(a);
        assertThat(result.answer()).isEqualTo("정책 A가 해당합니다. 그 외 정책도 있습니다.");
    }

    @Test
    void 유효하지_않은_번호만_있으면_근거_없음이다() {
        Policy a = policy("정책 A", null);
        readyUser(List.of(a));
        when(searchService.searchCandidates(anyList(), anyString())).thenReturn(List.of(hit(a, "A")));
        when(aiTextClient.generate(any())).thenReturn(aiAnswer("해당합니다 [3]."));

        PolicyRagResult result = service().answer(USER_ID, QUESTION);

        assertThat(result.status()).isEqualTo(PolicyRagStatus.NO_EVIDENCE);
        assertThat(result.sources()).isEmpty();
    }

    @Test
    void 한도를_넘으면_검색과_AI를_호출하지_않고_429_예외() {
        User user = user(2000, 3_000_000L);
        when(policyService.findUser(USER_ID)).thenReturn(user);
        when(policyService.findCandidatePolicies(user, null)).thenReturn(List.of(policy("정책", null)));
        when(usageLimiter.acquire(USER_ID, AiUsageFeature.POLICY_RAG, 20))
                .thenThrow(new AiUsageLimitExceededException(AiUsageFeature.POLICY_RAG));

        assertThatThrownBy(() -> service().answer(USER_ID, QUESTION)).isInstanceOf(AiUsageLimitExceededException.class);

        verifyNoInteractions(searchService, aiTextClient);
    }

    @Test
    void 임베딩_호출이_실패하면_예외를_전파하고_차감한_횟수를_되돌린다() {
        readyUser(List.of(policy("정책", null)));
        when(searchService.searchCandidates(anyList(), anyString()))
                .thenThrow(new AiUnavailableException(AiClientException.Reason.DISABLED));

        assertThatThrownBy(() -> service().answer(USER_ID, QUESTION)).isInstanceOf(AiUnavailableException.class);

        verify(usageLimiter).release(TICKET);
        verifyNoInteractions(aiTextClient);
    }

    @Test
    void 텍스트_생성이_실패하면_예외를_전파하고_차감한_횟수를_되돌린다() {
        Policy a = policy("정책", null);
        readyUser(List.of(a));
        when(searchService.searchCandidates(anyList(), anyString())).thenReturn(List.of(hit(a, "원문")));
        when(aiTextClient.generate(any())).thenThrow(new AiCallFailedException(AiClientException.Reason.TIMEOUT));

        assertThatThrownBy(() -> service().answer(USER_ID, QUESTION)).isInstanceOf(AiCallFailedException.class);

        verify(usageLimiter).release(TICKET);
    }

    @Test
    void 한도와_출력_상한_설정값이_1보다_작으면_기동에_실패한다() {
        assertThatThrownBy(() -> new PolicyRagService(policyService, searchService, aiTextClient, prompt, usageLimiter, 0, 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PolicyRagService(policyService, searchService, aiTextClient, prompt, usageLimiter, 20, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
