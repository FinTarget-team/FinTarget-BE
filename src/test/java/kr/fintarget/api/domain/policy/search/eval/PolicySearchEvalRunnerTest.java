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
            List.of(SEOUL_RENT), null, true, null);
    // 정답 2건 중 월세(거리 0.4)는 하한 0.3에서 잘린다
    private static final SearchCase LOAN = new SearchCase("loan-01", CaseType.LOAN, "seoul-25", "전세 대출",
            List.of(KINFA_LOAN, SEOUL_RENT), null, true, null);
    private static final SearchCase UNRELATED = new SearchCase("unrelated-01", CaseType.UNRELATED, "seoul-25", "날씨",
            List.of(), null, false, null);
    private static final SearchCase UNLABELED = new SearchCase("benefit-02", CaseType.BENEFIT_SITUATION, "seoul-25", "월세",
            List.of(), null, false, "라벨링 전");
    // 나이·소득이 없으면 후보가 비어 임베딩을 호출하지 않는다 (캐시에 없는 질문이어도 실패하지 않음)
    private static final SearchCase INCOMPLETE = new SearchCase("ambiguous-01", CaseType.AMBIGUOUS, "incomplete", "지원금",
            List.of(), null, false, null);

    private static final PolicySearchEvalSet EVAL_SET = new PolicySearchEvalSet("테스트", Map.of(),
            Map.of("seoul-25", new Profile("SEOUL", 25, 2_500_000L, null),
                    "incomplete", new Profile("SEOUL", null, null, null)),
            List.of(RENT, LOAN, UNRELATED, UNLABELED, INCOMPLETE));

    private static final QuestionEmbeddingCache CACHE = QuestionEmbeddingCache.inMemory("text-embedding-3-small", Map.of(
            "월세", new float[]{1f, 0f, 0f},
            "전세 대출", new float[]{0f, 1f, 0f},
            "날씨", new float[]{0f, 0f, 1f},
            // 대출 의도 단어가 없는데 벡터는 KINFA 대출 쪽 (KINFA 제외 시점 판별용)
            "보증금 지원", new float[]{0f, 1f, 0f}));

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
    private PolicySearchSnapshotLoader.Loaded loaded;

    @BeforeEach
    void setUp() {
        loaded = PolicySearchSnapshotLoader.load(PolicySearchSnapshot.load(PolicySearchSnapshotTest.SAMPLE), policyRepository);
        runner = new PolicySearchEvalRunner(EVAL_SET, loaded, policyService, CACHE.client(), Year.now(BirthYearValidator.ZONE));
    }

    /** 적재한 스냅샷을 그대로 쓰면서 문항만 바꾼 러너 (스냅샷을 두 번 적재하면 keyOf가 깨진다) */
    private PolicySearchEvalRunner runnerFor(SearchCase... cases) {
        PolicySearchEvalSet evalSet = new PolicySearchEvalSet("테스트", Map.of(),
                Map.of("seoul-25", new Profile("SEOUL", 25, 2_500_000L, null)), List.of(cases));
        return new PolicySearchEvalRunner(evalSet, loaded, policyService, CACHE.client(),
                Year.now(BirthYearValidator.ZONE));
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

    /**
     * KINFA 제외는 정책별 묶기·상위 k 선정 "전"(후보 단계)에 일어나야 한다 (#184 1-1).
     *
     * <p>판별 방법: 질문 "보증금 지원"은 KINFA 대출 쪽 벡터([0,1,0])지만 대출 의도 단어가 없다.
     * k=1에서 KINFA가 1위이므로, 결과에서 KINFA만 지우는 방식이면 0건이 된다.
     * 후보에서 빼는 방식이면 빈자리를 월세가 채워 1건이 나온다.
     */
    @Test
    void KINFA_조건부_제외는_후보_단계에서_일어나_빈자리를_다른_정책이_채운다() {
        SearchCase deposit = new SearchCase("benefit-03", CaseType.BENEFIT_SITUATION, "seoul-25",
                "보증금 지원", List.of(SEOUL_RENT), null, true, "대출 의도 없는데 KINFA가 1위인 질문");
        PolicySearchEvalRunner only = runnerFor(deposit);

        assertThat(LoanIntent.of(deposit.question())).isFalse();
        // 제외하지 않으면 KINFA가 1위
        assertThat(only.search(deposit, new SearchConfig(1, 1.5, 3)))
                .extracting(hit -> loaded.keyOf(hit.policy())).containsExactly(KINFA_LOAN);
        // 제외하면 0건이 아니라 월세 1건 (= 선정 전에 빠졌다는 뜻)
        assertThat(only.search(deposit, new SearchConfig(1, 1.5, 3, true)))
                .extracting(hit -> loaded.keyOf(hit.policy())).containsExactly(SEOUL_RENT);
    }

    @Test
    void 대출_의도가_있는_질문은_KINFA를_빼지_않는다() {
        // 질문에 "대출"이 있으면 조건부 조합에서도 KINFA가 남는다
        assertThat(LoanIntent.of(LOAN.question())).isTrue();

        assertThat(runner.search(LOAN, new SearchConfig(1, 1.5, 3, true)))
                .extracting(hit -> loaded.keyOf(hit.policy())).containsExactly(KINFA_LOAN);
    }

    @Test
    void 리포트_파일을_쓴다() throws Exception {
        Report report = runner.run(List.of(K2_TIGHT, SearchConfig.PRODUCTION));

        PolicySearchEvalReport.write(tempDir, EVAL_SET, report, Map.of("스냅샷", "sample"));

        String md = Files.readString(tempDir.resolve("report.md"), StandardCharsets.UTF_8);
        assertThat(md).contains("## 설정값 비교", "| 2 | 0.30 | 3 | - | 0.750 | 1.000 | 25.0 |", "| 5 ★ | 0.50 | 3 | - |",
                // #184 조합 비교 표와 대출 의도 판정
                "## 조합 비교 (#184)", "| k=5 d=0.50 m=3 ★ | 1.000 |", "### 대출 의도 판정",
                "아직 라벨링하지 않은 문항(labeled=false) 1건", "### loan-01 (LOAN, seoul-25)", "| 2 | 0.4000 | O | 청년 월세 지원 | `YOUTH_CENTER:R-1` |",
                // 표 칸 안의 '|'는 이스케이프한다
                "| 1 | 0.0000 | O | 청년 전세 대출 | `KINFA:14\\|청년 전세 대출` |", "- 정답: `KINFA:14|청년 전세 대출`",
                "- 스냅샷: sample");
        JsonNode json = new ObjectMapper().readTree(tempDir.resolve("results.json").toFile());
        assertThat(json.get("configs")).hasSize(2);
        assertThat(json.get("configs").get(1).get("production").asBoolean()).isTrue();
        assertThat(json.get("cases")).hasSize(5);
        assertThat(json.get("cases").get(1).get("results").get(0).get("key").asText()).isEqualTo("KINFA:14|청년 전세 대출");
    }

    // 채점 대상 문항이 없으면 리포트는 "-", results.json은 null로 적는다
    @Test
    void 채점_대상이_없는_지표는_대시와_null로_적는다() throws Exception {
        SearchCase unlabeledOnly = new SearchCase("benefit-09", CaseType.BENEFIT_SITUATION, "seoul-25", "월세",
                List.of(), null, false, "라벨링 전");
        PolicySearchEvalRunner only = runnerFor(unlabeledOnly);
        Report report = only.run(List.of(SearchConfig.PRODUCTION));

        ConfigResult result = report.configs().getFirst();
        assertThat(result.recallAtK()).isNull();
        assertThat(result.wideRecallAtK()).isNull();
        assertThat(result.loss()).isNull();
        assertThat(result.meetsTarget()).isFalse();

        PolicySearchEvalReport.write(tempDir, only.evalSet(), report, Map.of("스냅샷", "sample"));

        String md = Files.readString(tempDir.resolve("report.md"), StandardCharsets.UTF_8);
        // 0.000이 아니라 "-"
        assertThat(md).contains("| k=5 d=0.50 m=3 ★ | - |").doesNotContain("| 0.000 |");

        JsonNode json = new ObjectMapper().readTree(tempDir.resolve("results.json").toFile());
        JsonNode config = json.get("configs").get(0);
        assertThat(config.get("recallAtK").isNull()).isTrue();
        assertThat(config.get("wideRecallAtK").isNull()).isTrue();
        assertThat(config.get("loss").isNull()).isTrue();
        assertThat(config.get("mrr").isNull()).isTrue();
    }

    @Test
    void 설정값_조합은_환경_변수가_없으면_기본_격자다() {
        List<SearchConfig> grid = SearchConfig.gridFromEnv();

        assertThat(grid).hasSize(3 * 7 * 3).contains(SearchConfig.PRODUCTION);
        assertThat(grid.getFirst()).isEqualTo(new SearchConfig(3, 0.30, 2));
        assertThat(SearchConfig.PRODUCTION.label()).isEqualTo("k=5 d=0.50 m=3");
    }
}
