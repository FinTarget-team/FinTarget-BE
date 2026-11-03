package kr.fintarget.api.domain.policy.benefit.eval;

import kr.fintarget.api.domain.policy.benefit.BenefitExtraction;
import kr.fintarget.api.domain.policy.benefit.eval.PolicyBenefitEvalSet.Expected;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 혜택 추출 결과를 정답과 비교한다 (#162).
 *
 * <p>판정은 시뮬레이션에 미치는 영향 기준이다.
 * <ul>
 *   <li>FALSE_REFLECT: 반영하면 안 되는 정책(대출·서비스 등)을 CASH로 반영 — 가장 나쁜 오류. 0에 가까워야 진행</li>
 *   <li>MISSED: CASH 정책을 반영하지 못함 — 정책 반영 결과가 null로 나갈 뿐 틀린 값은 아니다</li>
 *   <li>WRONG_AMOUNT: CASH로 맞혔지만 금액·기간이 다름 — 달성일이 틀린다</li>
 *   <li>WRONG_TYPE: 둘 다 반영 안 함이지만 유형이 다름 — 시뮬레이션 영향 없음, 참고용</li>
 * </ul>
 * isMaximum 불일치는 화면 안내 문구에만 영향이 있어 경고로만 남긴다.
 */
final class PolicyBenefitScorer {

    // 분기 지급 월 평균 환산(25만원/3) 반올림 차이 허용
    private static final long MONTHLY_TOLERANCE = 1L;

    private PolicyBenefitScorer() {
    }

    enum Verdict {
        CORRECT, FALSE_REFLECT, MISSED, WRONG_AMOUNT, WRONG_TYPE
    }

    record Score(Verdict verdict, List<String> details, List<String> warnings) {

        boolean passed() {
            return verdict == Verdict.CORRECT || verdict == Verdict.WRONG_TYPE;
        }
    }

    static Score score(Expected expected, BenefitExtraction actual) {
        List<String> details = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean expectedReflect = expected.type().reflectsInSimulation();
        boolean actualReflect = actual.type().reflectsInSimulation();

        if (!expectedReflect && actualReflect) {
            details.add("반영하면 안 되는 " + expected.type() + " 정책을 CASH로 반영");
            return new Score(Verdict.FALSE_REFLECT, details, warnings);
        }
        if (expectedReflect && !actualReflect) {
            details.add("CASH 정책을 " + actual.type() + "로 분류"
                    + (actual.validationNote() == null ? "" : " (검증 보정: " + actual.validationNote() + ")"));
            return new Score(Verdict.MISSED, details, warnings);
        }
        if (!expectedReflect) {
            if (expected.type() != actual.type()) {
                details.add("유형 " + expected.type() + " → " + actual.type());
                return new Score(Verdict.WRONG_TYPE, details, warnings);
            }
            return new Score(Verdict.CORRECT, details, warnings);
        }

        if (!amountEquals(expected.monthlyAmount(), actual.monthlyAmount(), MONTHLY_TOLERANCE)) {
            details.add("월 금액 " + expected.monthlyAmount() + " → " + actual.monthlyAmount());
        }
        // 월 지급이 없으면 기간은 계산에 쓰이지 않으므로 비교하지 않는다
        if (expected.monthlyAmount() != null && !Objects.equals(expected.months(), actual.months())) {
            details.add("기간 " + expected.months() + " → " + actual.months());
        }
        if (!amountEquals(expected.oneTimeAmount(), actual.oneTimeAmount(), 0L)) {
            details.add("1회 금액 " + expected.oneTimeAmount() + " → " + actual.oneTimeAmount());
        }
        if (expected.isMaximum() != actual.isMaximum()) {
            warnings.add("isMaximum " + expected.isMaximum() + " → " + actual.isMaximum());
        }
        return new Score(details.isEmpty() ? Verdict.CORRECT : Verdict.WRONG_AMOUNT, details, warnings);
    }

    private static boolean amountEquals(Long expected, Long actual, long tolerance) {
        if (expected == null || actual == null) {
            return expected == null && actual == null;
        }
        return Math.abs(expected - actual) <= tolerance;
    }
}
