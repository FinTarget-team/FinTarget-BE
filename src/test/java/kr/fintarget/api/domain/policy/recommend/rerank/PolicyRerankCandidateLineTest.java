package kr.fintarget.api.domain.policy.recommend.rerank;

import kr.fintarget.api.domain.policy.condition.BenefitKind;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyCondition;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyRerankCandidateLineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    @Test
    void 한_줄에_번호_정책명_유형_혜택_대상_지원_지역_신청_특정_대상을_넣는다() {
        Policy policy = Policy.create("청년 월세 지원", "무주택 청년 월세 지원", 19, 34, null, null, PolicyType.HOUSING,
                "SEOUL", "ext-1", "YOUTH_CENTER",
                PolicyText.builder().applyPeriodCode("0057002").eligibilityDetail("부모와 따로 사는 무주택 청년")
                        .supportContent("월 최대 20만원\n최장 12개월").build());
        policy.updateCondition(PolicyCondition.builder().benefitKind(BenefitKind.MONEY).specialTarget(false).build());

        String line = PolicyRerankCandidateLine.of(3, policy, "SEOUL", TODAY);

        assertThat(line).isEqualTo("[3] 청년 월세 지원 | 유형: 주거 | 혜택: MONEY(현금·적립·이자 지원) "
                + "| 대상: 19~34세, 부모와 따로 사는 무주택 청년 | 지원: 월 최대 20만원 최장 12개월 | 지역: 서울 | 신청: 가능 | 특정 대상: 아님");
    }

    @Test
    void 특정_대상_집단과_시군구_한정_미분석을_표시한다() {
        Policy special = Policy.create("신혼부부 전세 지원", null, null, null, null, null, PolicyType.HOUSING,
                null, "ext-2", "YOUTH_CENTER", null);
        special.updateCondition(PolicyCondition.builder().benefitKind(BenefitKind.COST_SAVING).localScope(true)
                .specialTarget(true).specialTargetGroup("신혼부부").build());
        Policy unextracted = Policy.create("청년 수당", null, null, null, null, null, PolicyType.ETC, null, "ext-3",
                "YOUTH_CENTER", null);

        assertThat(PolicyRerankCandidateLine.of(1, special, "SEOUL", TODAY))
                .contains("혜택: COST_SAVING(할인·바우처·감면)", "지역: 전국(시·군·구 한정)", "특정 대상: 신혼부부", "대상: 정보 없음");
        assertThat(PolicyRerankCandidateLine.of(2, unextracted, "SEOUL", TODAY))
                .contains("혜택: 미분석", "특정 대상: 확인 안 됨", "신청: 확인 필요");
    }

    @Test
    void 긴_값은_자르고_구분자는_바꾼다() {
        assertThat(PolicyRerankCandidateLine.cut("가".repeat(10), 5)).isEqualTo("가가가가가…");
        assertThat(PolicyRerankCandidateLine.cut("a | b\n\n c", 50)).isEqualTo("a / b c");
        assertThat(PolicyRerankCandidateLine.cut(null, 5)).isEmpty();
    }
}
