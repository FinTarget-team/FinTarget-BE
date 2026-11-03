package kr.fintarget.api.domain.policy.benefit.eval;

import kr.fintarget.api.domain.policy.benefit.BenefitExtraction;
import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.policy.benefit.eval.PolicyBenefitEvalSet.Expected;
import kr.fintarget.api.domain.policy.benefit.eval.PolicyBenefitScorer.Verdict;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyBenefitScorerTest {

    private static final Expected CASH = new Expected(BenefitType.CASH, 200_000L, 12, null, true, "e");
    private static final Expected LOAN = new Expected(BenefitType.LOAN, null, null, null, false, "e");

    private static BenefitExtraction cash(Long monthly, Integer months, Long oneTime) {
        return new BenefitExtraction(BenefitType.CASH, monthly, months, oneTime, true, "e", null);
    }

    @Test
    void 금액과_기간이_같으면_정답이다() {
        assertThat(PolicyBenefitScorer.score(CASH, cash(200_000L, 12, null)).verdict()).isEqualTo(Verdict.CORRECT);
    }

    @Test
    void 반영하면_안_되는_정책을_CASH로_내면_잘못_반영이다() {
        var score = PolicyBenefitScorer.score(LOAN, cash(100_000L, 12, null));

        assertThat(score.verdict()).isEqualTo(Verdict.FALSE_REFLECT);
        assertThat(score.passed()).isFalse();
    }

    @Test
    void CASH_정책을_반영하지_못하면_놓침이다() {
        assertThat(PolicyBenefitScorer.score(CASH, BenefitExtraction.of(BenefitType.SERVICE, null, null)).verdict())
                .isEqualTo(Verdict.MISSED);
    }

    @Test
    void 기간이_다르면_금액_오류다() {
        var score = PolicyBenefitScorer.score(CASH, cash(200_000L, 24, null));

        assertThat(score.verdict()).isEqualTo(Verdict.WRONG_AMOUNT);
        assertThat(score.details()).anyMatch(d -> d.contains("기간"));
    }

    @Test
    void 월_평균_환산의_1원_반올림_차이는_허용한다() {
        Expected quarterly = new Expected(BenefitType.CASH, 83_333L, 12, null, false, "e");

        assertThat(PolicyBenefitScorer.score(quarterly, cash(83_334L, 12, null)).verdict()).isEqualTo(Verdict.CORRECT);
    }

    @Test
    void 반영하지_않는_유형끼리_다르면_통과로_보되_구분해_남긴다() {
        var score = PolicyBenefitScorer.score(LOAN, BenefitExtraction.of(BenefitType.SERVICE, null, null));

        assertThat(score.verdict()).isEqualTo(Verdict.WRONG_TYPE);
        assertThat(score.passed()).isTrue();
    }

    @Test
    void isMaximum_차이는_경고만_남긴다() {
        var score = PolicyBenefitScorer.score(CASH,
                new BenefitExtraction(BenefitType.CASH, 200_000L, 12, null, false, "e", null));

        assertThat(score.verdict()).isEqualTo(Verdict.CORRECT);
        assertThat(score.warnings()).hasSize(1);
    }
}
