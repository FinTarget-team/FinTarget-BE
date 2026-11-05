package kr.fintarget.api.domain.policy.rag.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.policy.rag.PolicyRagPrompt;
import kr.fintarget.api.domain.policy.rag.PolicyRagStatus;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalRunner.Result;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.Bound;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.RagCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 답변 평가 실행기 2단계 a를 가짜 AI 클라이언트로 돌린다 (#184). 실호출 없음.
 */
@ExtendWith(OutputCaptureExtension.class)
class PolicyRagEvalRunnerTest {

    private static final Bound BOUND = PolicyRagEvalSet.load().bind(PolicyQaEvalSet.load());
    private static final String MISO = "single-02-miso-rate";
    private static final String OFF_TOPIC = "none-03-off-topic";
    private static final String NOT_IN_SOURCE = "notin-01-deposit-loan-term";
    private static final String MISO_ANSWER = "미소금융 창업초기 시설자금 금리는 4.5%입니다 [2]";
    private static final String NOT_IN_SOURCE_ANSWER = "머물자리론 대출 기간은 찾은 원문에서 확인할 수 없습니다 [1][5]";

    @TempDir
    Path tempDir;

    private static RagCase caseOf(String id) {
        return BOUND.evalSet().cases().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    // 질문(사용자 메시지)별로 정해 둔 답을 돌려준다
    private static AiTextClient fakeAi(AtomicInteger calls) {
        Map<String, String> answers = Map.of(
                caseOf(MISO).question(), MISO_ANSWER,
                caseOf(OFF_TOPIC).question(), "찾은 정책 원문에서는 답할 수 없는 질문입니다.",
                caseOf(NOT_IN_SOURCE).question(), NOT_IN_SOURCE_ANSWER);
        return request -> {
            calls.incrementAndGet();
            String question = request.messages().getLast().content();
            return new AiTextResult(answers.get(question), "fake-model", 1200, 80, 10);
        };
    }

    @Test
    void 고정_검색_결과로_답변을_받아_원문_답변과_함께_채점한다(CapturedOutput output) throws Exception {
        List<RagCase> cases = List.of(caseOf(MISO), caseOf(OFF_TOPIC), caseOf(NOT_IN_SOURCE));
        AtomicInteger calls = new AtomicInteger();

        List<Result> results = PolicyRagEvalRunner.run(PolicyRagEvalRunner.prepareFixed(BOUND, cases), fakeAi(calls),
                new PolicyRagPrompt(), BOUND::keyOf, 3);

        assertThat(calls).hasValue(3);
        assertThat(results).extracting(r -> r.evalCase().id()).containsExactly(MISO, OFF_TOPIC, NOT_IN_SOURCE);
        assertThat(results).allSatisfy(r -> assertThat(r.score().failures()).isEmpty());

        Result miso = results.get(0);
        assertThat(miso.status()).isEqualTo(PolicyRagStatus.ANSWERED);
        assertThat(miso.sources()).containsExactly("kinfa-miso-startup");
        assertThat(miso.rawAnswer()).isEqualTo(MISO_ANSWER);
        assertThat(miso.answer()).doesNotContain("[2]").contains("4.5%");
        assertThat(miso.hitKeys()).containsExactly("kinfa-restart-guarantee", "kinfa-miso-startup", "kinfa-seoul-startup");
        assertThat(miso.model()).isEqualTo("fake-model");
        assertThat(miso.outputTokens()).isEqualTo(80);

        // 관련 없음: 근거 번호가 없어 서버가 NO_EVIDENCE로 바꾼다 (기대 동작)
        assertThat(results.get(1).status()).isEqualTo(PolicyRagStatus.NO_EVIDENCE);
        assertThat(results.get(1).score().citationMissing()).isTrue();
        // 검색 결과 2건인데 [5]를 인용 → 무효 번호 1
        assertThat(results.get(2).score().invalidCitations()).isEqualTo(1);

        // 운영 로그에는 질문·답변 본문이 남지 않는다 (원문은 결과 파일에만)
        for (RagCase c : cases) {
            assertThat(output.getAll()).doesNotContain(c.question());
        }
        assertThat(output.getAll()).doesNotContain(MISO_ANSWER, "머물자리론 대출 기간은");
    }

    @Test
    void 호출이_실패한_문항은_실패로_기록하고_나머지는_계속한다() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AiTextClient ok = fakeAi(calls);
        AiTextClient flaky = request -> {
            if (request.messages().getLast().content().equals(caseOf(MISO).question())) {
                throw AiClientException.of(AiClientException.Reason.TIMEOUT);
            }
            return ok.generate(request);
        };

        List<Result> results = PolicyRagEvalRunner.run(
                PolicyRagEvalRunner.prepareFixed(BOUND, List.of(caseOf(MISO), caseOf(OFF_TOPIC))), flaky,
                new PolicyRagPrompt(), BOUND::keyOf, 1);

        assertThat(results.get(0).status()).isNull();
        assertThat(results.get(0).error()).contains("TIMEOUT");
        assertThat(results.get(0).score().failures()).containsExactly("호출 실패: AiCallFailedException(TIMEOUT)");
        assertThat(results.get(1).score().passed()).isTrue();
    }

    @Test
    void 리포트에_자동_채점과_원문_답변과_사람_검수_항목을_쓴다() throws Exception {
        List<Result> results = PolicyRagEvalRunner.run(
                PolicyRagEvalRunner.prepareFixed(BOUND, List.of(caseOf(MISO), caseOf(OFF_TOPIC))),
                fakeAi(new AtomicInteger()), new PolicyRagPrompt(), BOUND::keyOf, 2);

        PolicyRagEvalReport.write(tempDir, "답변 평가 테스트", results, Map.of("실행", "test"));

        String md = Files.readString(tempDir.resolve("report.md"), StandardCharsets.UTF_8);
        assertThat(md).contains("# 답변 평가 테스트", "| policy-rag-v1 | 2/2 | 2/2 | 1/1 | 0 | 1/1 | 0 | 0 | 0 | 0 |",
                "### single-02-miso-rate (SINGLE_MATCH)", "[2] 미소금융 창업자금_창업초기 시설자금 (`kinfa-miso-startup`)",
                "원문 답변:\n\n> " + MISO_ANSWER, "- [ ] 사람 검수:", "| SINGLE_MATCH | 1/1 |");
        JsonNode json = new ObjectMapper().readTree(tempDir.resolve("results.json").toFile());
        assertThat(json.get("results").get(0).get("rawAnswer").asText()).isEqualTo(MISO_ANSWER);
        assertThat(json.get("results").get(0).get("sources").get(0).asText()).isEqualTo("kinfa-miso-startup");
    }

    @Test
    void 문항_선택_환경_변수가_없으면_전체다() {
        assertThat(PolicyRagFixedEvalLiveRun.selected(BOUND.evalSet().cases())).hasSize(20);
        assertThat(Set.copyOf(EvalEnv.csv("AI_EVAL_NOT_SET", "a, b,,c"))).containsExactlyInAnyOrder("a", "b", "c");
    }
}
