package kr.fintarget.api.domain.policy.search.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalRunner.ConfigResult;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalRunner.Report;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.CaseType;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.Profile;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.SearchCase;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchScorer.ResultItem;
import kr.fintarget.api.domain.policy.service.PolicyService;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 샘플 스냅샷(3차원)과 메모리 임베딩 캐시로 검색 평가 실행기 전체를 돌린다 (#184). 실호출 없음.
 *
 * <p>서울 25세 후보: 월세(R-1, 청크 [1,0,0]·[0.8,0.6,0]), 전세 대출(KINFA, [0,1,0]). 부산 이사비(R-2)는 지역으로 빠진다.
 */
@DataJpaTest
@Import(PolicyService.class)
class PolicySearchEvalRunnerTest {

    private static final PolicyKey SEOUL_RENT = new PolicyKey("YOUTH_CENTER", "R-1");
    private static final PolicyKey KINFA_LOAN = new PolicyKey("KINFA", "14|청년 전세 대출");

    private static final SearchCase RENT = new SearchCase("benefit-01", CaseType.BENEFIT_SITUATION, "seoul-25", "월세",
            List.of(SEOUL_RENT), null, null);
    // 정답 2건 중 월세(거리 0.4)는 하한 0.3에서 잘린다
    private static final SearchCase LOAN = new SearchCase("loan-01", CaseType.LOAN, "seoul-25", "전세 대출",
            List.of(KINFA_LOAN, SEOUL_RENT), null, null);
    private static final SearchCase UNRELATED = new SearchCase("unrelated-01", CaseType.UNRELATED, "seoul-25", "날씨",
            List.of(), null, null);
    private static final SearchCase UNLABELED = new SearchCase("benefit-02", CaseType.BENEFIT_SITUATION, "seoul-25", "월세",
            List.of(), null, "라벨링 전");
    // 나이·소득이 없으면 후보가 비어 임베딩을 호출하지 않는다 (캐시에 없는 질문이어도 실패하지 않음)
    private static final SearchCase INCOMPLETE = new SearchCase("ambiguous-01", CaseType.AMBIGUOUS, "incomplete", "지원금",
            List.of(), null, null);

    private static final PolicySearchEvalSet EVAL_SET = new PolicySearchEvalSet("테스트", Map.of(),
            Map.of("seoul-25", new Profile("SEOUL", 25, 2_500_000L, null),
                    "incomplete", new Profile("SEOUL", null, null, null)),
            List.of(RENT, LOAN, UNRELATED, UNLABELED, INCOMPLETE));

    private static final QuestionEmbeddingCache CACHE = QuestionEmbeddingCache.inMemory("text-embedding-3-small", Map.of(
            "월세", new float[]{1f, 0f, 0f},
            "전세 대출", new float[]{0f, 1f, 0f},
            "날씨", new float[]{0f, 0f, 1f}));

    private static final SearchConfig K1 = new SearchConfig(1, 0.5, 3);
    private static final SearchConfig K2_TIGHT = new SearchConfig(2, 0.3, 3);
    private static final SearchConfig K2 = new SearchConfig(2, 0.5, 3);

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyService policyService;

    @TempDir
    Path tempDir;

    private PolicySearchEvalRunner runner;

    @BeforeEach
    void setUp() {
        PolicySearchSnapshotLoader.Loaded loaded =
                PolicySearchSnapshotLoader.load(PolicySearchSnapshot.load(PolicySearchSnapshotTest.SAMPLE), policyRepository);
        runner = new PolicySearchEvalRunner(EVAL_SET, loaded, policyService, CACHE.client(), Year.now(BirthYearValidator.ZONE));
    }

    @Test
    void 넓은_검색은_하한_없이_후보_전체를_거리순으로_돌려준다() {
        Report report = runner.run(List.of());

        assertThat(report.wideResults().get("loan-01")).extracting(ResultItem::key).containsExactly(KINFA_LOAN, SEOUL_RENT);
        assertThat(report.wideResults().get("loan-01")).extracting(ResultItem::distance)
                .satisfiesExactly(d -> assertThat(d).isCloseTo(0.0, within(1e-9)), d -> assertThat(d).isCloseTo(0.4, within(1e-6)));
        assertThat(report.wideResults().get("unrelated-01")).hasSize(2);
        assertThat(report.wideResults().get("ambiguous-01")).isEmpty();
        assertThat(report.wideSummary().scoredCases()).isEqualTo(2);
        assertThat(report.wideSummary().unlabeledCases()).isEqualTo(1);
        assertThat(report.wideSummary().recall().get(3)).isEqualTo(1.0);
        assertThat(report.wideSummary().violations()).isZero();
    }

    @Test
    void 설정값마다_Recall과_하한_손실을_계산한다() {
        Report report = runner.run(List.of(K1, K2_TIGHT, K2));
        Map<SearchConfig, ConfigResult> byConfig = report.configs().stream()
                .collect(java.util.stream.Collectors.toMap(ConfigResult::config, r -> r));

        // k=1: 대출 문항은 정답 2건 중 1건만 들어갈 수 있다. 하한 없이도 같으므로 손실 0
        assertThat(byConfig.get(K1).recallAtK()).isEqualTo(0.75);
        assertThat(byConfig.get(K1).loss()).isZero();
        assertThat(byConfig.get(K1).meetsTarget()).isFalse();

        // 하한 0.3: 대출 문항의 월세(거리 0.4)가 잘려 Recall 0.75, 하한이 없으면 1.0 → 손실 0.25
        assertThat(byConfig.get(K2_TIGHT).recallAtK()).isEqualTo(0.75);
        assertThat(byConfig.get(K2_TIGHT).wideRecallAtK()).isEqualTo(1.0);
        assertThat(byConfig.get(K2_TIGHT).loss()).isEqualTo(0.25);

        assertThat(byConfig.get(K2).recallAtK()).isEqualTo(1.0);
        assertThat(byConfig.get(K2).meetsTarget()).isTrue();
        // 무관 문항(거리 1.0)은 하한 0.5에서 0건
        assertThat(byConfig.get(K2).summary().unrelatedEmpty()).isEqualTo(1);
        assertThat(byConfig.get(K2).scoredEmpty()).isZero();
    }

    @Test
    void 리포트_파일을_쓴다() throws Exception {
        Report report = runner.run(List.of(K2_TIGHT, SearchConfig.PRODUCTION));

        PolicySearchEvalReport.write(tempDir, EVAL_SET, report, Map.of("스냅샷", "sample"));

        String md = Files.readString(tempDir.resolve("report.md"), StandardCharsets.UTF_8);
        assertThat(md).contains("## 설정값 비교", "| 2 | 0.30 | 3 | 0.750 | 1.000 | 25.0 |", "| 5 ★ | 0.50 | 3 |",
                "정답(relevant)이 비어 있는 문항 1건", "### loan-01 (LOAN, seoul-25)", "| 2 | 0.4000 | O | 청년 월세 지원 | `YOUTH_CENTER:R-1` |",
                // 표 칸 안의 '|'는 이스케이프한다
                "| 1 | 0.0000 | O | 청년 전세 대출 | `KINFA:14\\|청년 전세 대출` |", "- 정답: `KINFA:14|청년 전세 대출`",
                "- 스냅샷: sample");
        JsonNode json = new ObjectMapper().readTree(tempDir.resolve("results.json").toFile());
        assertThat(json.get("configs")).hasSize(2);
        assertThat(json.get("configs").get(1).get("production").asBoolean()).isTrue();
        assertThat(json.get("cases")).hasSize(5);
        assertThat(json.get("cases").get(1).get("results").get(0).get("key").asText()).isEqualTo("KINFA:14|청년 전세 대출");
    }

    @Test
    void 설정값_조합은_환경_변수가_없으면_기본_격자다() {
        List<SearchConfig> grid = SearchConfig.gridFromEnv();

        assertThat(grid).hasSize(3 * 7 * 3).contains(SearchConfig.PRODUCTION);
        assertThat(grid.getFirst()).isEqualTo(new SearchConfig(3, 0.30, 2));
        assertThat(SearchConfig.PRODUCTION.label()).isEqualTo("k=5 d=0.50 m=3");
    }
}
