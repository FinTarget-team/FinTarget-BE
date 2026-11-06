package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.CaseType;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.Profile;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.SearchCase;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchPool.CasePool;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchPool.Item;
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

/**
 * 라벨링 풀: 벡터 상위와 키워드 상위를 프로필 후보 안에서 합친다 (#184). 샘플 스냅샷과 메모리 임베딩 캐시만 쓴다.
 *
 * <p>서울 25세 후보: 월세(R-1), 전세 대출(KINFA). 부산 이사비(R-2)는 후보가 아니다.
 */
@DataJpaTest
@Import(PolicyService.class)
class PolicySearchPoolTest {

    private static final PolicyKey SEOUL_RENT = new PolicyKey("YOUTH_CENTER", "R-1");
    private static final PolicyKey KINFA_LOAN = new PolicyKey("KINFA", "14|청년 전세 대출");
    private static final PolicyKey BUSAN_MOVING = new PolicyKey("YOUTH_CENTER", "R-2");

    // 질문 벡터는 월세 쪽([1,0,0])인데 낱말은 "전세 대출" → 벡터 1위는 월세, 키워드 1위는 전세 대출
    private static final SearchCase MIXED = new SearchCase("loan-01", CaseType.LOAN, "seoul-25", "전세 대출이 필요해요",
            List.of(KINFA_LOAN, BUSAN_MOVING), null, true, "부산 정책은 일부러 잘못 넣은 라벨");
    private static final SearchCase BUSAN_WORDS = new SearchCase("region-01", CaseType.REGION, "seoul-25", "부산 이사비",
            List.of(), null, false, null);
    private static final SearchCase UNRELATED = new SearchCase("unrelated-01", CaseType.UNRELATED, "seoul-25", "날씨",
            List.of(), null, false, null);

    private static final PolicySearchEvalSet EVAL_SET = new PolicySearchEvalSet("테스트", Map.of(),
            Map.of("seoul-25", new Profile("SEOUL", 25, 2_500_000L, null)), List.of(MIXED, BUSAN_WORDS, UNRELATED));

    private static final QuestionEmbeddingCache CACHE = QuestionEmbeddingCache.inMemory("text-embedding-3-small", Map.of(
            "전세 대출이 필요해요", new float[]{1f, 0f, 0f},
            "부산 이사비", new float[]{1f, 0f, 0f},
            "날씨", new float[]{0f, 0f, 1f}));

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyService policyService;

    @TempDir
    Path tempDir;

    private PolicySearchPool pool;

    @BeforeEach
    void setUp() {
        PolicySearchSnapshot snapshot = PolicySearchSnapshot.load(PolicySearchSnapshotTest.SAMPLE);
        PolicySearchSnapshotLoader.Loaded loaded = PolicySearchSnapshotLoader.load(snapshot, policyRepository);
        PolicySearchEvalRunner runner = new PolicySearchEvalRunner(EVAL_SET, loaded, policyService, CACHE.client(),
                Year.now(BirthYearValidator.ZONE));
        pool = new PolicySearchPool(runner, snapshot);
    }

    @Test
    void 벡터_상위와_키워드_상위를_합치고_순위를_함께_적는다() {
        List<CasePool> pools = pool.build(1, 1);

        // 무관 문항은 정답을 두지 않아 풀을 만들지 않는다
        assertThat(pools).extracting(p -> p.evalCase().id()).containsExactly("loan-01", "region-01");
        CasePool mixed = pools.getFirst();
        assertThat(mixed.candidateCount()).isEqualTo(2);
        assertThat(mixed.tokens()).containsExactly("전세", "대출", "필요해");
        assertThat(mixed.items()).extracting(Item::key).containsExactly(SEOUL_RENT, KINFA_LOAN);

        Item rent = mixed.items().get(0);
        assertThat(rent.vectorRank()).isEqualTo(1);
        assertThat(rent.distance()).isZero();
        assertThat(rent.keywordRank()).isNull();
        assertThat(rent.snippet()).isEqualTo("[지원 내용] / 월 20만원");
        assertThat(rent.labeled()).isFalse();

        Item loan = mixed.items().get(1);
        assertThat(loan.vectorRank()).isNull();
        assertThat(loan.keywordRank()).isEqualTo(1);
        assertThat(loan.matchedTokens()).containsExactly("전세", "대출");
        assertThat(loan.labeled()).isTrue();
        assertThat(loan.eligible()).isTrue();
    }

    @Test
    void 후보가_아닌_정책은_키워드가_맞아도_풀에_넣지_않고_그런_라벨은_따로_표시한다() {
        List<CasePool> pools = pool.build(1, 5);

        CasePool busan = pools.get(1);
        assertThat(busan.items()).extracting(Item::key).doesNotContain(BUSAN_MOVING);
        assertThat(pools.getFirst().labeledNotCandidate()).containsExactly(BUSAN_MOVING);
        assertThat(pools.getFirst().labeledOutsidePool()).isEmpty();
    }

    @Test
    void 낱말은_조사를_떼고_두_글자_이상만_쓴다() {
        assertThat(PolicySearchPool.tokens("부산에서 월세를 지원받을 수 있나요?")).containsExactly("부산", "월세", "지원받", "있나");
        assertThat(PolicySearchPool.tokens("청년 청년")).containsExactly("청년");
    }

    @Test
    void 라벨링_파일을_쓴다() throws Exception {
        PolicySearchPoolReport.write(tempDir, pool.build(20, 10), Map.of("스냅샷", "sample"));

        String md = Files.readString(tempDir.resolve("pool.md"), StandardCharsets.UTF_8);
        assertThat(md).contains("## 라벨링 방법", "## loan-01 (LOAN, seoul-25)", "- 현재 정답: `KINFA:14|청년 전세 대출`, `YOUTH_CENTER:R-2`",
                "정답인데 이 프로필의 후보가 아님",
                "1. `YOUTH_CENTER:R-1` 청년 월세 지원 (SEOUL)\n   - 벡터 1위 (거리 0.0000) · 키워드 -\n   - 원문: [지원 내용] / 월 20만원\n",
                "2. **[현재 정답]** `KINFA:14|청년 전세 대출` 청년 전세 대출 (전국)\n   - 벡터 2위 (거리 1.0000) · 키워드 1위 (전세, 대출)\n");
        assertThat(Files.exists(tempDir.resolve("pool.json"))).isTrue();
    }

    // 풀에는 거리 상한이 없다. 실제 제약은 벡터 상위 몇 건까지 담는지다 (#184 1-2)
    @Test
    void 풀은_거리_상한_없이_벡터_상위_건수로만_끊는다() {
        assertThat(SearchConfig.WIDE.maxDistance()).isEqualTo(2.0);  // 코사인 거리 최대값 = 자르지 않음

        // 벡터 1건만 담으면 거리가 먼 후보는 풀에 없다
        CasePool narrow = pool.build(1, 0).stream().filter(c -> c.evalCase().id().equals("loan-01")).findFirst().orElseThrow();
        assertThat(narrow.items()).hasSize(1);
        // 넓히면 더 담긴다
        CasePool wide = pool.build(20, 0).stream().filter(c -> c.evalCase().id().equals("loan-01")).findFirst().orElseThrow();
        assertThat(wide.items()).hasSizeGreaterThan(narrow.items().size());
    }

    @Test
    void 풀_크기는_환경_변수가_없으면_기본값이다() {
        assertThat(System.getenv(PolicySearchPool.ENV_VECTOR_TOP)).isNull();

        List<CasePool> pools = pool.buildFromEnv();

        assertThat(pools).isNotEmpty();
        assertThat(PolicySearchPool.VECTOR_TOP).isEqualTo(20);
        assertThat(PolicySearchPool.KEYWORD_TOP).isEqualTo(10);
    }
}
