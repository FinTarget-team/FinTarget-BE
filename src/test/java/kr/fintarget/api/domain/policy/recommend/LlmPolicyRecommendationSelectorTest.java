package kr.fintarget.api.domain.policy.recommend;

import kr.fintarget.api.common.config.OpenAiProperties;
import kr.fintarget.api.domain.ai.client.AiCallFailedException;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiModelTier;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.policy.condition.BenefitKind;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyCondition;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.recommend.LlmPolicyRecommendationSelector.RefreshOutcome;
import kr.fintarget.api.domain.policy.recommend.rerank.PolicyRerankCache;
import kr.fintarget.api.domain.policy.recommend.rerank.PolicyRerankPrompt;
import kr.fintarget.api.domain.user.code.IncomeBracket;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmPolicyRecommendationSelectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final int N = 3;
    private static final OpenAiProperties WITH_KEY = properties("test-key");
    private static final OpenAiProperties WITHOUT_KEY = properties("");

    private final RuleBasedPolicyRecommendationSelector rule = new RuleBasedPolicyRecommendationSelector(N, List.of());
    private final AiTextClient aiTextClient = mock(AiTextClient.class);
    private final PolicyRerankCache cache = new PolicyRerankCache();
    private final ExecutorService callExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<Runnable> retries = new ArrayList<>();

    @AfterEach
    void tearDown() {
        callExecutor.shutdownNow();
    }

    private static OpenAiProperties properties(String apiKey) {
        return new OpenAiProperties(apiKey, null, null, null, null, null, null, null, null, null);
    }

    private LlmPolicyRecommendationSelector selector() {
        return selector(true, WITH_KEY, Duration.ofSeconds(5));
    }

    private LlmPolicyRecommendationSelector selector(boolean enabled, OpenAiProperties properties, Duration timeout) {
        return new LlmPolicyRecommendationSelector(rule, aiTextClient, properties, new PolicyRerankPrompt(), cache,
                enabled, timeout, 3000, callExecutor, retries::add);
    }

    private static User user(String id, Long income) {
        return User.builder().id(id).provider(SocialProvider.KAKAO).providerId("p-" + id).region("SEOUL")
                .birthYear(2000).income(income).employmentType("UNEMPLOYED").build();
    }

    private static final User USER = user("u1", IncomeBracket.RANGE_150_260.getIncome());

    private static Policy policy(String name) {
        Policy policy = Policy.create(name, "설명", 19, 34, null, null, PolicyType.FINANCE_WELFARE, null, name,
                "YOUTH_CENTER", PolicyText.builder().applyPeriodCode("0057002").supportContent(name + " 지원 내용").build());
        policy.updateCondition(PolicyCondition.builder().benefitKind(BenefitKind.MONEY).specialTarget(false).build());
        ReflectionTestUtils.setField(policy, "policyId", UUID.nameUUIDFromBytes(name.getBytes()));
        return policy;
    }

    // 후보 8건 (N=3). 규칙 순서는 정책 ID 순이라 rerankCandidates 결과로 확인한다
    private static final List<Policy> SORTED = IntStream.rangeClosed(1, 8).mapToObj(i -> policy("정책 " + i)).toList();

    private List<Policy> candidates() {
        return rule.rerankCandidates(USER, SORTED, TODAY);
    }

    private List<Policy> ruleTopN() {
        return rule.select(USER, SORTED, TODAY);
    }

    private static AiTextResult reply(String text) {
        return new AiTextResult(text, "gpt-6.1-sol", 4000, 600, 200);
    }

    // 후보 번호(1부터)로 JSON 응답을 만든다
    private static String json(Object... ids) {
        return "{\"selected\":[" + java.util.Arrays.stream(ids)
                .map(id -> "{\"policyId\":" + (id instanceof String s ? "\"" + s + "\"" : id) + ",\"reason\":\"이유\"}")
                .collect(Collectors.joining(",")) + "]}";
    }

    private static List<String> names(List<Policy> policies) {
        return policies.stream().map(Policy::getName).toList();
    }

    @Test
    void 정상_응답이면_LLM이_고른_순서대로_N건을_돌려주고_상위_모델로_호출한다() {
        List<Policy> candidates = candidates();
        when(aiTextClient.generate(any())).thenReturn(reply(json(8, 5, 2)));

        List<Policy> result = selector().select(USER, SORTED, TODAY);

        assertThat(result).containsExactly(candidates.get(7), candidates.get(4), candidates.get(1));
        verify(aiTextClient).generate(org.mockito.ArgumentMatchers.argThat((AiTextRequest request) ->
                request.tier() == AiModelTier.PREMIUM
                        && request.maxOutputTokens() == 3000
                        && request.systemPrompt().contains("반드시 3건을 채웁니다")
                        && request.messages().getFirst().content().contains("[8] 정책")
                        && !request.messages().getFirst().content().contains(candidates.getFirst().getPolicyId().toString())));
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void 사용자_식별_정보는_프롬프트에_넣지_않는다() {
        User named = User.builder().id("u-named").provider(SocialProvider.KAKAO).providerId("p").name("홍길동")
                .email("hong@example.com").region("SEOUL").birthYear(2000).income(IncomeBracket.UNDER_150.getIncome()).build();
        when(aiTextClient.generate(any())).thenReturn(reply(json(1, 2, 3)));

        selector().select(named, SORTED, TODAY);

        verify(aiTextClient).generate(org.mockito.ArgumentMatchers.argThat((AiTextRequest request) -> {
            String message = request.messages().getFirst().content();
            return !message.contains("홍길동") && !message.contains("hong@example.com") && !message.contains("2000")
                    && !message.contains("u-named") && message.contains("거주 지역: 서울");
        }));
    }

    @Test
    void 파싱_실패면_규칙_상위_N이고_캐시하지_않는다() {
        when(aiTextClient.generate(any())).thenReturn(reply("추천 정책은 1번입니다"));

        assertThat(selector().select(USER, SORTED, TODAY)).isEqualTo(ruleTopN());
        assertThat(cache.size()).isZero();
    }

    @Test
    void 후보_밖_ID와_중복은_버리고_모자라면_규칙_순서로_채운다() {
        List<Policy> candidates = candidates();
        when(aiTextClient.generate(any())).thenReturn(reply(json(99, 6, 0, 6, "abc")));

        List<Policy> result = selector().select(USER, SORTED, TODAY);

        // 6번 하나만 남고, 규칙 상위 N(1·2·3번) 순서로 채운다
        assertThat(result).containsExactly(candidates.get(5), candidates.get(0), candidates.get(1));
    }

    @Test
    void N건_미만이면_규칙_순서로_채우고_이미_고른_정책은_다시_넣지_않는다() {
        List<Policy> candidates = candidates();
        when(aiTextClient.generate(any())).thenReturn(reply(json(2, 7)));

        assertThat(selector().select(USER, SORTED, TODAY))
                .containsExactly(candidates.get(1), candidates.get(6), candidates.get(0));
    }

    @Test
    void 고른_정책이_0건이면_규칙_상위_N이고_캐시하지_않는다() {
        when(aiTextClient.generate(any())).thenReturn(reply("{\"selected\":[]}"));

        assertThat(selector().select(USER, SORTED, TODAY)).isEqualTo(ruleTopN());
        assertThat(cache.size()).isZero();
    }

    @Test
    void AI_호출이_실패하면_규칙_상위_N이다() {
        when(aiTextClient.generate(any())).thenThrow(AiClientException.of(AiClientException.Reason.SERVER_ERROR));

        assertThat(selector().select(USER, SORTED, TODAY)).isEqualTo(ruleTopN());
        assertThat(cache.size()).isZero();
    }

    @Test
    void 입력_해시가_같으면_캐시를_쓰고_LLM을_다시_호출하지_않는다() {
        List<Policy> candidates = candidates();
        when(aiTextClient.generate(any())).thenReturn(reply(json(4, 5, 6)));
        LlmPolicyRecommendationSelector selector = selector();

        List<Policy> first = selector.select(USER, SORTED, TODAY);
        List<Policy> second = selector.select(USER, SORTED, TODAY);

        assertThat(second).isEqualTo(first).containsExactly(candidates.get(3), candidates.get(4), candidates.get(5));
        verify(aiTextClient, times(1)).generate(any());
    }

    @Test
    void 사용자_정보나_후보가_바뀌면_다시_계산한다() {
        when(aiTextClient.generate(any())).thenReturn(reply(json(4, 5, 6)));
        LlmPolicyRecommendationSelector selector = selector();

        selector.select(USER, SORTED, TODAY);
        // 소득 구간 변경
        selector.select(user("u1", IncomeBracket.OVER_410.getIncome()), SORTED, TODAY);
        // 후보 변경 (마지막 정책 빠짐)
        selector.select(user("u1", IncomeBracket.OVER_410.getIncome()), SORTED.subList(0, 7), TODAY);

        verify(aiTextClient, times(3)).generate(any());
    }

    @Test
    void 캐시에_있는_정책이_현재_후보에서_빠졌으면_빼고_규칙_순서로_채운다() {
        List<Policy> candidates = candidates();
        List<Policy> current = candidates.subList(0, 6);
        List<UUID> cached = List.of(candidates.get(7).getPolicyId(), candidates.get(4).getPolicyId(), candidates.get(6).getPolicyId());

        List<Policy> result = LlmPolicyRecommendationSelector.merge(cached, current, current.subList(0, N), N);

        assertThat(result).containsExactly(candidates.get(4), candidates.get(0), candidates.get(1));
    }

    @Test
    void 시간_초과면_규칙_상위_N을_돌려주고_캐시하지_않으며_비동기로_한_번_재시도한다() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(aiTextClient.generate(any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                release.await(5, TimeUnit.SECONDS);
                // 시간 초과로 포기된 뒤 끝나도 캐시에 넣지 않아야 한다
            }
            return reply(json(8, 7, 6));
        });
        LlmPolicyRecommendationSelector selector = selector(true, WITH_KEY, Duration.ofMillis(200));

        List<Policy> result = selector.select(USER, SORTED, TODAY);

        assertThat(result).isEqualTo(ruleTopN());
        assertThat(cache.size()).isZero();
        assertThat(retries).hasSize(1);

        release.countDown();
        retries.getFirst().run();
        assertThat(cache.size()).isEqualTo(1);
        assertThat(selector.select(USER, SORTED, TODAY)).containsExactly(candidates().get(7), candidates().get(6), candidates().get(5));
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void 같은_사용자의_계산이_진행_중이면_그_결과를_기다려_돌려주고_LLM은_한_번만_호출한다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(aiTextClient.generate(any())).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return reply(json(8, 7, 6));
        });
        LlmPolicyRecommendationSelector selector = selector();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<Policy>> first = pool.submit(() -> selector.select(USER, SORTED, TODAY));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            // 두 번째 조회는 새로 호출하지 않고 첫 번째 계산을 기다린다 (#243)
            Future<List<Policy>> second = pool.submit(() -> selector.select(USER, SORTED, TODAY));
            Thread.sleep(100);
            assertThat(second.isDone()).isFalse();

            release.countDown();
            List<Policy> reranked = List.of(candidates().get(7), candidates().get(6), candidates().get(5));
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(reranked);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(reranked);
        }
        verify(aiTextClient, times(1)).generate(any());
    }

    @Test
    void 미리_계산도_진행_중인_계산과_겹치지_않고_입력이_다른_계산은_기다리지_않는다() {
        LlmPolicyRecommendationSelector selector = selector();
        cache.tryStart(USER.getId(), "다른 입력 해시");

        assertThat(selector.refresh(USER, SORTED, TODAY)).isEqualTo(RefreshOutcome.IN_FLIGHT);
        assertThat(selector.select(USER, SORTED, TODAY)).isEqualTo(ruleTopN());
        verify(aiTextClient, never()).generate(any());
    }

    @Test
    void 미리_계산은_캐시가_없을_때만_호출한다() {
        when(aiTextClient.generate(any())).thenReturn(reply(json(1, 2, 3)));
        LlmPolicyRecommendationSelector selector = selector();

        assertThat(selector.refresh(USER, SORTED, TODAY)).isEqualTo(RefreshOutcome.COMPUTED);
        assertThat(selector.refresh(USER, SORTED, TODAY)).isEqualTo(RefreshOutcome.CACHED);
        verify(aiTextClient, times(1)).generate(any());
    }

    @Test
    void 미리_계산이_실패하면_FAILED이고_예외를_던지지_않는다() {
        when(aiTextClient.generate(any())).thenThrow(new AiCallFailedException(AiClientException.Reason.TIMEOUT));

        assertThat(selector().refresh(USER, SORTED, TODAY)).isEqualTo(RefreshOutcome.FAILED);
    }

    @Test
    void 리랭킹이_꺼져_있으면_LLM을_호출하지_않고_규칙_상위_N이다() {
        LlmPolicyRecommendationSelector selector = selector(false, WITH_KEY, Duration.ofSeconds(5));

        assertThat(selector.select(USER, SORTED, TODAY)).isEqualTo(ruleTopN());
        assertThat(selector.refresh(USER, SORTED, TODAY)).isEqualTo(RefreshOutcome.DISABLED);
        verify(aiTextClient, never()).generate(any());
    }

    @Test
    void AI_키가_없으면_LLM을_호출하지_않고_규칙_상위_N이다() {
        LlmPolicyRecommendationSelector selector = selector(true, WITHOUT_KEY, Duration.ofSeconds(5));

        assertThat(selector.isAvailable()).isFalse();
        assertThat(selector.select(USER, SORTED, TODAY)).isEqualTo(ruleTopN());
        verify(aiTextClient, never()).generate(any());
    }

    @Test
    void 실행_중_스위치로_끄면_캐시가_있어도_규칙_상위_N이다() {
        when(aiTextClient.generate(any())).thenReturn(reply(json(8, 7, 6)));
        LlmPolicyRecommendationSelector selector = selector();
        selector.select(USER, SORTED, TODAY);

        selector.setEnabled(false);

        assertThat(selector.select(USER, SORTED, TODAY)).isEqualTo(ruleTopN());
        selector.setEnabled(true);
        assertThat(selector.select(USER, SORTED, TODAY)).containsExactly(candidates().get(7), candidates().get(6), candidates().get(5));
        verify(aiTextClient, times(1)).generate(any());
    }

    @Test
    void 정책_유형_필터_목록은_LLM을_호출하지_않고_규칙_결과다() {
        assertThat(selector().selectWithinType(USER, SORTED, TODAY)).isEqualTo(ruleTopN());
        verify(aiTextClient, never()).generate(any());
    }

    @Test
    void 후보가_N건_이하면_LLM을_호출하지_않는다() {
        List<Policy> few = SORTED.subList(0, N);

        assertThat(selector().select(USER, few, TODAY)).isEqualTo(rule.select(USER, few, TODAY));
        assertThat(selector().select(USER, List.of(), TODAY)).isEmpty();
        verify(aiTextClient, never()).generate(any());
    }

    @Test
    void 리랭킹_후에도_규칙_선택기의_상위_N과_후보_목록은_그대로다() {
        List<Policy> topNBefore = ruleTopN();
        List<Policy> candidatesBefore = candidates();
        List<Policy> sortedBefore = List.copyOf(SORTED);
        when(aiTextClient.generate(any())).thenReturn(reply(json(8, 7, 6)));

        selector().select(USER, SORTED, TODAY);

        assertThat(ruleTopN()).isEqualTo(topNBefore);
        assertThat(candidates()).isEqualTo(candidatesBefore);
        assertThat(candidatesBefore.subList(0, N)).isEqualTo(topNBefore);
        assertThat(SORTED).isEqualTo(sortedBefore);
    }

    // 시·군·구 한정 정책: 정형값(zipCd) 또는 AI 추출값
    private static Policy localPolicy(String name, boolean structured) {
        Policy policy = policy(name);
        if (structured) {
            policy.withRegionLocalScope(true);
        } else {
            policy.updateCondition(PolicyCondition.builder().benefitKind(BenefitKind.MONEY).specialTarget(false)
                    .localScope(true).build());
        }
        return policy;
    }

    @Test
    void 시군구_한정_정책은_LLM_입력에서_빠지고_후보_번호도_한정_아닌_정책만_매긴다() {
        List<Policy> withLocal = new ArrayList<>(SORTED);
        withLocal.add(localPolicy("영월군 주거비", true));
        withLocal.add(localPolicy("여수시 도서구입비", false));
        when(aiTextClient.generate(any())).thenReturn(reply(json(1, 2, 3)));

        selector().select(USER, withLocal, TODAY);

        verify(aiTextClient).generate(org.mockito.ArgumentMatchers.argThat((AiTextRequest request) -> {
            String message = request.messages().getFirst().content();
            return message.contains("[후보 정책] (8건)") && !message.contains("영월군") && !message.contains("여수시")
                    && !message.contains("[9]");
        }));
        // 규칙 후보 자체에는 그대로 있다 (리랭킹 입력 단계에서만 거름)
        assertThat(names(rule.rerankCandidates(USER, withLocal, TODAY))).contains("영월군 주거비", "여수시 도서구입비");
    }

    @Test
    void 채울_때_시군구_한정_정책은_다른_정책을_다_쓴_뒤_마지막에만_쓴다() {
        Policy local1 = localPolicy("영월군 주거비", true);
        Policy local2 = localPolicy("여수시 도서구입비", false);
        Policy a = policy("정책 A");
        Policy b = policy("정책 B");
        // 규칙 상위 N에 한정 정책이 앞에 있어도 한정 아닌 정책을 먼저 채운다
        List<Policy> candidates = List.of(local1, a, local2, b);
        List<Policy> ruleTopN = List.of(local1, a, local2);

        assertThat(LlmPolicyRecommendationSelector.merge(List.of(), candidates, ruleTopN, 3)).containsExactly(a, b, local1);
        assertThat(LlmPolicyRecommendationSelector.merge(List.of(b.getPolicyId()), candidates, ruleTopN, 4))
                .containsExactly(b, a, local1, local2);
        // LLM이 한정 정책 ID를 돌려줘도(캐시 등) 앞에 두지 않는다
        assertThat(LlmPolicyRecommendationSelector.merge(List.of(local2.getPolicyId(), b.getPolicyId()), candidates, ruleTopN, 2))
                .containsExactly(b, a);
    }

    @Test
    void 한정_정책을_뺀_후보가_N건_이하면_LLM을_호출하지_않고_규칙_상위_N이다() {
        List<Policy> few = new ArrayList<>(SORTED.subList(0, N));
        few.add(localPolicy("영월군 주거비", true));
        few.add(localPolicy("강진군 주거비", true));

        assertThat(selector().select(USER, few, TODAY)).isEqualTo(rule.select(USER, few, TODAY));
        assertThat(selector().refresh(USER, few, TODAY)).isEqualTo(RefreshOutcome.NOT_NEEDED);
        verify(aiTextClient, never()).generate(any());
    }

    // ===== 진행 중인 계산 기다리기 (#243) =====

    // 미리 계산(온보딩 6단계)을 다른 스레드에서 시작하고 LLM 호출에 들어갈 때까지 기다린다
    private Future<RefreshOutcome> startPrecompute(ExecutorService pool, LlmPolicyRecommendationSelector selector,
                                                   CountDownLatch started) throws InterruptedException {
        Future<RefreshOutcome> precompute = pool.submit(() -> selector.refresh(USER, SORTED, TODAY));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        return precompute;
    }

    @Test
    void 미리_계산이_진행_중일_때_조회하면_끝나기를_기다려_리랭킹_결과를_받는다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(aiTextClient.generate(any())).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return reply(json(8, 5, 2));
        });
        LlmPolicyRecommendationSelector selector = selector();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<RefreshOutcome> precompute = startPrecompute(pool, selector, started);
            Future<List<Policy>> listed = pool.submit(() -> selector.select(USER, SORTED, TODAY));
            Thread.sleep(100);
            assertThat(listed.isDone()).isFalse();

            release.countDown();
            assertThat(listed.get(5, TimeUnit.SECONDS))
                    .containsExactly(candidates().get(7), candidates().get(4), candidates().get(1));
            assertThat(precompute.get(5, TimeUnit.SECONDS)).isEqualTo(RefreshOutcome.COMPUTED);
        }
        verify(aiTextClient, times(1)).generate(any());
    }

    @Test
    void 기다리다_시간을_넘기면_규칙_결과를_돌려주고_새로_호출하지_않는다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(aiTextClient.generate(any())).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return reply(json(8, 5, 2));
        });
        LlmPolicyRecommendationSelector selector = selector(true, WITH_KEY, Duration.ofMillis(300));

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            startPrecompute(pool, selector, started);
            long start = System.nanoTime();
            List<Policy> listed = selector.select(USER, SORTED, TODAY);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(listed).isEqualTo(ruleTopN());
            assertThat(elapsedMs).isLessThan(2_000);
            release.countDown();
        }
        verify(aiTextClient, times(1)).generate(any());
    }

    @Test
    void 진행_중인_계산이_시작된_뒤_지난_시간을_빼고_남은_시간만_기다린다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(aiTextClient.generate(any())).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return reply(json(8, 5, 2));
        });
        LlmPolicyRecommendationSelector selector = selector(true, WITH_KEY, Duration.ofMillis(1_000));

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            startPrecompute(pool, selector, started);
            Thread.sleep(700);
            long start = System.nanoTime();
            List<Policy> listed = selector.select(USER, SORTED, TODAY);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            // timeout 1초 중 0.7초가 이미 지났으므로 약 0.3초만 기다린다
            assertThat(listed).isEqualTo(ruleTopN());
            assertThat(elapsedMs).isLessThan(800);
            release.countDown();
        }
    }

    @Test
    void 기다리던_계산이_실패하면_규칙_결과를_돌려준다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(aiTextClient.generate(any())).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            throw new AiCallFailedException(AiClientException.Reason.SERVER_ERROR);
        });
        LlmPolicyRecommendationSelector selector = selector();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<RefreshOutcome> precompute = startPrecompute(pool, selector, started);
            Future<List<Policy>> listed = pool.submit(() -> selector.select(USER, SORTED, TODAY));
            Thread.sleep(100);

            release.countDown();
            long start = System.nanoTime();
            assertThat(listed.get(5, TimeUnit.SECONDS)).isEqualTo(ruleTopN());
            // 실패를 timeout(5초)까지 기다리지 않고 바로 알아챈다
            assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_000);
            assertThat(precompute.get(5, TimeUnit.SECONDS)).isEqualTo(RefreshOutcome.FAILED);
        }
        verify(aiTextClient, times(1)).generate(any());
        assertThat(cache.inFlight(USER.getId())).isEmpty();
    }

    @Test
    void 같은_사용자가_동시에_여러_번_조회해도_LLM은_한_번만_호출하고_모두_리랭킹_결과를_받는다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(aiTextClient.generate(any())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return reply(json(8, 7, 6));
        });
        LlmPolicyRecommendationSelector selector = selector();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<Policy>>> lists = new ArrayList<>();
            lists.add(pool.submit(() -> selector.select(USER, SORTED, TODAY)));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 5; i++) {
                lists.add(pool.submit(() -> selector.select(USER, SORTED, TODAY)));
            }
            Thread.sleep(100);
            release.countDown();

            List<Policy> reranked = List.of(candidates().get(7), candidates().get(6), candidates().get(5));
            for (Future<List<Policy>> listed : lists) {
                assertThat(listed.get(5, TimeUnit.SECONDS)).isEqualTo(reranked);
            }
        }
        assertThat(calls.get()).isEqualTo(1);
    }
}
