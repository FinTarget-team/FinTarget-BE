package kr.fintarget.api.domain.policy.search;

import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiEmbeddingClient;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
import kr.fintarget.api.domain.ai.usage.AiUsageFeature;
import kr.fintarget.api.domain.ai.usage.AiUsageLimitExceededException;
import kr.fintarget.api.domain.ai.usage.AiUsageLimiter;
import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.policy.condition.BenefitKind;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyBenefit;
import kr.fintarget.api.domain.policy.entity.PolicyCondition;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.search.PolicyChunkSearchRepository.ChunkMatch;
import kr.fintarget.api.domain.policy.service.PolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 정책 검색의 묶기·유사도 하한·k 로직 (#182). 이 부분은 SQL이 아니라 Java에 있어 H2 없이 검증된다
 * (#184가 k·하한값을 조정하므로 테스트로 덮어 둔다).
 */
@ExtendWith(MockitoExtension.class)
class PolicySearchServiceTest {

    private static final String USER_ID = "user-1";
    private static final float[] VECTOR = {0.1f, 0.2f};

    @Mock
    private PolicyService policyService;

    @Mock
    private PolicyChunkSearchRepository searchRepository;

    @Mock
    private AiEmbeddingClient embeddingClient;

    @Mock
    private AiUsageLimiter usageLimiter;

    private PolicySearchService service(int k, double maxDistance, int chunkMultiplier) {
        return service(k, maxDistance, chunkMultiplier, 50);
    }

    private PolicySearchService service(int k, double maxDistance, int chunkMultiplier, int dailyLimit) {
        return new PolicySearchService(policyService, searchRepository, embeddingClient, usageLimiter,
                k, maxDistance, chunkMultiplier, dailyLimit);
    }

    private Policy policy(String name) {
        Policy policy = Policy.create(name, "설명", null, null, null, null, PolicyType.ETC, null, null, null);
        ReflectionTestUtils.setField(policy, "policyId", UUID.randomUUID());
        return policy;
    }

    private ChunkMatch match(Policy policy, String label, String content, double distance) {
        return new ChunkMatch(policy.getPolicyId(), UUID.randomUUID(), label, content, distance);
    }

    private void stubEmbedding() {
        when(embeddingClient.embed(List.of("질문"))).thenReturn(List.of(VECTOR));
    }

    @Test
    void 나이나_소득이_없는_사용자는_빈_결과이고_임베딩을_호출하지_않는다() {
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of());

        assertThat(service(5, 0.5, 3).search(USER_ID, "질문")).isEmpty();

        verify(embeddingClient, never()).embed(anyList());
        verify(searchRepository, never()).search(anyList(), any(), anyInt());
        // 임베딩을 호출하지 않으므로 검색 한도도 차감하지 않는다 (#183)
        verifyNoInteractions(usageLimiter);
    }

    @Test
    void 같은_정책의_청크가_여러_개면_가장_가까운_것_하나로_묶인다() {
        Policy a = policy("정책 A");
        Policy b = policy("정책 B");
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of(a, b));
        stubEmbedding();
        when(searchRepository.search(anyList(), eq(VECTOR), anyInt())).thenReturn(List.of(
                match(a, "지원 내용", "가장 가까운 청크", 0.10),
                match(a, "자격 조건", "같은 정책의 다른 청크", 0.20),
                match(b, "전체", "다른 정책", 0.30)
        ));

        List<PolicySearchHit> hits = service(5, 0.5, 3).search(USER_ID, "질문");

        assertThat(hits).extracting(hit -> hit.policy().getName()).containsExactly("정책 A", "정책 B");
        assertThat(hits.getFirst().sectionLabel()).isEqualTo("지원 내용");
        assertThat(hits.getFirst().content()).isEqualTo("가장 가까운 청크");
        assertThat(hits.getFirst().distance()).isEqualTo(0.10);
    }

    @Test
    void 유사도_하한값을_넘는_결과는_제외된다() {
        Policy near = policy("가까운 정책");
        Policy far = policy("먼 정책");
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of(near, far));
        stubEmbedding();
        when(searchRepository.search(anyList(), eq(VECTOR), anyInt())).thenReturn(List.of(
                match(near, "전체", "본문", 0.30),
                match(far, "전체", "본문", 0.51)
        ));

        List<PolicySearchHit> hits = service(5, 0.5, 3).search(USER_ID, "질문");

        assertThat(hits).extracting(hit -> hit.policy().getName()).containsExactly("가까운 정책");
    }

    @Test
    void 하한값을_넘는_결과만_있으면_빈_결과다() {
        Policy far = policy("먼 정책");
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of(far));
        stubEmbedding();
        when(searchRepository.search(anyList(), eq(VECTOR), anyInt()))
                .thenReturn(List.of(match(far, "전체", "본문", 0.9)));

        assertThat(service(5, 0.5, 3).search(USER_ID, "질문")).isEmpty();
    }

    @Test
    void 묶은_결과는_k개를_넘지_않고_가까운_순서다() {
        List<Policy> policies = List.of(policy("1"), policy("2"), policy("3"), policy("4"));
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(policies);
        stubEmbedding();
        when(searchRepository.search(anyList(), eq(VECTOR), anyInt())).thenReturn(List.of(
                match(policies.get(0), "전체", "본문", 0.10),
                match(policies.get(1), "전체", "본문", 0.20),
                match(policies.get(2), "전체", "본문", 0.30),
                match(policies.get(3), "전체", "본문", 0.40)
        ));

        List<PolicySearchHit> hits = service(2, 0.5, 3).search(USER_ID, "질문");

        assertThat(hits).extracting(hit -> hit.policy().getName()).containsExactly("1", "2");
    }

    // 조회 상한은 k × chunk-multiplier 여야 한다. 한 정책이 청크 여러 개를 차지해도 k개를 채우려면 넉넉히 가져와야 한다
    @Test
    void 청크_조회_상한은_k와_배수의_곱이다() {
        Policy a = policy("정책 A");
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of(a));
        stubEmbedding();
        when(searchRepository.search(anyList(), eq(VECTOR), eq(15))).thenReturn(List.of());

        service(5, 0.5, 3).search(USER_ID, "질문");

        verify(searchRepository).search(anyList(), eq(VECTOR), eq(15));
    }

    @Test
    void 임베딩_호출이_실패하면_예외가_그대로_전파된다() {
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of(policy("정책 A")));
        when(embeddingClient.embed(List.of("질문")))
                .thenThrow(new AiUnavailableException(AiClientException.Reason.DISABLED));

        assertThatThrownBy(() -> service(5, 0.5, 3).search(USER_ID, "질문"))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void 후보가_있으면_검색_한도를_1회_차감한다() {
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of(policy("정책 A")));
        stubEmbedding();
        when(searchRepository.search(anyList(), eq(VECTOR), anyInt())).thenReturn(List.of());

        service(5, 0.5, 3, 50).search(USER_ID, "질문");

        verify(usageLimiter).acquire(USER_ID, AiUsageFeature.POLICY_SEARCH, 50);
        verify(usageLimiter, never()).release(any());
    }

    @Test
    void 검색_한도를_넘으면_임베딩을_호출하지_않고_429_예외() {
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of(policy("정책 A")));
        when(usageLimiter.acquire(USER_ID, AiUsageFeature.POLICY_SEARCH, 50))
                .thenThrow(new AiUsageLimitExceededException(AiUsageFeature.POLICY_SEARCH));

        assertThatThrownBy(() -> service(5, 0.5, 3).search(USER_ID, "질문"))
                .isInstanceOf(AiUsageLimitExceededException.class);

        verify(embeddingClient, never()).embed(anyList());
    }

    @Test
    void 임베딩_호출이_실패하면_차감한_검색_횟수를_되돌린다() {
        AiUsageLimiter.Ticket ticket = new AiUsageLimiter.Ticket(USER_ID, LocalDate.of(2026, 11, 5), AiUsageFeature.POLICY_SEARCH);
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of(policy("정책 A")));
        when(usageLimiter.acquire(USER_ID, AiUsageFeature.POLICY_SEARCH, 50)).thenReturn(ticket);
        when(embeddingClient.embed(List.of("질문")))
                .thenThrow(new AiUnavailableException(AiClientException.Reason.DISABLED));

        assertThatThrownBy(() -> service(5, 0.5, 3).search(USER_ID, "질문"))
                .isInstanceOf(AiUnavailableException.class);

        verify(usageLimiter).release(ticket);
    }

    @Test
    void 후보를_받아_검색하면_검색_한도를_차감하지_않는다() {
        Policy a = policy("정책 A");
        stubEmbedding();
        when(searchRepository.search(anyList(), eq(VECTOR), anyInt()))
                .thenReturn(List.of(match(a, "전체", "청크", 0.1)));

        assertThat(service(5, 0.5, 3).searchCandidates(List.of(a), "질문")).hasSize(1);

        verifyNoInteractions(usageLimiter, policyService);
    }

    private Policy loan(String name) {
        Policy policy = policy(name);
        policy.updateBenefit(PolicyBenefit.builder().type(BenefitType.LOAN).build());
        return policy;
    }

    // 운영 쿼리처럼 넘겨받은 정책 안에서 거리순 상위 limit개만 돌려준다
    private void stubRankedSearch(String question, List<ChunkMatch> ranked) {
        when(embeddingClient.embed(List.of(question))).thenReturn(List.of(VECTOR));
        when(searchRepository.search(anyList(), eq(VECTOR), anyInt())).thenAnswer(invocation -> {
            List<UUID> policyIds = invocation.getArgument(0);
            int limit = invocation.getArgument(2);
            return ranked.stream().filter(m -> policyIds.contains(m.policyId())).limit(limit).toList();
        });
    }

    // 조회 상한이 3청크이고 대출이 가장 가깝다. 조회한 뒤에 대출을 버리면 결과가 0건이 된다 (#201)
    @Test
    void 대출_의도가_없으면_대출_상품을_빼고_다른_정책으로_k개를_채운다() {
        List<Policy> loans = List.of(loan("대출 1"), loan("대출 2"), loan("대출 3"));
        List<Policy> supports = List.of(policy("지원 1"), policy("지원 2"), policy("지원 3"));
        stubRankedSearch("월세가 너무 부담돼", List.of(
                match(loans.get(0), "전체", "본문", 0.10), match(loans.get(1), "전체", "본문", 0.11),
                match(loans.get(2), "전체", "본문", 0.12), match(supports.get(0), "전체", "본문", 0.30),
                match(supports.get(1), "전체", "본문", 0.31), match(supports.get(2), "전체", "본문", 0.32)));

        List<PolicySearchHit> hits = service(3, 0.65, 1).searchCandidates(
                List.of(loans.get(0), loans.get(1), loans.get(2), supports.get(0), supports.get(1), supports.get(2)),
                "월세가 너무 부담돼");

        assertThat(hits).extracting(hit -> hit.policy().getName()).containsExactly("지원 1", "지원 2", "지원 3");
    }

    @Test
    void 대출_의도가_있으면_대출_상품을_유지한다() {
        Policy loan = loan("전세 대출");
        Policy support = policy("월세 지원");
        stubRankedSearch("전세 대출 알아보고 있어", List.of(
                match(loan, "전체", "본문", 0.10), match(support, "전체", "본문", 0.30)));

        List<PolicySearchHit> hits = service(5, 0.65, 3).searchCandidates(List.of(loan, support), "전세 대출 알아보고 있어");

        assertThat(hits).extracting(hit -> hit.policy().getName()).containsExactly("전세 대출", "월세 지원");
    }

    @Test
    void 대출_상품만_남으면_임베딩을_호출하지_않고_빈_결과다() {
        assertThat(service(5, 0.65, 3).searchCandidates(List.of(loan("대출 1")), "월세가 너무 부담돼")).isEmpty();

        verify(embeddingClient, never()).embed(anyList());
    }

    @Test
    void 혜택을_아직_추출하지_않은_정책은_대출_상품으로_보지_않는다() {
        assertThat(policy("미추출 정책").isLoanProduct()).isFalse();
        assertThat(loan("대출").isLoanProduct()).isTrue();
    }

    @Test
    void 유사도_하한값은_설정에서_읽고_없으면_기본값을_쓴다() {
        Policy a = policy("정책 A");
        stubEmbedding();
        when(searchRepository.search(anyList(), eq(VECTOR), anyInt())).thenReturn(List.of(match(a, "전체", "본문", 0.60)));
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(PolicyService.class, () -> policyService)
                .withBean(PolicyChunkSearchRepository.class, () -> searchRepository)
                .withBean(AiEmbeddingClient.class, () -> embeddingClient)
                .withBean(AiUsageLimiter.class, () -> usageLimiter)
                .withBean(PolicySearchService.class);

        runner.withPropertyValues("policy.search.max-distance=0.55").run(context ->
                assertThat(context.getBean(PolicySearchService.class).searchCandidates(List.of(a), "질문")).isEmpty());
        runner.run(context ->
                assertThat(context.getBean(PolicySearchService.class).searchCandidates(List.of(a), "질문")).hasSize(1));
        assertThat(PolicySearchService.DEFAULT_MAX_DISTANCE).isEqualTo(0.65);
    }

    @Test
    void k와_배수_설정값이_1보다_작으면_기동에_실패한다() {
        assertThatThrownBy(() -> service(0, 0.5, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(5, 0.5, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 한도_설정값이_0_이하면_무제한으로_기동한다() {
        // 0 이하는 무제한이라 기동을 막지 않는다. 차감 여부는 AiUsageLimiter가 판단한다 (#239)
        assertThatCode(() -> service(5, 0.5, 3, 0)).doesNotThrowAnyException();
        assertThatCode(() -> service(5, 0.5, 3, -1)).doesNotThrowAnyException();
        assertThat(PolicySearchService.DEFAULT_DAILY_LIMIT).isZero();
    }

    // #235 통합 답변 전용 검색

    private Policy withSource(Policy policy, String source) {
        ReflectionTestUtils.setField(policy, "source", source);
        return policy;
    }

    private Policy withKind(Policy policy, BenefitKind kind) {
        policy.updateCondition(PolicyCondition.builder().benefitKind(kind).build());
        return policy;
    }

    @Test
    void 통합_답변_검색은_대출_의도가_없으면_대출만_빼고_온통청년_이자_보증료_지원은_남긴다() {
        Policy kinfaLoan = withKind(withSource(loan("햇살론유스"), "KINFA"), BenefitKind.MONEY);
        Policy plainLoan = withKind(withSource(loan("주거안정월세대출"), "YOUTH_CENTER"), BenefitKind.LOAN);
        Policy unknownLoan = withSource(loan("분석 전 대출"), "YOUTH_CENTER");
        Policy interestSupport = withKind(withSource(loan("부산 청년 임차보증금 대출이자 지원"), "YOUTH_CENTER"), BenefitKind.MONEY);
        Policy rent = policy("청년 월세 지원");
        String question = "보증금 부담 줄이는 방법 있어?";
        List<Policy> candidates = List.of(kinfaLoan, plainLoan, unknownLoan, interestSupport, rent);
        stubRankedSearch(question, List.of(
                match(kinfaLoan, "전체", "햇살론", 0.30),
                match(plainLoan, "전체", "월세대출", 0.31),
                match(unknownLoan, "전체", "대출", 0.32),
                match(interestSupport, "전체", "이자 지원", 0.35),
                match(rent, "전체", "월세", 0.40)));

        List<PolicySearchHit> hits = service(5, 0.65, 3).searchForAnswer(candidates, question);

        assertThat(hits).extracting(PolicySearchHit::policy).containsExactly(interestSupport, rent);
        // 대출 의도가 있으면 모두 대상
        assertThat(PolicySearchService.excludeLoansExceptSupportUnlessAsked(candidates, "대출 이자 지원 있어?")).hasSize(5);
        // 정책 검색 API 경로(#182, #201)는 그대로: 혜택 유형 LOAN을 모두 뺀다
        assertThat(service(5, 0.65, 3).searchCandidates(candidates, question))
                .extracting(PolicySearchHit::policy).containsExactly(rent);
    }

    @Test
    void 통합_답변_검색은_질문에_정책명이_있으면_거리와_상관없이_맨_앞에_넣는다() {
        Policy stay = withSource(loan("부산 청년 임차보증금 대출 및 대출이자 지원(머물자리론)"), "YOUTH_CENTER");
        Policy a = policy("부산 청년 중개보수 지원");
        Policy b = policy("청년 월세 지원");
        String question = "머물자리론 지금 신청 가능해?";
        when(embeddingClient.embed(List.of(question))).thenReturn(List.of(VECTOR));
        // 벡터 검색 조회 상한 안에 머물자리론 청크가 없다 (질문이 짧아 거리가 멀다)
        when(searchRepository.search(anyList(), eq(VECTOR), eq(15))).thenReturn(List.of(
                match(a, "전체", "중개보수", 0.50), match(b, "전체", "월세", 0.60)));
        when(searchRepository.search(eq(List.of(stay.getPolicyId())), eq(VECTOR), eq(1)))
                .thenReturn(List.of(match(stay, "전체", "머물자리론 본문", 0.70)));

        List<PolicySearchHit> hits = service(5, 0.65, 3).searchForAnswer(List.of(stay, a, b), question);

        assertThat(hits).extracting(PolicySearchHit::policy).containsExactly(stay, a, b);
        assertThat(hits.getFirst().content()).isEqualTo("머물자리론 본문");
    }

    @Test
    void 통합_답변_검색에서_이름이_맞은_정책이_벡터_결과에_있으면_그_청크를_쓰고_k건을_넘지_않는다() {
        Policy kpass = policy("K-패스(K패스)");
        Policy a = policy("A 정책");
        Policy b = policy("B 정책");
        String question = "K-패스 지금 신청할 수 있어?";
        when(embeddingClient.embed(List.of(question))).thenReturn(List.of(VECTOR));
        when(searchRepository.search(anyList(), eq(VECTOR), eq(6))).thenReturn(List.of(
                match(a, "전체", "A", 0.40), match(b, "전체", "B", 0.45), match(kpass, "전체", "K-패스 청크", 0.55)));

        List<PolicySearchHit> hits = service(2, 0.65, 3).searchForAnswer(List.of(kpass, a, b), question);

        assertThat(hits).extracting(PolicySearchHit::policy).containsExactly(kpass, a);
        assertThat(hits.getFirst().content()).isEqualTo("K-패스 청크");
        verify(searchRepository, never()).search(eq(List.of(kpass.getPolicyId())), any(), eq(1));
    }

    @Test
    void 통합_답변_검색은_후보가_없으면_임베딩을_호출하지_않는다() {
        assertThat(service(5, 0.65, 3).searchForAnswer(List.of(), "질문")).isEmpty();
        verifyNoInteractions(embeddingClient, searchRepository);
    }
}
