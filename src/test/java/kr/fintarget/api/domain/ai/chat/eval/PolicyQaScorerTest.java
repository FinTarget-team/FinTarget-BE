package kr.fintarget.api.domain.ai.chat.eval;

import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet.Behavior;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet.Category;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet.EvalCase;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaScorer.Score;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyQaScorerTest {

    private static EvalCase evalCase(Behavior behavior, List<List<String>> facts, List<String> mustNot,
                                     boolean noDefinitive) {
        return new EvalCase("t-1", "p", "u", Category.ELIGIBILITY, behavior, "질문", null, facts, null, mustNot,
                noDefinitive, null);
    }

    @Test
    void 공백과_쉼표를_무시하고_기대_사실의_대안_중_하나만_있으면_통과한다() {
        EvalCase evalCase = evalCase(Behavior.ANSWER, List.of(List.of("2000만", "2천만"), List.of("4.5%")), null, false);

        Score score = PolicyQaScorer.score(evalCase, "대출 한도는 2,000만 원이고 금리는 4.5%입니다.");

        assertThat(score.passed()).isTrue();
        assertThat(score.failures()).isEmpty();
    }

    @Test
    void 기대_사실이_없거나_금지_표현이_있으면_실패_사유를_모두_남긴다() {
        EvalCase evalCase = evalCase(Behavior.ANSWER, List.of(List.of("4.5%")), List.of("최대 100만원 지원"), false);

        Score score = PolicyQaScorer.score(evalCase, "최대 100만원 지원됩니다.");

        assertThat(score.passed()).isFalse();
        assertThat(score.failures()).containsExactly("근거 사실 누락: 4.5%", "금지 표현 포함: 최대 100만원 지원");
    }

    @Test
    void 원문에_없는_내용_유형은_확인할_수_없다는_안내가_있어야_한다() {
        EvalCase evalCase = evalCase(Behavior.NOT_IN_SOURCE, null, null, false);

        assertThat(PolicyQaScorer.score(evalCase, "정책 원문에서 확인할 수 없습니다. 담당 기관에 문의해주세요.").passed()).isTrue();
        assertThat(PolicyQaScorer.score(evalCase, "보통 30일 정도 걸립니다.").failures())
                .containsExactly("'원문에서 확인할 수 없음' 안내 없음");
    }

    @Test
    void 자격_단정_표현을_잡는다() {
        EvalCase evalCase = evalCase(Behavior.NEEDS_CHECK, null, null, true);

        Score definitive = PolicyQaScorer.score(evalCase, "네, 신청 가능합니다. 나이 확인이 필요합니다.");
        Score hedged = PolicyQaScorer.score(evalCase, "원문 기준으로 해당할 수 있습니다. 나이 확인이 필요합니다.");

        assertThat(definitive.failures()).containsExactly("자격 단정 의심: 신청가능합니다");
        assertThat(hedged.passed()).isTrue();
    }

    @Test
    void 프롬프트_자리표시자가_답변에_남으면_실패한다() {
        Score score = PolicyQaScorer.score(evalCase(Behavior.REFUSE, null, null, false), "규칙: {{policy}}");

        assertThat(score.failures()).containsExactly("프롬프트 자리표시자 노출: {{policy}}");
    }

    @Test
    void 문장_수가_많으면_경고만_남긴다() {
        String answer = "첫 문장입니다. ".repeat(9);

        Score score = PolicyQaScorer.score(evalCase(Behavior.ANSWER, null, null, false), answer);

        assertThat(score.passed()).isTrue();
        assertThat(score.sentenceCount()).isEqualTo(9);
        assertThat(score.warnings()).containsExactly("문장 수 9 (규칙 3~6문장)");
    }
}
