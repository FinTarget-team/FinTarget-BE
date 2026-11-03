package kr.fintarget.api.domain.policy.benefit.eval;

import kr.fintarget.api.domain.policy.benefit.BenefitExtraction;
import kr.fintarget.api.domain.policy.benefit.BenefitExtractionParser;
import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.policy.benefit.eval.PolicyBenefitEvalSet.EvalCase;
import kr.fintarget.api.domain.policy.benefit.eval.PolicyBenefitEvalSet.Expected;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 평가셋 정답 자체의 일관성 검사 (#162). 정답이 틀리면 모델 비교가 무의미하므로 일반 테스트에서 항상 돈다.
 */
class PolicyBenefitEvalSetTest {

    private final PolicyBenefitEvalSet evalSet = PolicyBenefitEvalSet.load();

    @Test
    void 평가셋은_50건이고_id가_중복되지_않는다() {
        Set<String> ids = new HashSet<>();
        evalSet.cases().forEach(c -> assertThat(ids.add(c.id())).as(c.id()).isTrue());
        assertThat(evalSet.cases()).hasSize(50);
    }

    @Test
    void CASH_정답은_금액이_있고_나머지_유형은_금액이_없다() {
        for (EvalCase c : evalSet.cases()) {
            Expected e = c.expected();
            if (e.type() == BenefitType.CASH) {
                assertThat(e.monthlyAmount() != null || e.oneTimeAmount() != null).as(c.id()).isTrue();
            } else {
                assertThat(e.monthlyAmount()).as(c.id()).isNull();
                assertThat(e.months()).as(c.id()).isNull();
                assertThat(e.oneTimeAmount()).as(c.id()).isNull();
            }
        }
    }

    @Test
    void CASH_정답을_AI_응답으로_돌려도_파서_검증을_통과한다() {
        // 정답의 근거 문장이 원문에 실제로 있고 금액이 상한 안이어야, 모델이 정답을 내면 CASH로 저장된다
        for (EvalCase c : evalSet.cases()) {
            Expected e = c.expected();
            if (e.type() != BenefitType.CASH) {
                continue;
            }
            String json = String.format(
                    "{\"type\":\"CASH\",\"monthlyAmount\":%s,\"months\":%s,\"oneTimeAmount\":%s,\"isMaximum\":%s,\"evidence\":\"%s\"}",
                    e.monthlyAmount(), e.months(), e.oneTimeAmount(), e.isMaximum(), e.evidence().replace("\"", "\\\""));
            BenefitExtraction parsed = BenefitExtractionParser.parse(json, c.policy().toSource());

            assertThat(parsed.validationNote()).as(c.id()).isNull();
            assertThat(PolicyBenefitScorer.score(e, parsed).verdict()).as(c.id())
                    .isEqualTo(PolicyBenefitScorer.Verdict.CORRECT);
        }
    }
}
