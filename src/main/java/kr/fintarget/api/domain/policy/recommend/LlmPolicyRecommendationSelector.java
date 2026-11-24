package kr.fintarget.api.domain.policy.recommend;

import jakarta.annotation.PreDestroy;
import kr.fintarget.api.common.config.OpenAiProperties;
import kr.fintarget.api.domain.ai.chat.service.UserProfileSummary;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiMessage;
import kr.fintarget.api.domain.ai.client.AiModelTier;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.recommend.rerank.PolicyRerankCache;
import kr.fintarget.api.domain.policy.recommend.rerank.PolicyRerankCandidateLine;
import kr.fintarget.api.domain.policy.recommend.rerank.PolicyRerankParseException;
import kr.fintarget.api.domain.policy.recommend.rerank.PolicyRerankParser;
import kr.fintarget.api.domain.policy.recommend.rerank.PolicyRerankPrompt;
import kr.fintarget.api.domain.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 규칙 후보 중 LLM이 사용자에게 가장 맞는 N건을 고른다 (#229). 기본으로 주입되는 추천 선택기다.
 *
 * <ol>
 *   <li>후보: {@link RuleBasedPolicyRecommendationSelector#rerankCandidates} (최대 M건, 앞 N건은 규칙 상위 N)에서
 *       시·군·구 한정 정책({@link PolicyLocalScope})을 뺀 목록. 온보딩 지역이 시·도 단위라 그 시·군·구 거주 여부를 알 수 없다</li>
 *   <li>상위 모델(openai.model.premium)에 사용자 정보 5개(구간만)와 후보 한 줄 요약을 보내고 최대 N건을 적합한 순서로 받는다</li>
 *   <li>후보 밖·중복·형식 오류 항목은 버리고, N건 미만이면 규칙 순서로 채운다(시·군·구 한정 정책은 맨 마지막). 0건이거나 해석할 수 없으면 규칙 상위 N</li>
 *   <li>결과는 사용자별 메모리 캐시({@link PolicyRerankCache})에 둔다. 입력 해시(사용자 정보 + 후보 정책 ID 목록 + 프롬프트 버전)가 같으면 캐시를 쓴다</li>
 *   <li>캐시가 없으면 동기 호출(policy.recommend.rerank.timeout, 기본 15초). 실패·시간 초과면 규칙 상위 N을 돌려주고 캐시하지 않으며, 비동기로 한 번 다시 계산한다</li>
 *   <li>같은 사용자의 계산이 진행 중이면 기다리지 않고 규칙 상위 N</li>
 * </ol>
 * 리랭킹이 꺼져 있거나(policy.recommend.rerank.enabled=false, 관리자 API로 실행 중 변경 가능) AI 키가 없으면 LLM을 호출하지 않고 규칙 상위 N이다.
 * 한정 정책을 뺀 후보가 N건 이하면 고를 것이 없어 호출하지 않는다. 정책 유형 필터가 있는 목록은 규칙 결과를 쓴다 ({@link #selectWithinType}).
 * 어떤 경우에도 예외를 밖으로 던지지 않는다 (규칙 결과로 대체).
 */
@Slf4j
@Primary
@Component
public class LlmPolicyRecommendationSelector implements PolicyRecommendationSelector {

    // 출력은 정책 10건 × (번호 + 이유 한 문장) 약 700토큰이고, 추론 토큰(effort low)을 더해도 이 안에 든다
    static final int DEFAULT_MAX_OUTPUT_TOKENS = 3_000;

    // 요청 경로 호출 상태: 진행 중 → 저장(결과 사용) 또는 포기(시간 초과, 결과 버림) 중 하나로 한 번만 바뀐다
    private static final int CALL_RUNNING = 0;
    private static final int CALL_STORED = 1;
    private static final int CALL_ABANDONED = 2;

    private final RuleBasedPolicyRecommendationSelector ruleSelector;
    private final AiTextClient aiTextClient;
    private final OpenAiProperties openAiProperties;
    private final PolicyRerankPrompt prompt;
    private final PolicyRerankCache cache;
    private final AtomicBoolean enabled;
    private final Duration timeout;
    private final int maxOutputTokens;
    private final ExecutorService callExecutor;
    private final Executor retryExecutor;

    @Autowired
    public LlmPolicyRecommendationSelector(RuleBasedPolicyRecommendationSelector ruleSelector, AiTextClient aiTextClient,
                                           OpenAiProperties openAiProperties, PolicyRerankPrompt prompt,
                                           PolicyRerankCache cache,
                                           @Value("${policy.recommend.rerank.enabled:true}") boolean enabled,
                                           @Value("${policy.recommend.rerank.timeout:15s}") Duration timeout,
                                           @Value("${policy.recommend.rerank.max-output-tokens:" + DEFAULT_MAX_OUTPUT_TOKENS + "}") int maxOutputTokens) {
        this(ruleSelector, aiTextClient, openAiProperties, prompt, cache, enabled, timeout, maxOutputTokens,
                Executors.newVirtualThreadPerTaskExecutor(), null);
    }

    /** 실행기를 지정한다 (테스트용). retryExecutor가 null이면 callExecutor로 재시도한다 */
    LlmPolicyRecommendationSelector(RuleBasedPolicyRecommendationSelector ruleSelector, AiTextClient aiTextClient,
                                    OpenAiProperties openAiProperties, PolicyRerankPrompt prompt, PolicyRerankCache cache,
                                    boolean enabled, Duration timeout, int maxOutputTokens,
                                    ExecutorService callExecutor, Executor retryExecutor) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("policy.recommend.rerank.timeout은 0보다 커야 합니다: " + timeout);
        }
        this.ruleSelector = ruleSelector;
        this.aiTextClient = aiTextClient;
        this.openAiProperties = openAiProperties;
        this.prompt = prompt;
        this.cache = cache;
        this.enabled = new AtomicBoolean(enabled);
        this.timeout = timeout;
        this.maxOutputTokens = Math.max(1, maxOutputTokens);
        this.callExecutor = callExecutor;
        this.retryExecutor = retryExecutor == null ? callExecutor : retryExecutor;
    }

    @PreDestroy
    void shutdown() {
        callExecutor.shutdownNow();
    }

    @Override
    public List<Policy> select(User user, List<Policy> sortedCandidates, LocalDate today) {
        List<Policy> fallback = ruleSelector.select(user, sortedCandidates, today);
        try {
            return rerankOrFallback(user, sortedCandidates, today, fallback);
        } catch (RuntimeException e) {
            log.error("추천 리랭킹 처리 중 오류 - 규칙 결과 사용 userId={}", user.getId(), e);
            return fallback;
        }
    }

    @Override
    public List<Policy> selectWithinType(User user, List<Policy> sortedCandidates, LocalDate today) {
        return ruleSelector.select(user, sortedCandidates, today);
    }

    /** 리랭킹을 쓸 수 있는지 (설정·실행 중 스위치가 켜져 있고 AI 키가 있음) */
    public boolean isAvailable() {
        return enabled.get() && openAiProperties.hasApiKey();
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    /** 실행 중 리랭킹을 켜거나 끈다 (관리자 API). 재시작하면 설정값(policy.recommend.rerank.enabled)으로 돌아간다 */
    public void setEnabled(boolean value) {
        boolean previous = enabled.getAndSet(value);
        if (previous != value) {
            log.info("추천 리랭킹 스위치 변경 enabled={} → {}", previous, value);
        }
    }

    public String modelId() {
        return openAiProperties.model().idOf(AiModelTier.PREMIUM);
    }

    public String promptVersion() {
        return prompt.version();
    }

    /**
     * 미리 계산 (#229). 캐시가 없거나 입력이 바뀐 경우에만 LLM을 호출해 캐시를 채운다. 호출하는 스레드에서 끝날 때까지 기다린다
     * (시간 상한은 클라이언트 설정 openai.total-timeout). 예외를 던지지 않는다.
     */
    public RefreshOutcome refresh(User user, List<Policy> sortedCandidates, LocalDate today) {
        try {
            if (!isAvailable()) {
                return RefreshOutcome.DISABLED;
            }
            List<Policy> candidates = llmCandidates(ruleSelector.rerankCandidates(user, sortedCandidates, today));
            if (candidates.size() <= ruleSelector.limit()) {
                return RefreshOutcome.NOT_NEEDED;
            }
            RerankInput input = input(user, candidates, today);
            if (cache.find(user.getId(), input.hash()).isPresent()) {
                return RefreshOutcome.CACHED;
            }
            if (!cache.tryStart(user.getId())) {
                return RefreshOutcome.IN_FLIGHT;
            }
            try {
                return callAndStore(user.getId(), input, candidates, "precompute", () -> true)
                        ? RefreshOutcome.COMPUTED : RefreshOutcome.FAILED;
            } finally {
                cache.finish(user.getId());
            }
        } catch (RuntimeException e) {
            log.error("추천 리랭킹 미리 계산 중 오류 userId={}", user.getId(), e);
            return RefreshOutcome.FAILED;
        }
    }

    private List<Policy> rerankOrFallback(User user, List<Policy> sortedCandidates, LocalDate today, List<Policy> fallback) {
        if (!isAvailable()) {
            return fallback;
        }
        List<Policy> allCandidates = ruleSelector.rerankCandidates(user, sortedCandidates, today);
        List<Policy> candidates = llmCandidates(allCandidates);
        int limit = ruleSelector.limit();
        if (candidates.size() <= limit) {
            return fallback;
        }
        String userId = user.getId();
        RerankInput input = input(user, candidates, today);
        Optional<PolicyRerankCache.Entry> cached = cache.find(userId, input.hash());
        if (cached.isPresent()) {
            return merge(cached.get().policyIds(), allCandidates, fallback, limit);
        }
        if (!cache.tryStart(userId)) {
            log.info("추천 리랭킹 대체 userId={} candidates={} fallback=IN_FLIGHT", userId, candidates.size());
            return fallback;
        }

        // 시간 초과로 포기한 호출이 나중에 끝나도 캐시에 넣지 않도록, 결과를 쓸지 포기할지 한 번만 정한다
        AtomicInteger state = new AtomicInteger(CALL_RUNNING);
        Future<Boolean> future;
        try {
            future = callExecutor.submit(() -> callAndStore(userId, input, candidates, "request",
                    () -> state.compareAndSet(CALL_RUNNING, CALL_STORED)));
        } catch (RuntimeException e) {
            cache.finish(userId);
            throw e;
        }
        boolean timedOut = false;
        try {
            boolean stored = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (stored) {
                return cache.find(userId, input.hash())
                        .map(entry -> merge(entry.policyIds(), allCandidates, fallback, limit))
                        .orElse(fallback);
            }
            return fallback;
        } catch (TimeoutException e) {
            if (state.compareAndSet(CALL_RUNNING, CALL_ABANDONED)) {
                timedOut = true;
                future.cancel(true);
                log.warn("추천 리랭킹 대체 userId={} candidates={} fallback=TIMEOUT timeoutMs={} prompt={} model={}",
                        userId, candidates.size(), timeout.toMillis(), prompt.version(), modelId());
                return fallback;
            }
            // 시간 초과 직전에 결과를 저장했다 (드묾)
            return cache.find(userId, input.hash())
                    .map(entry -> merge(entry.policyIds(), allCandidates, fallback, limit))
                    .orElse(fallback);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state.compareAndSet(CALL_RUNNING, CALL_ABANDONED);
            future.cancel(true);
            return fallback;
        } catch (ExecutionException e) {
            log.error("추천 리랭킹 호출 오류 - 규칙 결과 사용 userId={}", userId, e.getCause());
            return fallback;
        } finally {
            cache.finish(userId);
            if (timedOut) {
                retryAsync(userId, input, candidates);
            }
        }
    }

    // 시간 초과 뒤 비동기로 한 번만 다시 계산한다. 재시도도 실패하면 다음 조회·미리 계산 때 다시 시도한다
    private void retryAsync(String userId, RerankInput input, List<Policy> candidates) {
        try {
            retryExecutor.execute(() -> {
                if (!isAvailable() || cache.find(userId, input.hash()).isPresent() || !cache.tryStart(userId)) {
                    return;
                }
                try {
                    callAndStore(userId, input, candidates, "retry", () -> true);
                } finally {
                    cache.finish(userId);
                }
            });
        } catch (RuntimeException e) {
            log.warn("추천 리랭킹 재시도 예약 실패 userId={}", userId);
        }
    }

    /**
     * LLM을 호출하고 결과를 검증해 캐시에 넣는다. 1건 이상 고르면 true. 실패·0건이면 캐시하지 않고 false.
     * 호출마다 사용자 ID, 후보 수, 선택 수, 버린 수, 대체 여부(사유), 소요 시간, 프롬프트 버전, 모델명, 토큰 수를 남긴다 (사용자 정보 값은 남기지 않음).
     *
     * @param canStore 결과를 캐시에 넣어도 되는지 (요청 경로에서 시간 초과로 포기했으면 false)
     */
    private boolean callAndStore(String userId, RerankInput input, List<Policy> candidates, String trigger,
                                 BooleanSupplier canStore) {
        long start = System.currentTimeMillis();
        int limit = ruleSelector.limit();
        String model = modelId();
        try {
            AiTextResult result = aiTextClient.generate(new AiTextRequest(prompt.instructions(limit),
                    List.of(AiMessage.user(input.userMessage())), AiModelTier.PREMIUM, maxOutputTokens));
            model = result.model();
            PolicyRerankParser.Selection selection = PolicyRerankParser.parse(result.text(), candidates.size(), limit);
            long elapsedMs = System.currentTimeMillis() - start;
            if (selection.numbers().isEmpty()) {
                log.warn("추천 리랭킹 호출 userId={} trigger={} candidates={} selected=0 dropped={} fallback=EMPTY_SELECTION "
                                + "elapsedMs={} prompt={} model={} inputTokens={} outputTokens={}",
                        userId, trigger, candidates.size(), selection.dropped(), elapsedMs, prompt.version(), model,
                        result.inputTokens(), result.outputTokens());
                return false;
            }
            List<UUID> ids = selection.numbers().stream().map(number -> candidates.get(number - 1).getPolicyId()).toList();
            boolean stored = canStore.getAsBoolean();
            if (stored) {
                cache.put(userId, new PolicyRerankCache.Entry(ids, selection.reasons(), input.hash(), prompt.version(),
                        model, LocalDateTime.now()));
            }
            log.info("추천 리랭킹 호출 userId={} trigger={} candidates={} selected={} dropped={} fallback={} elapsedMs={} "
                            + "prompt={} model={} inputTokens={} outputTokens={} reasoningTokens={}",
                    userId, trigger, candidates.size(), ids.size(), selection.dropped(),
                    stored ? (ids.size() < limit ? "FILLED_BY_RULE" : "none") : "TIMEOUT",
                    elapsedMs, prompt.version(), model, result.inputTokens(), result.outputTokens(), result.reasoningTokens());
            return stored;
        } catch (AiClientException e) {
            logFailure(userId, trigger, candidates.size(), e.getReason().name(), start, model);
        } catch (PolicyRerankParseException e) {
            logFailure(userId, trigger, candidates.size(), "PARSE_FAILED", start, model);
        } catch (RuntimeException e) {
            log.error("추천 리랭킹 호출 오류 userId={} trigger={}", userId, trigger, e);
        }
        return false;
    }

    private void logFailure(String userId, String trigger, int candidateCount, String reason, long start, String model) {
        log.warn("추천 리랭킹 호출 userId={} trigger={} candidates={} selected=0 dropped=0 fallback={} elapsedMs={} prompt={} model={}",
                userId, trigger, candidateCount, reason, System.currentTimeMillis() - start, prompt.version(), model);
    }

    private RerankInput input(User user, List<Policy> candidates, LocalDate today) {
        String profileText = UserProfileSummary.of(user, Year.of(today.getYear())).toPromptText();
        List<String> lines = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            lines.add(PolicyRerankCandidateLine.of(i + 1, candidates.get(i), user.getRegion(), today));
        }
        String ids = candidates.stream().map(policy -> String.valueOf(policy.getPolicyId())).collect(Collectors.joining(","));
        String hash = sha256(prompt.version() + "\n" + profileText + "\n" + ids);
        return new RerankInput(prompt.userMessage(profileText, lines), hash);
    }

    /** LLM에 넘길 후보: 리랭킹 후보에서 시·군·구 한정 정책을 뺀다 (순서 유지) */
    static List<Policy> llmCandidates(List<Policy> rerankCandidates) {
        return rerankCandidates.stream().filter(policy -> !PolicyLocalScope.isLocal(policy)).toList();
    }

    /**
     * LLM이 고른 정책을 순서대로 놓고, 현재 후보에 없는 ID와 시·군·구 한정 정책은 뺀 뒤 규칙 순서로 N건까지 채운다.
     * 채우는 순서는 규칙 상위 N 중 한정 아닌 정책 → 나머지 한정 아닌 후보 → 규칙 상위 N 중 한정 정책 → 나머지 한정 후보.
     * 시·군·구 한정 정책은 다른 정책을 다 쓴 뒤에만 들어간다.
     *
     * @param candidates 리랭킹 후보 전체 ({@link RuleBasedPolicyRecommendationSelector#rerankCandidates}, 한정 정책 포함)
     */
    static List<Policy> merge(List<UUID> chosenIds, List<Policy> candidates, List<Policy> ruleTopN, int limit) {
        Map<UUID, Policy> byId = candidates.stream()
                .filter(policy -> !PolicyLocalScope.isLocal(policy))
                .collect(Collectors.toMap(Policy::getPolicyId, Function.identity(), (a, b) -> a));
        Set<Policy> result = new LinkedHashSet<>();
        for (UUID id : chosenIds) {
            if (result.size() >= limit) {
                break;
            }
            Policy policy = byId.get(id);
            if (policy != null) {
                result.add(policy);
            }
        }
        for (boolean local : new boolean[]{false, true}) {
            for (List<Policy> source : List.of(ruleTopN, candidates)) {
                for (Policy policy : source) {
                    if (result.size() >= limit) {
                        return List.copyOf(result);
                    }
                    if (PolicyLocalScope.isLocal(policy) == local) {
                        result.add(policy);
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record RerankInput(String userMessage, String hash) {
    }

    /** 미리 계산 결과 */
    public enum RefreshOutcome {
        /** 새로 계산해 캐시에 넣음 */
        COMPUTED,
        /** 입력이 같은 캐시가 있어 건너뜀 */
        CACHED,
        /** 같은 사용자 계산이 진행 중이라 건너뜀 */
        IN_FLIGHT,
        /** 후보가 N건 이하라 고를 것이 없음 */
        NOT_NEEDED,
        /** 리랭킹이 꺼져 있거나 AI 키가 없음 */
        DISABLED,
        /** 호출 실패·해석 실패·0건 */
        FAILED
    }
}
