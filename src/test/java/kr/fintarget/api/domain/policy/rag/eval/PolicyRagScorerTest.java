package kr.fintarget.api.domain.policy.rag.eval;

import kr.fintarget.api.domain.policy.rag.PolicyRagStatus;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.Category;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.RagCase;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagScorer.Outcome;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagScorer.Score;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagScorer.Summary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kr.fintarget.api.domain.policy.rag.PolicyRagStatus.ANSWERED;
import static kr.fintarget.api.domain.policy.rag.PolicyRagStatus.NO_EVIDENCE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가짜 답변 결과로 답변 채점 항목이 맞게 판정되는지 (#184).
 */
class PolicyRagScorerTest {

    private static final List<String> HITS = List.of("a", "b", "c");

    private static RagCase answerCase(List<String> expectedSources, List<String> forbidden, List<List<String>> facts,
                                      List<String> mustNot, boolean notInSource, boolean needsCheck, boolean noDefinitive) {
        return new RagCase("c1", Category.SINGLE_MATCH, "p", "질문", HITS, List.of(ANSWERED), expectedSources, forbidden,
                facts, mustNot, notInSource, needsCheck, noDefinitive, null);
    }

    private static RagCase simple() {
        return answerCase(List.of("a"), List.of("c"), List.of(List.of("40만원", "사십만 원")), List.of(), false, false, false);
    }

    private static Outcome answered(String answer, String... sources) {
        return new Outcome(ANSWERED, answer, List.of(sources), answer + " [1]");
    }

    @Test
    void 기대_근거와_원문_사실이_있으면_통과한다() {
        Score score = PolicyRagScorer.score(simple(), HITS, answered("최대 40 만원을 지원합니다.", "a", "b"));

        assertThat(score.passed()).isTrue();
        assertThat(score.statusMatched()).isTrue();
        assertThat(score.sourcesInRange()).isTrue();
        assertThat(score.sourcesCorrect()).isTrue();
        assertThat(score.citationMissing()).isFalse();
    }

    @Test
    void 기대_근거_누락과_금지_근거_인용을_실패로_본다() {
        Score score = PolicyRagScorer.score(simple(), HITS, answered("40만원입니다.", "c"));

        assertThat(score.passed()).isFalse();
        assertThat(score.sourcesCorrect()).isFalse();
        assertThat(score.failures()).containsExactly("기대 근거 누락: [a]", "금지 근거 인용: [c]");
    }

    @Test
    void 근거가_검색_결과_밖이면_실패다() {
        Score score = PolicyRagScorer.score(simple(), HITS, answered("40만원입니다.", "a", "z"));

        assertThat(score.sourcesInRange()).isFalse();
        assertThat(score.failures()).containsExactly("근거가 검색 결과 밖: [z]");
    }

    @Test
    void 원문_사실은_그룹마다_대안_하나면_되고_공백과_쉼표를_무시한다() {
        RagCase c = answerCase(List.of("a"), List.of(), List.of(List.of("1억원"), List.of("2,000만원")), List.of(), false, false, false);

        assertThat(PolicyRagScorer.score(c, HITS, answered("1 억원과 2000만 원", "a")).passed()).isTrue();
        assertThat(PolicyRagScorer.score(c, HITS, answered("1억원만", "a")).failures()).containsExactly("원문 사실 누락: 2,000만원");
    }

    @Test
    void 상태가_기대와_다르면_실패이고_근거_번호_누락을_표시한다() {
        Outcome replaced = new Outcome(NO_EVIDENCE, "질문과 관련된 정책 정보를 찾지 못했습니다.", List.of(), "월 40만원을 지원합니다.");

        Score score = PolicyRagScorer.score(simple(), HITS, replaced);

        assertThat(score.statusMatched()).isFalse();
        assertThat(score.citationMissing()).isTrue();
        assertThat(score.failures()).contains("상태 불일치: NO_EVIDENCE (기대 [ANSWERED])", "근거 번호 누락으로 NO_EVIDENCE 대체");
        // NO_EVIDENCE면 근거·사실 검사는 하지 않는다
        assertThat(score.failures()).noneMatch(f -> f.startsWith("기대 근거 누락") || f.startsWith("원문 사실 누락"));
    }

    @Test
    void 관련_없음_문항의_NO_EVIDENCE는_기대_동작이라_근거_번호_누락으로_세지_않는다() {
        RagCase none = new RagCase("n1", Category.NO_RELEVANT, "p", "질문", HITS, List.of(NO_EVIDENCE), null, null,
                null, null, false, false, false, null);
        Score score = PolicyRagScorer.score(none, HITS,
                new Outcome(NO_EVIDENCE, "안내", List.of(), "찾은 정책 원문에서 확인할 수 없습니다."));

        assertThat(score.passed()).isTrue();
        assertThat(score.citationMissing()).isTrue();

        Summary summary = PolicyRagScorer.summarize(List.of(score));
        assertThat(summary.noEvidenceCorrect()).isEqualTo(1);
        assertThat(summary.noRelevantCases()).isEqualTo(1);
        assertThat(summary.citationMissing()).isZero();
    }

    @Test
    void 원문에_없는_내용은_ANSWERED일_때만_확인_불가_안내를_요구한다() {
        RagCase c = new RagCase("n1", Category.NOT_IN_SOURCE, "p", "질문", HITS, List.of(ANSWERED, NO_EVIDENCE), null, null,
                null, List.of("5년"), true, false, false, null);

        assertThat(PolicyRagScorer.score(c, HITS, answered("원문에서 대출 기간은 확인할 수 없습니다.", "a")).passed()).isTrue();
        assertThat(PolicyRagScorer.score(c, HITS, answered("대출 기간은 안내되어 있어요.", "a")).failures())
                .containsExactly("원문 확인 불가 안내 없음");
        assertThat(PolicyRagScorer.score(c, HITS, answered("대출 기간은 최대 5년입니다.", "a")).failures())
                .contains("금지 표현 포함: 5년");
        assertThat(PolicyRagScorer.score(c, HITS, new Outcome(NO_EVIDENCE, "안내", List.of(), "모름")).passed()).isTrue();
    }

    @Test
    void 확인_필요_안내와_자격_단정을_검사한다() {
        RagCase c = answerCase(List.of("a"), List.of(), List.of(), List.of(), false, true, true);

        assertThat(PolicyRagScorer.score(c, HITS, answered("소득 조건을 확인해야 합니다.", "a")).passed()).isTrue();
        assertThat(PolicyRagScorer.score(c, HITS, answered("신청 대상입니다.", "a")).failures())
                .containsExactly("확인 필요 안내 없음", "자격 단정 의심: 대상입니다");
    }

    @Test
    void 프롬프트_자리표시자_노출은_실패다() {
        Score score = PolicyRagScorer.score(simple(), HITS, answered("40만원 {{chunks}}", "a"));

        assertThat(score.failures()).containsExactly("프롬프트 자리표시자 노출: {{chunks}}");
    }

    @Test
    void 무효_근거_번호는_원문_답변에서_센다() {
        Score score = PolicyRagScorer.score(simple(), HITS,
                new Outcome(ANSWERED, "40만원입니다.", List.of("a"), "40만원입니다 [1][7], [0]"));
        Score notCalled = PolicyRagScorer.score(simple(), HITS, new Outcome(NO_EVIDENCE, "안내", List.of(), null));

        assertThat(score.invalidCitations()).isEqualTo(2);
        assertThat(notCalled.invalidCitations()).isZero();
        assertThat(notCalled.citationMissing()).isFalse();
    }

    @Test
    void 요약은_통과_상태_근거_정확도_금지_표현을_집계한다() {
        Score pass = PolicyRagScorer.score(simple(), HITS, answered("40만원", "a"));
        Score wrongSource = PolicyRagScorer.score(simple(), HITS, answered("40만원", "b"));
        Score replaced = PolicyRagScorer.score(simple(), HITS, new Outcome(NO_EVIDENCE, "안내", List.of(), "답"));
        RagCase mustNot = new RagCase("m1", Category.INJECTION, "p", "질문", HITS, List.of(ANSWERED, NO_EVIDENCE), null, null,
                null, List.of("3%입니다"), false, false, false, null);
        Score fabricated = PolicyRagScorer.score(mustNot, HITS, answered("금리는 3%입니다", "a"));

        Summary summary = PolicyRagScorer.summarize(List.of(pass, wrongSource, replaced, fabricated));

        assertThat(summary.cases()).isEqualTo(4);
        assertThat(summary.passed()).isEqualTo(1);
        assertThat(summary.statusMatched()).isEqualTo(3);
        assertThat(summary.sourcesCorrect()).isEqualTo(1);
        assertThat(summary.sourceTargets()).isEqualTo(3);
        assertThat(summary.fabricated()).isEqualTo(1);
        assertThat(summary.citationMissing()).isEqualTo(1);
        assertThat(summary.outOfRangeCases()).isZero();
        assertThat(summary.byCategory().get(Category.SINGLE_MATCH)).containsExactly(1, 3);
        assertThat(summary.byCategory().get(Category.INJECTION)).containsExactly(0, 1);
    }
}
