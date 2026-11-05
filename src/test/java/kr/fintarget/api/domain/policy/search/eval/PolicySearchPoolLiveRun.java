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
 * 스냅샷으로 정답 라벨링용 풀을 만든다 (#184 1단계). 스냅샷 경로(AI_EVAL_SNAPSHOT_DIR)가 있을 때만 실행된다.
 * 질문 임베딩 캐시 규칙은 {@link PolicySearchEvalLiveRun}과 같다.
 *
 * <p>결과: build/ai-eval/pool-{실행 시각}/pool.md(라벨링할 때 보는 파일), pool.json.
 */
@Slf4j
@DataJpaTest
@Import(PolicyService.class)
@EnabledIfEnvironmentVariable(named = PolicySearchSnapshot.ENV_DIR, matches = ".+")
class PolicySearchPoolLiveRun {

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyService policyService;

    @Test
    void 문항별_라벨링_풀을_만든다() throws Exception {
        PolicySearchEvalSet evalSet = PolicySearchEvalSet.load();
        SearchEvalEnvironment env = SearchEvalEnvironment.open(policyRepository,
                evalSet.cases().stream().map(SearchCase::question).toList(),
                evalSet.cases().stream().flatMap(c -> c.relevant().stream()).map(PolicyKey::toString).toList());

        PolicySearchEvalRunner runner = new PolicySearchEvalRunner(evalSet, env.loaded(), policyService,
                env.cache().client(), Year.now(BirthYearValidator.ZONE));
        List<PolicySearchPool.CasePool> pools = new PolicySearchPool(runner, env.snapshot())
                .build(PolicySearchPool.VECTOR_TOP, PolicySearchPool.KEYWORD_TOP);

        Path dir = SearchEvalEnvironment.outputDir("pool");
        PolicySearchPoolReport.write(dir, pools, env.context());
        log.info("라벨링 풀 완료: 문항 {}건, 결과: {}", pools.size(), dir.resolve("pool.md").toAbsolutePath());
        assertThat(pools).isNotEmpty();
    }
}
