package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.SearchCase;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스냅샷으로 검색 설정값(k·하한·배수) 조합을 비교한다 (#184 1단계). 스냅샷 경로(AI_EVAL_SNAPSHOT_DIR)가 있을 때만 실행된다.
 *
 * <p>질문 임베딩은 캐시({@link QuestionEmbeddingCache})에서 읽는다. 캐시에 없는 질문이 있으면 OPENAI_API_KEY와 RUN_AI_EVAL=true일 때만
 * 임베딩 API를 호출해 채운다 (질문 35개 × 1회). 그 뒤로는 실호출 없이 다시 돌릴 수 있다.
 *
 * <p>결과: build/ai-eval/search-{실행 시각}/results.json, report.md.
 * 조합은 AI_EVAL_SEARCH_K, AI_EVAL_SEARCH_MAX_DISTANCE, AI_EVAL_SEARCH_MULTIPLIER(쉼표 구분)로 바꿀 수 있다.
 */
@Slf4j
@DataJpaTest
@Import(PolicyService.class)
@EnabledIfEnvironmentVariable(named = PolicySearchSnapshot.ENV_DIR, matches = ".+")
class PolicySearchEvalLiveRun {

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyService policyService;

    @Test
    void 설정값_조합별로_검색을_평가하고_리포트를_남긴다() throws Exception {
        PolicySearchEvalSet evalSet = PolicySearchEvalSet.load();
        SearchEvalEnvironment env = SearchEvalEnvironment.open(policyRepository,
                evalSet.cases().stream().map(SearchCase::question).toList(),
                evalSet.cases().stream().flatMap(c -> c.relevant().stream()).map(PolicyKey::toString).toList());

        PolicySearchEvalRunner runner = new PolicySearchEvalRunner(evalSet, env.loaded(), policyService,
                env.cache().client(), Year.now(BirthYearValidator.ZONE));
        List<SearchConfig> configs = SearchConfig.fromEnv();
        PolicySearchEvalRunner.Report report = runner.run(configs);

        Path dir = SearchEvalEnvironment.outputDir("search");
        PolicySearchEvalReport.write(dir, evalSet, report, env.context());
        log.info("검색 평가 완료: 문항 {}건, 설정 {}개, 결과: {}", evalSet.cases().size(), configs.size(), dir.toAbsolutePath());
        assertThat(report.configs()).hasSize(configs.size());
    }
}
