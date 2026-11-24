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
import kr.fintarget.api.domain.policy.entity.PolicyCondition;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.search.PolicySearchHit;
import kr.fintarget.api.domain.policy.search.PolicySearchService;
import kr.fintarget.api.domain.policy.service.PolicyService;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
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
import static org.assertj.core.api.Assertions.assertThatCode;
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
        when(searchService.searchForAnswer(List.of(a, b), QUESTION))
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
        when(searchService.searchForAnswer(anyList(), anyString()))
                .thenReturn(List.of(hit(busan, "부산 원문"), hit(ulsan, "경남·울산 원문")));
        when(aiTextClient.generate(any())).thenReturn(aiAnswer("경남·울산 거주자는 보증료를 지원받을 수 있습니다 [2]."));

        PolicyRagResult result = service().answer(USER_ID, QUESTION);

        assertThat(result.sources()).containsExactly(ulsan);
        assertThat(result.sources().getFirst().regionCodes()).containsExactly("GYEONGNAM", "ULSAN");
    }

    @Test
    void 나이와_소득이_모두_없으면_AI를_호출하지_않고_조건_정보_부족이다() {
        when(policyService.findUser(USER_ID)).thenReturn(user(null, null));

        PolicyRagResult result = service().answer(USER_ID, QUESTION);

        assertThat(result.status()).isEqualTo(PolicyRagStatus.PROFILE_INCOMPLETE);
        assertThat(result.answer()).isEqualTo(PolicyRagResult.PROFILE_INCOMPLETE_ANSWER);
        assertThat(result.sources()).isEmpty();
        verifyNoInteractions(searchService, aiTextClient, usageLimiter);
    }

    @Test
    void 나이나_소득_중_하나만_없으면_조건_정보_부족이_아니라_후보를_찾는다() {
        // #225: 하나만 없으면 그 조건만 건너뛰고 후보를 찾는다
        User noAge = user(null, 3_000_000L);
        when(policyService.findUser(USER_ID)).thenReturn(noAge);
        when(policyService.findCandidatePolicies(noAge, null)).thenReturn(List.of());

        assertThat(service().answer(USER_ID, QUESTION).status()).isEqualTo(PolicyRagStatus.NO_EVIDENCE);
        verify(policyService).findCandidatePolicies(noAge, null);
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
        when(searchService.searchForAnswer(anyList(), anyString())).thenReturn(List.of());

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
        when(searchService.searchForAnswer(anyList(), anyString())).thenReturn(List.of(hit(a, "원문")));
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
        when(searchService.searchForAnswer(anyList(), anyString())).thenReturn(List.of(hit(a, "A"), hit(b, "B")));
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
        when(searchService.searchForAnswer(anyList(), anyString())).thenReturn(List.of(hit(a, "A")));
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
        when(searchService.searchForAnswer(anyList(), anyString()))
                .thenThrow(new AiUnavailableException(AiClientException.Reason.DISABLED));

        assertThatThrownBy(() -> service().answer(USER_ID, QUESTION)).isInstanceOf(AiUnavailableException.class);

        verify(usageLimiter).release(TICKET);
        verifyNoInteractions(aiTextClient);
    }

    @Test
    void 텍스트_생성이_실패하면_예외를_전파하고_차감한_횟수를_되돌린다() {
        Policy a = policy("정책", null);
        readyUser(List.of(a));
        when(searchService.searchForAnswer(anyList(), anyString())).thenReturn(List.of(hit(a, "원문")));
        when(aiTextClient.generate(any())).thenThrow(new AiCallFailedException(AiClientException.Reason.TIMEOUT));

        assertThatThrownBy(() -> service().answer(USER_ID, QUESTION)).isInstanceOf(AiCallFailedException.class);

        verify(usageLimiter).release(TICKET);
    }

    @Test
    void 출력_상한_설정값이_1보다_작으면_기동에_실패한다() {
        assertThatThrownBy(() -> new PolicyRagService(policyService, searchService, aiTextClient, prompt, usageLimiter, 20, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 한도_설정값이_0_이하면_무제한으로_기동한다() {
        // 0 이하는 무제한이라 기동을 막지 않는다. 차감 여부는 AiUsageLimiter가 판단한다 (#239)
        assertThatCode(() -> new PolicyRagService(policyService, searchService, aiTextClient, prompt, usageLimiter, 0, 1000))
                .doesNotThrowAnyException();
        assertThatCode(() -> new PolicyRagService(policyService, searchService, aiTextClient, prompt, usageLimiter, -1, 1000))
                .doesNotThrowAnyException();
        assertThat(PolicyRagService.DEFAULT_DAILY_LIMIT).isZero();
    }

    // #235

    private static Policy localPolicy(String name) {
        return policy(name, "JEONNAM").withRegionLocalScope(true);
    }

    @Test
    void 시군구_한정_정책은_검색_후보에서_빼고_추천_공통_후보는_그대로_쓴다() {
        Policy national = policy("청년 월세 지원", null);
        Policy gokseong = localPolicy("곡성군 일하잡(job)센터 지원사업");
        readyUser(List.of(national, gokseong));
        when(searchService.searchForAnswer(List.of(national), QUESTION)).thenReturn(List.of(hit(national, "정책명: 청년 월세 지원")));
        when(aiTextClient.generate(any())).thenReturn(aiAnswer("월세 지원 정책을 1개 찾았어요 [1]"));

        PolicyRagResult result = service().answer(USER_ID, QUESTION);

        assertThat(result.sources()).containsExactly(national);
        verify(searchService).searchForAnswer(List.of(national), QUESTION);
    }

    @Test
    void 질문에_그_시군구_이름이_있으면_한정_정책을_남긴다() {
        Policy national = policy("청년 월세 지원", null);
        Policy gokseong = localPolicy("곡성군 일하잡(job)센터 지원사업");
        String question = "곡성에서 일자리 구할 때 도움받을 수 있어?";
        User user = user(2000, 3_000_000L);
        when(policyService.findUser(USER_ID)).thenReturn(user);
        when(policyService.findCandidatePolicies(user, null)).thenReturn(List.of(national, gokseong));
        when(usageLimiter.acquire(USER_ID, AiUsageFeature.POLICY_RAG, 20)).thenReturn(TICKET);
        when(searchService.searchForAnswer(List.of(national, gokseong), question)).thenReturn(List.of(hit(gokseong, "정책명: 곡성군")));
        when(aiTextClient.generate(any())).thenReturn(aiAnswer("곡성군 정책을 찾았어요 [1]"));

        assertThat(service().answer(USER_ID, question).sources()).containsExactly(gokseong);
    }

    @Test
    void 후보가_모두_시군구_한정이면_임베딩_없이_근거_없음이고_한도를_차감하지_않는다() {
        User user = user(2000, 3_000_000L);
        when(policyService.findUser(USER_ID)).thenReturn(user);
        when(policyService.findCandidatePolicies(user, null)).thenReturn(List.of(localPolicy("곡성군 일하잡(job)센터 지원사업")));

        PolicyRagResult result = service().answer(USER_ID, QUESTION);

        assertThat(result.status()).isEqualTo(PolicyRagStatus.NO_EVIDENCE);
        verifyNoInteractions(searchService, aiTextClient, usageLimiter);
    }

    @Test
    void 특정_대상_전용_정책은_청크_앞에_대상_집단을_붙이고_오늘_날짜를_넣는다() {
        Policy special = policy("자립준비청년 자립수당", null);
        special.updateCondition(PolicyCondition.builder().specialTarget(true).specialTargetGroup("자립준비청년").build());
        Policy general = policy("청년 월세 지원", null);
        readyUser(List.of(special, general));
        when(searchService.searchForAnswer(List.of(special, general), QUESTION))
                .thenReturn(List.of(hit(special, "정책명: 자립준비청년 자립수당"), hit(general, "정책명: 청년 월세 지원")));
        when(aiTextClient.generate(any())).thenReturn(aiAnswer("월세 지원 정책을 찾았어요 [2]"));

        service().answer(USER_ID, QUESTION);

        ArgumentCaptor<AiTextRequest> request = ArgumentCaptor.forClass(AiTextRequest.class);
        verify(aiTextClient).generate(request.capture());
        assertThat(request.getValue().systemPrompt())
                .contains("[1]\n[신청 대상 한정] 자립준비청년\n정책명: 자립준비청년 자립수당", "[2]\n정책명: 청년 월세 지원",
                        "[오늘 날짜]\n" + LocalDate.now(BirthYearValidator.ZONE).getYear() + "년");
    }

    @Test
    void 고정_문구는_해요체이고_근거_없음은_답할_수_있는_범위를_알린다() {
        assertThat(PolicyRagResult.NO_EVIDENCE_ANSWER)
                .contains("찾지 못했어요", "청년 주거·금융·일자리 정책에 대한 질문에 답할 수 있어요")
                .doesNotContain("습니다");
        assertThat(PolicyRagResult.PROFILE_INCOMPLETE_ANSWER).contains("필요해요").doesNotContain("습니다", "합니다");
    }
}
