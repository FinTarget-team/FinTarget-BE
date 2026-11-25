package kr.fintarget.api.domain.policy.rag;

import jakarta.annotation.PreDestroy;
import kr.fintarget.api.domain.ai.client.AiMessage;
import kr.fintarget.api.domain.ai.client.AiModelTier;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.policy.search.AnswerQueryRewriter;
import kr.fintarget.api.domain.policy.search.QueryRewrite;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * 통합 답변 질문을 정책 검색용 짧은 문장으로 바꾼다 (#241). 기본 모델(openai.model.default)을 쓴다.
 * 바꾸는 규칙은 프롬프트 {@link PolicyRagQueryRewritePrompt}에 있다 (구어체·은어를 표준어로, 질문에 없는 조건은 넣지 않음, 관계없는 질문은 빈 출력).
 *
 * <p>요청 경로라 짧게 기다린다(policy.rag.query-rewrite.timeout, 기본 3초). 실패·시간 초과·빈 출력이면 결과로 알리고 예외를 던지지 않는다.
 * 검색은 원래 질문만으로 진행된다 ({@link kr.fintarget.api.domain.policy.search.PolicySearchService#searchForAnswer(List, String, AnswerQueryRewriter)}).
 */
@Slf4j
@Component
public class PolicyRagQueryRewriter implements AnswerQueryRewriter {

    // 로컬 평가(#241): 잘 찾던 정형 질문의 1위 거리는 0.33~0.495, 못 찾던 구어체 질문은 0.52 이상이다.
    // 0.45에서는 0.46~0.47대 정형 질문(전세 대출 이자 등)이 바꿔 쓰기로 대출 상품에 밀려 나빠져 0.50으로 올렸다
    static final double DEFAULT_SKIP_DISTANCE = 0.50;
    // 출력은 40자 이내 한 줄(약 30토큰)이고 추론 토큰을 더해도 이 안에 든다. 넘으면 실패로 보고 원래 질문만 쓴다
    static final int DEFAULT_MAX_OUTPUT_TOKENS = 300;
    // 모델이 지시와 달리 길게 쓰면 앞부분만 쓴다
    static final int MAX_TEXT_LENGTH = 80;

    private static final Pattern PREFIX = Pattern.compile("^(검색\\s*문장|출력|검색어)\\s*[:：]\\s*");
    private static final Pattern QUOTES = Pattern.compile("^[\"'“”‘’`]+|[\"'“”‘’`]+$");
    // 빈 출력 대신 이렇게 답하는 경우
    private static final Set<String> EMPTY_WORDS = Set.of("없음", "(없음)", "빈 문자열", "-", "N/A");

    private final AiTextClient aiTextClient;
    private final PolicyRagQueryRewritePrompt prompt;
    private final boolean enabled;
    private final double skipDistance;
    private final Duration timeout;
    private final int maxOutputTokens;
    private final ExecutorService executor;

    @Autowired
    public PolicyRagQueryRewriter(AiTextClient aiTextClient, PolicyRagQueryRewritePrompt prompt,
                                  @Value("${policy.rag.query-rewrite.enabled:true}") boolean enabled,
                                  @Value("${policy.rag.query-rewrite.skip-distance:" + DEFAULT_SKIP_DISTANCE + "}") double skipDistance,
                                  @Value("${policy.rag.query-rewrite.timeout:3s}") Duration timeout,
                                  @Value("${policy.rag.query-rewrite.max-output-tokens:" + DEFAULT_MAX_OUTPUT_TOKENS + "}") int maxOutputTokens) {
        this(aiTextClient, prompt, enabled, skipDistance, timeout, maxOutputTokens, Executors.newVirtualThreadPerTaskExecutor());
    }

    /** 실행기를 지정한다 (테스트용) */
    PolicyRagQueryRewriter(AiTextClient aiTextClient, PolicyRagQueryRewritePrompt prompt, boolean enabled,
                           double skipDistance, Duration timeout, int maxOutputTokens, ExecutorService executor) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("policy.rag.query-rewrite.timeout은 0보다 커야 합니다: " + timeout);
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("policy.rag.query-rewrite.max-output-tokens는 1 이상이어야 합니다: " + maxOutputTokens);
        }
        this.aiTextClient = aiTextClient;
        this.prompt = prompt;
        this.enabled = enabled;
        this.skipDistance = skipDistance;
        this.timeout = timeout;
        this.maxOutputTokens = maxOutputTokens;
        this.executor = executor;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public double skipDistance() {
        return skipDistance;
    }

    @Override
    public QueryRewrite rewrite(String question) {
        long started = System.nanoTime();
        Future<AiTextResult> future;
        try {
            AiTextRequest request = new AiTextRequest(prompt.instructions(), List.of(AiMessage.user(question)),
                    AiModelTier.DEFAULT, maxOutputTokens);
            future = executor.submit(() -> aiTextClient.generate(request));
        } catch (RuntimeException e) {
            log.warn("검색 문장 바꿔 쓰기 실패 prompt={} error={}", prompt.version(), e.getClass().getSimpleName());
            return QueryRewrite.of(QueryRewrite.Outcome.FAILED, elapsedMs(started));
        }
        try {
            AiTextResult result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            String text = clean(result.text());
            long elapsed = elapsedMs(started);
            log.info("검색 문장 바꿔 쓰기 완료 prompt={} model={} empty={} inputTokens={} outputTokens={} elapsedMs={}",
                    prompt.version(), result.model(), text.isEmpty(), result.inputTokens(), result.outputTokens(), elapsed);
            return text.isEmpty() ? QueryRewrite.of(QueryRewrite.Outcome.EMPTY, elapsed) : QueryRewrite.rewritten(text, elapsed);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("검색 문장 바꿔 쓰기 시간 초과 prompt={} timeoutMs={}", prompt.version(), timeout.toMillis());
            return QueryRewrite.of(QueryRewrite.Outcome.TIMEOUT, elapsedMs(started));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return QueryRewrite.of(QueryRewrite.Outcome.FAILED, elapsedMs(started));
        } catch (ExecutionException e) {
            log.warn("검색 문장 바꿔 쓰기 실패 prompt={} error={}", prompt.version(), e.getCause().getClass().getSimpleName());
            return QueryRewrite.of(QueryRewrite.Outcome.FAILED, elapsedMs(started));
        }
    }

    /**
     * 모델 출력에서 검색 문장만 남긴다: 첫 번째 비지 않은 줄, 앞머리("검색 문장:")·따옴표 제거, 최대 {@value #MAX_TEXT_LENGTH}자.
     * "없음"처럼 빈 출력을 뜻하는 답은 빈 문자열이다.
     */
    static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String line = raw.lines().map(String::strip).filter(s -> !s.isEmpty()).findFirst().orElse("");
        line = PREFIX.matcher(line).replaceFirst("");
        line = QUOTES.matcher(line).replaceAll("").strip();
        if (EMPTY_WORDS.contains(line)) {
            return "";
        }
        return line.length() > MAX_TEXT_LENGTH ? line.substring(0, MAX_TEXT_LENGTH).strip() : line;
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
