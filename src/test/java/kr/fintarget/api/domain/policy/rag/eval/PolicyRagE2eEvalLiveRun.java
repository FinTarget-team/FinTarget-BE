package kr.fintarget.api.domain.policy.rag.eval;

import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.policy.rag.PolicyRagPrompt;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalRunner.Prepared;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalRunner.Result;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.RagCase;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.search.eval.EvalAiContext;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchSnapshot;
import kr.fintarget.api.domain.policy.search.eval.SearchConfig;
import kr.fintarget.api.domain.policy.search.eval.SearchEvalEnvironment;
import kr.fintarget.api.domain.policy.service.PolicyService;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 답변 평가 2단계 b (#184, 검색부터 답변까지): 스냅샷을 H2에 적재하고 policy-rag-e2e-cases.json 문항을
 * 운영과 같은 후보 필터·검색·답변 단계로 돌려 채점한다.
 * 스냅샷 경로(AI_EVAL_SNAPSHOT_DIR), OPENAI_API_KEY, RUN_AI_EVAL=true가 모두 있을 때만 실행된다.
 *
 * <p>질문 임베딩은 검색 평가와 같은 캐시를 쓴다 (없는 질문만 임베딩 API 호출). 한도는 차감하지 않는다.
 * 검색 설정값은 운영 기본값이며 AI_EVAL_RAG_SEARCH="k,하한,배수"(예: 5,0.45,3)로 바꿀 수 있다.
 * 결과: build/ai-eval/rag-e2e-{실행 시각}/results.json, report.md.
 * 그 밖의 환경 변수(AI_EVAL_PROMPTS, AI_EVAL_CASES, AI_EVAL_PARALLELISM)는 {@link PolicyRagFixedEvalLiveRun}과 같다.
 */
@Slf4j
@DataJpaTest
@Import(PolicyService.class)
@EnabledIfEnvironmentVariable(named = PolicySearchSnapshot.ENV_DIR, matches = ".+")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVAL", matches = "true")
class PolicyRagE2eEvalLiveRun {

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyService policyService;

    @Test
    void 스냅샷에서_검색부터_답변까지_실행하고_리포트를_남긴다() throws Exception {
        PolicySearchEvalSet searchEvalSet = PolicySearchEvalSet.load();
        PolicyRagE2eEvalSet evalSet = PolicyRagE2eEvalSet.load().validate(searchEvalSet);
        List<RagCase> cases = PolicyRagFixedEvalLiveRun.selected(evalSet.cases());
        SearchEvalEnvironment env = SearchEvalEnvironment.open(policyRepository,
                cases.stream().map(RagCase::question).toList(), evalSet.sourceKeys());
        SearchConfig config = searchConfig();
        List<PolicyRagPrompt> prompts = EvalEnv.csv("AI_EVAL_PROMPTS", PolicyRagPrompt.VERSION).stream()
                .map(PolicyRagPrompt::ofVersion).toList();
        int parallelism = Integer.parseInt(EvalEnv.get("AI_EVAL_PARALLELISM", "4"));

        // H2 조회·검색은 테스트 트랜잭션 스레드에서 먼저 끝낸다
        List<Prepared> prepared = PolicyRagEvalRunner.prepareEndToEnd(cases, searchEvalSet, env.loaded(), policyService,
                env.cache().client(), config, Year.now(BirthYearValidator.ZONE));
        List<Result> results = new ArrayList<>();
        EvalAiContext.run(context -> {
            AiTextClient client = context.getBean(AiTextClient.class);
            for (PolicyRagPrompt prompt : prompts) {
                results.addAll(PolicyRagEvalRunner.run(prepared, client, prompt,
                        policy -> env.loaded().keyOf(policy).toString(), parallelism));
            }
        });

        Map<String, Object> context = env.context();
        context.put("평가셋", PolicyRagE2eEvalSet.RESOURCE + " (" + cases.size() + "문항)");
        context.put("검색 설정값", config.label() + (config.isProduction() ? " (운영 기본값)" : ""));
        context.put("프롬프트", prompts.stream().map(PolicyRagPrompt::version).toList().toString());
        Path dir = SearchEvalEnvironment.outputDir("rag-e2e");
        PolicyRagEvalReport.write(dir, "통합 정책 AI 답변 평가 2단계 b (검색부터 답변까지)", results, context);
        log.info("답변 평가 2단계 b 완료 {}건, 결과: {}", results.size(), dir.toAbsolutePath());
        assertThat(results).hasSize(cases.size() * prompts.size());
    }

    private static SearchConfig searchConfig() {
        List<String> values = EvalEnv.csv("AI_EVAL_RAG_SEARCH", "");
        if (values.isEmpty()) {
            return SearchConfig.PRODUCTION;
        }
        if (values.size() != 3) {
            throw new IllegalArgumentException("AI_EVAL_RAG_SEARCH는 \"k,하한,배수\" 형식입니다: " + values);
        }
        return new SearchConfig(Integer.parseInt(values.get(0)), Double.parseDouble(values.get(1)),
                Integer.parseInt(values.get(2)));
    }
}
