package kr.fintarget.api.domain.policy.rag.eval;

import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.policy.rag.PolicyRagPrompt;
import kr.fintarget.api.domain.policy.rag.PolicyRagStatus;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalRunner.Prepared;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalRunner.Result;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.Category;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.RagCase;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.Profile;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchSnapshot;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchSnapshotLoader;
import kr.fintarget.api.domain.policy.search.eval.QuestionEmbeddingCache;
import kr.fintarget.api.domain.policy.search.eval.SearchConfig;
import kr.fintarget.api.domain.policy.service.PolicyService;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static kr.fintarget.api.domain.policy.rag.PolicyRagStatus.ANSWERED;
import static kr.fintarget.api.domain.policy.rag.PolicyRagStatus.NO_EVIDENCE;
import static kr.fintarget.api.domain.policy.rag.PolicyRagStatus.PROFILE_INCOMPLETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 답변 평가 2단계 b(검색부터 답변까지)를 샘플 스냅샷과 가짜 AI로 돌린다 (#184). 실호출 없음.
 *
 * <p>서울 25세 후보: 월세(R-1, 거리 0 / 0.4), 전세 대출(KINFA). 운영 설정(k=5, 하한 0.5)으로 검색한다.
 */
@DataJpaTest
@Import(PolicyService.class)
class PolicyRagE2eEvalTest {

    private static final String RENT = "YOUTH_CENTER:R-1";
    private static final String KINFA = "KINFA:14|청년 전세 대출";
    private static final String BUSAN = "YOUTH_CENTER:R-2";

    private static final PolicySearchEvalSet SEARCH_SET = new PolicySearchEvalSet("테스트", Map.of(),
            Map.of("seoul-25", new Profile("SEOUL", 25, 2_500_000L, null),
                    "incomplete", new Profile("SEOUL", null, null, null)), List.of());

    private static final QuestionEmbeddingCache CACHE = QuestionEmbeddingCache.inMemory("text-embedding-3-small", Map.of(
            "월세", new float[]{1f, 0f, 0f},
            "전세 대출", new float[]{0f, 1f, 0f},
            "날씨", new float[]{0f, 0f, 1f}));

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyService policyService;

    private static RagCase ragCase(String id, Category category, String profile, String question,
                                   List<PolicyRagStatus> status, List<String> expectedSources, List<List<String>> facts) {
        return new RagCase(id, category, profile, question, List.of(), status, expectedSources, List.of(), facts, List.of(),
                false, false, false, null);
    }

    @Test
    void 운영_순서대로_조건_부족_검색_결과_없음_답변을_구분한다() throws Exception {
        List<RagCase> cases = List.of(
                ragCase("e-rent", Category.SINGLE_MATCH, "seoul-25", "월세", List.of(ANSWERED), List.of(RENT), List.of(List.of("20만원"))),
                // 부산 정책은 서울 후보가 아니라 검색 결과에 없다 → 검색 누락으로 따로 표시
                ragCase("e-loan", Category.MULTI_MATCH, "seoul-25", "전세 대출", List.of(ANSWERED), List.of(KINFA, BUSAN), List.of()),
                ragCase("e-none", Category.NO_RELEVANT, "seoul-25", "날씨", List.of(NO_EVIDENCE), List.of(), List.of()),
                // 나이·소득 없음: 임베딩도 호출하지 않는다 (캐시에 없는 질문이어도 실패하지 않음)
                ragCase("e-incomplete", Category.PROFILE_INCOMPLETE, "incomplete", "지원금", List.of(PROFILE_INCOMPLETE), List.of(), List.of()));
        PolicySearchSnapshotLoader.Loaded loaded = PolicySearchSnapshotLoader.load(
                PolicySearchSnapshot.load(Path.of("src/test/resources/ai-eval/search-snapshot-sample")), policyRepository);
        AtomicInteger calls = new AtomicInteger();
        AiTextClient fakeAi = request -> {
            calls.incrementAndGet();
            String answer = request.messages().getLast().content().equals("월세")
                    ? "월 20만원을 최대 12개월 지원합니다 [1]" : "전세 대출이 있습니다 [1]";
            return new AiTextResult(answer, "fake-model", 900, 40, 0);
        };

        List<Prepared> prepared = PolicyRagEvalRunner.prepareEndToEnd(cases, SEARCH_SET, loaded, policyService,
                CACHE.client(), SearchConfig.PRODUCTION, Year.now(BirthYearValidator.ZONE));
        List<Result> results = PolicyRagEvalRunner.run(prepared, fakeAi, new PolicyRagPrompt(),
                policy -> loaded.keyOf(policy).toString(), 2);

        // AI는 검색 결과가 있는 2문항만 호출한다
        assertThat(calls).hasValue(2);
        assertThat(results).extracting(Result::status).containsExactly(ANSWERED, ANSWERED, NO_EVIDENCE, PROFILE_INCOMPLETE);

        Result rent = results.get(0);
        assertThat(rent.hitKeys()).containsExactly(RENT);
        assertThat(rent.sources()).containsExactly(RENT);
        assertThat(rent.score().passed()).isTrue();

        Result loan = results.get(1);
        assertThat(loan.hitKeys()).containsExactly(KINFA, RENT);
        assertThat(loan.searchMissed()).containsExactly(BUSAN);
        assertThat(loan.score().failures()).containsExactly("기대 근거 누락: [" + BUSAN + "]");

        assertThat(results.get(2).hitKeys()).isEmpty();
        assertThat(results.get(2).rawAnswer()).isNull();
        assertThat(results.get(2).score().passed()).isTrue();
        assertThat(results.get(3).score().passed()).isTrue();
        assertThat(results.get(3).rawAnswer()).isNull();
    }

    @Test
    void 평가셋_파일은_검색_평가셋_프로필과_맞다() {
        PolicyRagE2eEvalSet evalSet = PolicyRagE2eEvalSet.load().validate(PolicySearchEvalSet.load());

        assertThat(evalSet.cases()).isNotEmpty();
        assertThat(evalSet.cases()).allSatisfy(c -> assertThat(c.note()).isNotBlank());
    }

    @Test
    void 평가셋_검증은_프로필_정책_키_상태_조합을_확인한다() {
        RagCase badProfile = ragCase("a", Category.SINGLE_MATCH, "no-such", "q", List.of(ANSWERED), List.of(), List.of());
        RagCase badKey = ragCase("b", Category.SINGLE_MATCH, "seoul-25", "q", List.of(ANSWERED), List.of("yc-busan-moving"), List.of());
        RagCase badIncomplete = ragCase("c", Category.PROFILE_INCOMPLETE, "incomplete", "q", List.of(NO_EVIDENCE), List.of(), List.of());
        RagCase withHits = new RagCase("d", Category.SINGLE_MATCH, "seoul-25", "q", List.of(RENT), List.of(ANSWERED),
                List.of(), List.of(), List.of(), List.of(), false, false, false, null);

        assertThatThrownBy(() -> new PolicyRagE2eEvalSet("t", Map.of(), List.of(badProfile)).validate(SEARCH_SET))
                .hasMessageContaining("프로필");
        assertThatThrownBy(() -> new PolicyRagE2eEvalSet("t", Map.of(), List.of(badKey)).validate(SEARCH_SET))
                .hasMessageContaining("정책 키");
        assertThatThrownBy(() -> new PolicyRagE2eEvalSet("t", Map.of(), List.of(badIncomplete)).validate(SEARCH_SET))
                .hasMessageContaining("PROFILE_INCOMPLETE");
        assertThatThrownBy(() -> new PolicyRagE2eEvalSet("t", Map.of(), List.of(withHits)).validate(SEARCH_SET))
                .hasMessageContaining("hits");
    }
}
