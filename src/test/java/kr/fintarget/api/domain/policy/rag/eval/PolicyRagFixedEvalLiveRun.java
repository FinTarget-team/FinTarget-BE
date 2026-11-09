package kr.fintarget.api.domain.policy.rag.eval;

import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.policy.rag.PolicyRagPrompt;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalRunner.Result;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.RagCase;
import kr.fintarget.api.domain.policy.search.eval.EvalAiContext;
import kr.fintarget.api.domain.policy.search.eval.SearchEvalEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 답변 평가 2단계 a (#184, 고정 청크): policy-rag-cases.json의 고정 검색 결과로 실제 OpenAI를 호출해 채점한다.
 * 비용이 들므로 OPENAI_API_KEY와 RUN_AI_EVAL=true가 모두 있을 때만 실행된다 (스냅샷은 필요 없음).
 *
 * <p>운영과 같은 클라이언트 빈과 프롬프트, 출력 상한(1000토큰), DEFAULT 모델을 쓴다. 한도는 차감하지 않는다.
 * 결과: build/ai-eval/rag-fixed-{실행 시각}/results.json(모델 원문 답변 포함), report.md.
 *
 * <p>환경 변수: AI_EVAL_PROMPTS(기본 현재 VERSION, 쉼표 구분), AI_EVAL_CASES(문항 id 쉼표 구분, 비우면 전체),
 * AI_EVAL_PARALLELISM(기본 4)
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVAL", matches = "true")
class PolicyRagFixedEvalLiveRun {

    @Test
    void 고정_검색_결과로_답변을_생성하고_리포트를_남긴다() throws Exception {
        PolicyRagEvalSet.Bound bound = PolicyRagEvalSet.load().bind(PolicyQaEvalSet.load());
        List<RagCase> cases = selected(bound.evalSet().cases());
        List<PolicyRagPrompt> prompts = EvalEnv.csv("AI_EVAL_PROMPTS", PolicyRagPrompt.VERSION).stream()
                .map(PolicyRagPrompt::ofVersion).toList();
        int parallelism = Integer.parseInt(EvalEnv.get("AI_EVAL_PARALLELISM", "4"));

        List<Result> results = new ArrayList<>();
        EvalAiContext.run(context -> {
            AiTextClient client = context.getBean(AiTextClient.class);
            for (PolicyRagPrompt prompt : prompts) {
                results.addAll(PolicyRagEvalRunner.run(PolicyRagEvalRunner.prepareFixed(bound, cases), client, prompt,
                        bound::keyOf, parallelism));
            }
        });

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("평가셋", PolicyRagEvalSet.RESOURCE + " (" + cases.size() + "문항)");
        context.put("실행", LocalDateTime.now().withNano(0).toString());
        context.put("프롬프트", prompts.stream().map(PolicyRagPrompt::version).toList().toString());
        Path dir = SearchEvalEnvironment.outputDir("rag-fixed");
        PolicyRagEvalReport.write(dir, "통합 정책 AI 답변 평가 2단계 a (고정 청크)", results, context);
        log.info("답변 평가 2단계 a 완료 {}건, 결과: {}", results.size(), dir.toAbsolutePath());
        assertThat(results).hasSize(cases.size() * prompts.size());
    }

    static List<RagCase> selected(List<RagCase> cases) {
        Set<String> only = Set.copyOf(EvalEnv.csv("AI_EVAL_CASES", ""));
        return cases.stream().filter(c -> only.isEmpty() || only.contains(c.id())).toList();
    }
}
