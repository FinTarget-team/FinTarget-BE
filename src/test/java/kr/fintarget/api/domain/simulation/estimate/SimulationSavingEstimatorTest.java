package kr.fintarget.api.domain.simulation.estimate;

import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyBenefit;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog;
import kr.fintarget.api.domain.user.code.IncomeBracket;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주거 정책 월 절감액 추정 (#257). 기대 금액은 설계 문서 6.1의 숫자이고, 기준 월세 62만원에서 나온다.
 */
class SimulationSavingEstimatorTest {

    private static final String DORM = "20260123005400112079";
    private static final String DORM_NAME = "대학생 연합생활관(은행권,고양)";
    private static final String DORM_YOUTH_HOUSE = "20260922005400113506";
    private static final String DORM_YOUTH_HOUSE_NAME = "기숙사형 청년주택 공급";
    private static final String SAFE_HOUSE = "20261021005400214219";
    private static final String SAFE_HOUSE_NAME = "2026 청년안심주택 공급(매입)";
    private static final String INTEGRATED = "20260921005400113460";
    private static final String INTEGRATED_NAME = "청년 맞춤형 공적 임대주택 공급(통합공공임대)";
    private static final String INTEGRATED_2 = "20260922005400113532";
    private static final String INTEGRATED_2_NAME = "청년층 대상 공공임대주택 공급(통합공공임대)";

    private final SimulationSavingEstimator estimator = estimator(SimulationSavingEstimator.DEFAULT_BASE_RENT);

    private static SimulationSavingEstimator estimator(long baseRent) {
        return new SimulationSavingEstimator(new ManualPolicyCatalog(), true, true, baseRent);
    }

    private static Policy policy(String externalId, String name) {
        return Policy.create(name, "설명", 19, 34, null, null, PolicyType.HOUSING, null, externalId, "YOUTH_CENTER",
                PolicyText.builder().build());
    }

    private static User user(String region, IncomeBracket bracket) {
        return User.builder()
                .id("u1").provider(SocialProvider.KAKAO).providerId("p1")
                .region(region)
                .birthYear(2003)
                .income(bracket == null ? null : bracket.getIncome())
                .build();
    }

    private static User seoul(IncomeBracket bracket) {
        return user("SEOUL", bracket);
    }

    private Optional<SavingEstimateResult> estimate(String externalId, String name, User user) {
        return estimator.estimate(policy(externalId, name), user);
    }

    @Test
    void 연합생활관은_기준_월세에서_기숙사비를_뺀_금액이다() {
        SavingEstimateResult result = estimate(DORM, DORM_NAME, seoul(IncomeBracket.UNDER_150)).orElseThrow();

        assertThat(result.monthlyAmount()).isEqualTo(470_000L);
        // 원문에 거주 기간이 없어 months는 null이고 계산은 기본 12개월이다
        assertThat(result.months()).isNull();
        assertThat(result.basis())
                .isEqualTo("입주 시 서울 주요 대학가 원룸 평균 월세 62만원 대비 기숙사비 월 15만원으로 계산한 예상 절감액");
    }

    @Test
    void 기숙사형_청년주택과_청년안심주택은_시세의_50퍼센트_기준이다() {
        assertThat(estimate(DORM_YOUTH_HOUSE, DORM_YOUTH_HOUSE_NAME, seoul(IncomeBracket.UNDER_150)).orElseThrow())
                .satisfies(result -> {
                    assertThat(result.monthlyAmount()).isEqualTo(310_000L);
                    assertThat(result.basis())
                            .isEqualTo("입주 시 서울 주요 대학가 원룸 평균 월세 62만원의 50% 수준 임대료로 계산한 예상 절감액");
                });
        assertThat(estimate(SAFE_HOUSE, SAFE_HOUSE_NAME, seoul(IncomeBracket.RANGE_150_260)).orElseThrow()
                .monthlyAmount()).isEqualTo(310_000L);
    }

    @Test
    void 통합공공임대는_소득_구간별_비율로_계산한다() {
        assertThat(estimate(INTEGRATED, INTEGRATED_NAME, seoul(IncomeBracket.UNDER_150)).orElseThrow().monthlyAmount())
                .isEqualTo(310_000L);
        assertThat(estimate(INTEGRATED, INTEGRATED_NAME, seoul(IncomeBracket.RANGE_150_260)).orElseThrow().monthlyAmount())
                .isEqualTo(217_000L);
        assertThat(estimate(INTEGRATED, INTEGRATED_NAME, seoul(IncomeBracket.RANGE_260_300)).orElseThrow().monthlyAmount())
                .isEqualTo(124_000L);
        // 300만원 이상은 모두 90% (실제보다 절감액을 크게 잡는 구간을 없앴다)
        assertThat(estimate(INTEGRATED, INTEGRATED_NAME, seoul(IncomeBracket.RANGE_300_380)).orElseThrow().monthlyAmount())
                .isEqualTo(62_000L);
        assertThat(estimate(INTEGRATED, INTEGRATED_NAME, seoul(IncomeBracket.OVER_410)).orElseThrow().monthlyAmount())
                .isEqualTo(62_000L);
    }

    @Test
    void 통합공공임대_basis에는_사용자_소득_구간의_비율이_들어간다() {
        assertThat(estimate(INTEGRATED, INTEGRATED_NAME, seoul(IncomeBracket.RANGE_150_260)).orElseThrow().basis())
                .isEqualTo("입주 시 서울 주요 대학가 원룸 평균 월세 62만원의 65% 수준 임대료로 계산한 예상 절감액");
        assertThat(estimate(INTEGRATED, INTEGRATED_NAME, seoul(IncomeBracket.RANGE_260_300)).orElseThrow().basis())
                .isEqualTo("입주 시 서울 주요 대학가 원룸 평균 월세 62만원의 80% 수준 임대료로 계산한 예상 절감액");
    }

    @Test
    void 같은_제도인_청년층_대상_공공임대주택도_같은_규칙이다() {
        assertThat(estimate(INTEGRATED_2, INTEGRATED_2_NAME, seoul(IncomeBracket.RANGE_150_260)).orElseThrow()
                .monthlyAmount()).isEqualTo(217_000L);
    }

    @Test
    void 소득이_없으면_통합공공임대는_추정하지_않는다() {
        assertThat(estimate(INTEGRATED, INTEGRATED_NAME, seoul(null))).isEmpty();
        // 비율이 고정된 정책은 소득이 없어도 추정한다
        assertThat(estimate(SAFE_HOUSE, SAFE_HOUSE_NAME, seoul(null))).isPresent();
    }

    @Test
    void 외부_ID나_정책명이_다르면_추정하지_않는다() {
        assertThat(estimate(SAFE_HOUSE, "다른 이름", seoul(IncomeBracket.UNDER_150))).isEmpty();
        assertThat(estimate("99999999999999999999", SAFE_HOUSE_NAME, seoul(IncomeBracket.UNDER_150))).isEmpty();
        Policy otherSource = Policy.create(SAFE_HOUSE_NAME, "설명", 19, 34, null, null, PolicyType.HOUSING, null,
                SAFE_HOUSE, "KINFA", PolicyText.builder().build());
        assertThat(estimator.estimate(otherSource, seoul(IncomeBracket.UNDER_150))).isEmpty();
    }

    @Test
    void 서울_밖_사용자에게는_추정하지_않는다() {
        assertThat(estimate(SAFE_HOUSE, SAFE_HOUSE_NAME, user("GYEONGGI", IncomeBracket.UNDER_150))).isEmpty();
        assertThat(estimate(SAFE_HOUSE, SAFE_HOUSE_NAME, user("BUSAN", IncomeBracket.UNDER_150))).isEmpty();
        assertThat(estimate(SAFE_HOUSE, SAFE_HOUSE_NAME, user(null, IncomeBracket.UNDER_150))).isEmpty();
    }

    @Test
    void 설정을_끄면_추정하지_않는다() {
        SimulationSavingEstimator off = new SimulationSavingEstimator(new ManualPolicyCatalog(), false, true,
                SimulationSavingEstimator.DEFAULT_BASE_RENT);

        assertThat(off.estimate(policy(SAFE_HOUSE, SAFE_HOUSE_NAME), seoul(IncomeBracket.UNDER_150))).isEmpty();
        assertThat(off.isEnabled()).isFalse();
        assertThat(SimulationSavingEstimator.disabled().isEnabled()).isFalse();
    }

    @Test
    void 응답_노출만_끄면_시뮬레이션_계산에는_그대로_쓴다() {
        SimulationSavingEstimator hidden = new SimulationSavingEstimator(new ManualPolicyCatalog(), true, false,
                SimulationSavingEstimator.DEFAULT_BASE_RENT);
        Policy target = policy(SAFE_HOUSE, SAFE_HOUSE_NAME);

        assertThat(hidden.estimate(target, seoul(IncomeBracket.UNDER_150))).isPresent();
        assertThat(hidden.estimateForResponse(target, seoul(IncomeBracket.UNDER_150))).isEmpty();
        assertThat(hidden.isExposedInPolicyResponses()).isFalse();
        assertThat(estimator.isExposedInPolicyResponses()).isTrue();
    }

    @Test
    void 실제_지급액이_있는_정책은_추정하지_않는다() {
        Policy withCash = policy(SAFE_HOUSE, SAFE_HOUSE_NAME);
        withCash.updateBenefit(PolicyBenefit.builder()
                .type(BenefitType.CASH).monthlyAmount(200_000L).benefitMonths(12).build());

        assertThat(withCash.hasBenefitForSimulation()).isTrue();
        assertThat(estimator.estimate(withCash, seoul(IncomeBracket.UNDER_150))).isEmpty();
    }

    @Test
    void 기준_월세를_바꾸면_결과가_비례해서_바뀐다() {
        SimulationSavingEstimator cheaper = estimator(500_000L);

        assertThat(cheaper.estimate(policy(SAFE_HOUSE, SAFE_HOUSE_NAME), seoul(IncomeBracket.UNDER_150)).orElseThrow())
                .satisfies(result -> {
                    assertThat(result.monthlyAmount()).isEqualTo(250_000L);
                    assertThat(result.basis()).contains("월세 50만원의 50% 수준");
                });
        assertThat(cheaper.estimate(policy(DORM, DORM_NAME), seoul(IncomeBracket.UNDER_150)).orElseThrow()
                .monthlyAmount()).isEqualTo(350_000L);
    }

    @Test
    void 기준_월세가_실제_임대료보다_낮으면_추정하지_않는다() {
        SimulationSavingEstimator tooCheap = estimator(150_000L);

        assertThat(tooCheap.estimate(policy(DORM, DORM_NAME), seoul(IncomeBracket.UNDER_150))).isEmpty();
    }

    @Test
    void 기준_월세_설정이_0_이하면_기동에_실패한다() {
        assertThatThrownBy(() -> estimator(0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 추정_결과는_CASH_혜택으로_바뀌어_계산에_들어간다() {
        SavingEstimateResult result = estimate(DORM, DORM_NAME, seoul(IncomeBracket.UNDER_150)).orElseThrow();

        PolicyBenefit benefit = result.toPolicyBenefit();
        assertThat(benefit.getType()).isEqualTo(BenefitType.CASH);
        assertThat(benefit.getMonthlyAmount()).isEqualTo(470_000L);
        assertThat(benefit.getBenefitMonths()).isNull();
        assertThat(benefit.effectiveMonths()).isEqualTo(PolicyBenefit.DEFAULT_MONTHS_WHEN_UNSPECIFIED);
        assertThat(benefit.isMaximum()).isFalse();
        assertThat(benefit.getEvidence()).isEqualTo(result.basis());
    }
}
