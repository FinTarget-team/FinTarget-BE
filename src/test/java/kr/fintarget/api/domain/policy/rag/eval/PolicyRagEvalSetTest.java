package kr.fintarget.api.domain.policy.rag.eval;

import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaScorer;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.ai.usage.AiUsageLimiter;
import kr.fintarget.api.domain.policy.rag.PolicyRagPrompt;
import kr.fintarget.api.domain.policy.rag.PolicyRagResult;
import kr.fintarget.api.domain.policy.rag.PolicyRagService;
import kr.fintarget.api.domain.policy.rag.PolicyRagStatus;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.Bound;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.Category;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.RagCase;
import kr.fintarget.api.domain.policy.search.PolicySearchHit;
import kr.fintarget.api.domain.policy.search.PolicySearchService;
import kr.fintarget.api.domain.policy.service.PolicyService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 답변 평가셋 2단계 a의 형식과 라벨 일관성 (#184). 문항 초안을 사람이 검수하기 전에, 기대 사실이 실제 근거 정책 원문(청크)에 있는지 등
 * 기계적으로 확인할 수 있는 것을 확인한다.
 */
class PolicyRagEvalSetTest {

    private static final Bound BOUND = PolicyRagEvalSet.load().bind(PolicyQaEvalSet.load());

    @Test
    void 평가셋_20문항을_유형별로_읽고_스냅샷과_묶는다() {
        Map<Category, Long> counts = BOUND.evalSet().cases().stream()
                .collect(Collectors.groupingBy(RagCase::category, Collectors.counting()));

        assertThat(BOUND.evalSet().cases()).hasSize(20);
        assertThat(counts).containsEntry(Category.SINGLE_MATCH, 5L).containsEntry(Category.MULTI_MATCH, 3L)
                .containsEntry(Category.NO_RELEVANT, 3L).containsEntry(Category.NOT_IN_SOURCE, 3L)
                .containsEntry(Category.PROFILE_CHECK, 3L).containsEntry(Category.INJECTION, 2L)
                .containsEntry(Category.SAME_NAME_REGION, 1L);
        assertThat(BOUND.evalSet().cases()).allSatisfy(c -> assertThat(c.note()).isNotBlank());
    }

    @Test
    void 기대_사실은_기대_근거_정책의_청크에_있다() {
        for (RagCase c : BOUND.evalSet().cases()) {
            String sourceText = normalizedContent(c, c.expectedSources());
            for (List<String> group : c.expectedFacts()) {
                assertThat(group).as("%s 기대 사실이 근거 원문에 없음", c.id())
                        .anyMatch(fact -> sourceText.contains(PolicyQaScorer.normalize(fact)));
            }
        }
    }

    @Test
    void 금지_표현은_기대_근거_정책의_청크에_없다() {
        for (RagCase c : BOUND.evalSet().cases()) {
            String sourceText = normalizedContent(c, c.expectedSources());
            assertThat(c.mustNotInclude()).as("%s 금지 표현이 근거 원문에 있음", c.id())
                    .noneMatch(phrase -> sourceText.contains(PolicyQaScorer.normalize(phrase)));
        }
    }

    @Test
    void 검색_결과는_키_순서대로_고정_ID와_청크를_갖는다() {
        RagCase c = caseOf("single-02-miso-rate");

        List<PolicySearchHit> hits = BOUND.hitsOf(c);

        assertThat(hits).extracting(hit -> BOUND.keyOf(hit.policy())).containsExactlyElementsOf(c.hits());
        assertThat(hits.get(1).content()).startsWith("정책명: 미소금융 창업자금_창업초기 시설자금\n");
        assertThat(hits).extracting(PolicySearchHit::sectionLabel).containsOnly("전체");
        assertThat(BOUND.hitsOf(c)).extracting(PolicySearchHit::chunkId)
                .containsExactlyElementsOf(hits.stream().map(PolicySearchHit::chunkId).toList());
    }

    @Test
    void 합성_정책은_같은_이름에_지역과_금액만_다르다() {
        RagCase c = caseOf("region-01-same-name-busan");
        List<PolicySearchHit> hits = BOUND.hitsOf(c);

        assertThat(hits.get(0).policy().getName()).isEqualTo(hits.get(1).policy().getName());
        assertThat(hits.get(0).content()).contains("신청 가능 지역: 충남", "최대 40만원");
        assertThat(hits.get(1).content()).contains("신청 가능 지역: 부산", "최대 30만원").doesNotContain("40만원");
        assertThat(hits.get(1).policy().regionCodes()).containsExactly("BUSAN");
    }

    // 실호출 실행기(다음 단계)와 같은 경로: answerWithHits에 고정 검색 결과를 넣고, 결과를 채점기로 판정한다
    @Test
    void 고정_검색_결과로_답변_단계를_호출하고_채점할_수_있다() {
        RagCase c = caseOf("multi-01-busan-housing");
        List<AiTextRequest> requests = new ArrayList<>();
        AiTextClient fakeAi = request -> {
            requests.add(request);
            return new AiTextResult("부산 주거 지원은 2개입니다.\n- 머물자리론은 최대 1억원 대출입니다 [1]\n- 이사비는 최대 40만원입니다 [2]",
                    "fake", 0, 0, 0);
        };
        PolicyService policyService = Mockito.mock(PolicyService.class);
        PolicySearchService searchService = Mockito.mock(PolicySearchService.class);
        AiUsageLimiter limiter = Mockito.mock(AiUsageLimiter.class);
        PolicyRagService service = new PolicyRagService(policyService, searchService, fakeAi, new PolicyRagPrompt(),
                limiter, 20, 1000);

        PolicyRagResult result = service.answerWithHits(BOUND.hitsOf(c), BOUND.profileOf(c), c.question());

        assertThat(result.status()).isEqualTo(PolicyRagStatus.ANSWERED);
        assertThat(requests.getFirst().systemPrompt()).contains("[1]\n정책명: 부산 청년 임차보증금 대출", "[2]\n정책명: 부산 청년 중개보수",
                "- 거주 지역: 부산");
        verifyNoInteractions(policyService, searchService, limiter);

        PolicyRagScorer.Score score = PolicyRagScorer.score(c, c.hits(), new PolicyRagScorer.Outcome(result.status(),
                result.answer(), result.sources().stream().map(BOUND::keyOf).toList(), "원문 [1][2]"));
        assertThat(score.failures()).isEmpty();
    }

    private static RagCase caseOf(String id) {
        return BOUND.evalSet().cases().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static String normalizedContent(RagCase c, List<String> keys) {
        Map<String, PolicySearchHit> byKey = BOUND.hitsOf(c).stream()
                .collect(Collectors.toMap(hit -> BOUND.keyOf(hit.policy()), Function.identity()));
        return keys.stream().map(byKey::get).map(PolicySearchHit::content).map(PolicyQaScorer::normalize)
                .collect(Collectors.joining("\n"));
    }
}
