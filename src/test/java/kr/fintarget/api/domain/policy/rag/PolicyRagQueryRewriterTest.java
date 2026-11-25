package kr.fintarget.api.domain.policy.rag;

import kr.fintarget.api.domain.ai.client.AiCallFailedException;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiModelTier;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
import kr.fintarget.api.domain.policy.search.QueryRewrite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyRagQueryRewriterTest {

    private static final String QUESTION = "교통비 아끼는 방법 알려줘 버스 많이타";

    private final AiTextClient aiTextClient = mock(AiTextClient.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private PolicyRagQueryRewriter rewriter(Duration timeout) {
        return new PolicyRagQueryRewriter(aiTextClient, new PolicyRagQueryRewritePrompt(), true, 0.45, timeout, 300, executor);
    }

    private static AiTextResult result(String text) {
        return new AiTextResult(text, "gpt-6-luna", 900, 12, 0);
    }

    @Test
    void 바꾼_검색_문장을_돌려주고_기본_모델에_원래_질문을_사용자_메시지로_보낸다() {
        when(aiTextClient.generate(any())).thenReturn(result("청년 대중교통비 할인 환급 지원\n"));

        QueryRewrite rewrite = rewriter(Duration.ofSeconds(3)).rewrite(QUESTION);

        assertThat(rewrite.outcome()).isEqualTo(QueryRewrite.Outcome.REWRITTEN);
        assertThat(rewrite.text()).isEqualTo("청년 대중교통비 할인 환급 지원");
        ArgumentCaptor<AiTextRequest> captor = ArgumentCaptor.forClass(AiTextRequest.class);
        verify(aiTextClient).generate(captor.capture());
        AiTextRequest request = captor.getValue();
        assertThat(request.tier()).isEqualTo(AiModelTier.DEFAULT);
        assertThat(request.maxOutputTokens()).isEqualTo(300);
        assertThat(request.messages()).singleElement().satisfies(m -> assertThat(m.content()).isEqualTo(QUESTION));
        assertThat(request.systemPrompt()).contains("복비 → 중개보수", "질문에 없는 조건은 넣지 않습니다");
    }

    @Test
    void 빈_출력이나_없음은_EMPTY다() {
        when(aiTextClient.generate(any())).thenReturn(result("  \n"));
        assertThat(rewriter(Duration.ofSeconds(3)).rewrite("오늘 점심 뭐먹지").outcome()).isEqualTo(QueryRewrite.Outcome.EMPTY);

        when(aiTextClient.generate(any())).thenReturn(result("없음"));
        QueryRewrite rewrite = rewriter(Duration.ofSeconds(3)).rewrite("오늘 점심 뭐먹지");
        assertThat(rewrite.outcome()).isEqualTo(QueryRewrite.Outcome.EMPTY);
        assertThat(rewrite.text()).isNull();
    }

    @Test
    void 호출이_실패하면_예외_없이_FAILED다() {
        when(aiTextClient.generate(any())).thenThrow(new AiCallFailedException(AiClientException.Reason.INCOMPLETE));
        assertThat(rewriter(Duration.ofSeconds(3)).rewrite(QUESTION).outcome()).isEqualTo(QueryRewrite.Outcome.FAILED);

        doThrow(new AiUnavailableException(AiClientException.Reason.DISABLED)).when(aiTextClient).generate(any());
        assertThat(rewriter(Duration.ofSeconds(3)).rewrite(QUESTION).outcome()).isEqualTo(QueryRewrite.Outcome.FAILED);
    }

    @Test
    void 시간을_넘기면_기다리지_않고_TIMEOUT이다() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(aiTextClient.generate(any())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return result("늦은 답");
        });

        long started = System.nanoTime();
        QueryRewrite rewrite = rewriter(Duration.ofMillis(100)).rewrite(QUESTION);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        release.countDown();

        assertThat(rewrite.outcome()).isEqualTo(QueryRewrite.Outcome.TIMEOUT);
        assertThat(rewrite.text()).isNull();
        assertThat(elapsedMs).isLessThan(2_000);
    }

    @Test
    void 출력에서_검색_문장만_남긴다() {
        assertThat(PolicyRagQueryRewriter.clean("검색 문장: \"청년 주택 임차 중개보수 지원\"")).isEqualTo("청년 주택 임차 중개보수 지원");
        assertThat(PolicyRagQueryRewriter.clean("출력: 학생식당 아침 식비 지원\n설명: ...")).isEqualTo("학생식당 아침 식비 지원");
        assertThat(PolicyRagQueryRewriter.clean("\n\n  K-패스 대중교통비 환급  ")).isEqualTo("K-패스 대중교통비 환급");
        assertThat(PolicyRagQueryRewriter.clean(null)).isEmpty();
        assertThat(PolicyRagQueryRewriter.clean("(없음)")).isEmpty();
        assertThat(PolicyRagQueryRewriter.clean("가".repeat(200))).hasSize(PolicyRagQueryRewriter.MAX_TEXT_LENGTH);
    }

    @Test
    void 설정값을_노출하고_잘못된_값은_기동_시_막는다() {
        PolicyRagQueryRewriter off = new PolicyRagQueryRewriter(aiTextClient, new PolicyRagQueryRewritePrompt(), false, 0.4,
                Duration.ofSeconds(3), 300, executor);
        assertThat(off.isEnabled()).isFalse();
        assertThat(off.skipDistance()).isEqualTo(0.4);

        assertThatThrownBy(() -> new PolicyRagQueryRewriter(aiTextClient, new PolicyRagQueryRewritePrompt(), true, 0.45,
                Duration.ZERO, 300, executor)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PolicyRagQueryRewriter(aiTextClient, new PolicyRagQueryRewritePrompt(), true, 0.45,
                Duration.ofSeconds(3), 0, executor)).isInstanceOf(IllegalArgumentException.class);
    }
}
