package kr.fintarget.api.domain.policy.manual;

import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyBenefit;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog.ManualBenefit;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog.ManualPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 직접 등록 정책 리소스(#245)의 형식과 혜택 덮기 규칙.
 */
class ManualPolicyCatalogTest {

    private final ManualPolicyCatalog catalog = new ManualPolicyCatalog();

    @Test
    void 리소스의_4개_정책은_MANUAL_출처로_만들어지고_원문_칸이_채워진다() {
        assertThat(catalog.policies()).extracting(ManualPolicy::externalId).containsExactly(
                "MANUAL-HOPE-DOUBLE-2026", "MANUAL-YOUTH-FUTURE-SAVINGS-2026",
                "MANUAL-NATIONAL-SCHOLARSHIP-1-2026", "MANUAL-SEOUL-YOUTH-ALLOWANCE-2026");

        for (ManualPolicy manual : catalog.policies()) {
            Policy policy = manual.toPolicy();
            assertThat(policy.getSource()).isEqualTo(ManualPolicyCatalog.SOURCE);
            assertThat(policy.getPolicyType()).isEqualTo(PolicyType.FINANCE_WELFARE);
            assertThat(policy.getText().getSupportContent()).isNotBlank();
            assertThat(policy.getText().getApplyPeriod()).isNotBlank();
            assertThat(policy.getText().getReferenceUrl()).startsWith("https://");
            assertThat(manual.benefit().type()).isEqualTo("CASH");
        }
    }

    @Test
    void 시뮬레이션_혜택_값은_정책_자료와_같다() {
        assertThat(benefit("MANUAL-HOPE-DOUBLE-2026")).isEqualTo(new ManualBenefit("CASH", 150_000L, 36, null, true,
                benefit("MANUAL-HOPE-DOUBLE-2026").evidence()));
        assertThat(benefit("MANUAL-YOUTH-FUTURE-SAVINGS-2026").monthlyAmount()).isEqualTo(30_000L);
        assertThat(benefit("MANUAL-YOUTH-FUTURE-SAVINGS-2026").months()).isEqualTo(36);
        assertThat(benefit("MANUAL-NATIONAL-SCHOLARSHIP-1-2026").oneTimeAmount()).isEqualTo(1_000_000L);
        assertThat(benefit("MANUAL-NATIONAL-SCHOLARSHIP-1-2026").evidence()).contains("9구간 100만원", "1~3구간 600만원");
        assertThat(benefit("MANUAL-SEOUL-YOUTH-ALLOWANCE-2026").monthlyAmount()).isEqualTo(500_000L);
        assertThat(benefit("MANUAL-SEOUL-YOUTH-ALLOWANCE-2026").months()).isEqualTo(6);
    }

    @Test
    void 직접_등록_정책만_리소스_혜택으로_덮고_추출_메타데이터는_유지한다() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 25, 12, 0);
        PolicyBenefit aiResult = PolicyBenefit.builder().type(BenefitType.SERVICE).evidence("AI").promptVersion("v1")
                .sourceHash("hash").extractedAt(at).build();
        Policy manual = catalog.policies().getFirst().toPolicy();
        Policy youth = Policy.create("2026 희망두배 청년통장", "설명", null, null, null, null, PolicyType.ETC, null,
                "MANUAL-HOPE-DOUBLE-2026", "YOUTH_CENTER");

        PolicyBenefit applied = catalog.applyBenefit(manual, aiResult);

        assertThat(applied.getType()).isEqualTo(BenefitType.CASH);
        assertThat(applied.getMonthlyAmount()).isEqualTo(150_000L);
        assertThat(applied.getPromptVersion()).isEqualTo("v1");
        assertThat(applied.getSourceHash()).isEqualTo("hash");
        assertThat(applied.getExtractedAt()).isEqualTo(at);
        // externalId가 같아도 출처가 다르면 덮지 않는다
        assertThat(catalog.applyBenefit(youth, aiResult)).isSameAs(aiResult);
    }

    @Test
    void 형식이_잘못된_항목은_기동_시_막는다() {
        ManualBenefit cash = new ManualBenefit("CASH", 100_000L, 12, null, false, "근거");
        assertThatThrownBy(() -> new ManualPolicyCatalog(List.of(policy("NO-PREFIX", "A", "SEOUL", cash))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ManualPolicyCatalog(List.of(policy("MANUAL-A", "A", "서울", cash))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ManualPolicyCatalog(List.of(policy("MANUAL-A", "A", null,
                new ManualBenefit("CASH", null, null, null, false, "근거"))))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ManualPolicyCatalog(List.of(policy("MANUAL-A", "A", null,
                new ManualBenefit("CASH", 100_000L, null, null, false, "근거"))))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ManualPolicyCatalog(List.of(policy("MANUAL-A", "A", null, cash),
                policy("MANUAL-A", "B", null, cash)))).isInstanceOf(IllegalStateException.class);
    }

    private ManualBenefit benefit(String externalId) {
        return catalog.policies().stream().filter(p -> p.externalId().equals(externalId)).findFirst().orElseThrow().benefit();
    }

    private static ManualPolicy policy(String externalId, String name, String region, ManualBenefit benefit) {
        return new ManualPolicy(externalId, name, "FINANCE_WELFARE", region, 19, 34, "설명", "지원 내용", null, null, null,
                "상시", null, null, null, null, benefit);
    }

    // ===== 동기화 정책 혜택 보정 (#245) =====

    private static Policy youthCenter(String externalId, String name) {
        return Policy.create(name, "설명", null, null, null, null, PolicyType.HOUSING, null, externalId, "YOUTH_CENTER");
    }

    @Test
    void 혜택_보정_대상은_출처_외부ID_정책명이_모두_같을_때만_리소스_값으로_덮는다() {
        assertThat(catalog.benefitOverrides()).extracting(ManualPolicyCatalog.BenefitOverride::externalId).containsExactly(
                "20261021005400214208", "20261030005400214649", "20260409005400212654", "20260506005400213155",
                "20260710005400113256", "20260421005400212823");
        PolicyBenefit aiResult = PolicyBenefit.builder().type(BenefitType.SERVICE).evidence("AI").promptVersion("v1")
                .sourceHash("hash").build();

        PolicyBenefit seoulRent = catalog.applyBenefit(youthCenter("20261021005400214208", "2026 청년 월세 지원"), aiResult);
        assertThat(seoulRent.getType()).isEqualTo(BenefitType.CASH);
        assertThat(seoulRent.getMonthlyAmount()).isEqualTo(200_000L);
        assertThat(seoulRent.getBenefitMonths()).isEqualTo(12);
        assertThat(seoulRent.getMaximum()).isTrue();
        assertThat(seoulRent.getSourceHash()).isEqualTo("hash");

        PolicyBenefit jobSeeker = catalog.applyBenefit(youthCenter("20260710005400113256", "국민취업지원제도"), aiResult);
        assertThat(jobSeeker.getMonthlyAmount()).isEqualTo(600_000L);
        assertThat(jobSeeker.getBenefitMonths()).isEqualTo(6);
        assertThat(jobSeeker.getMaximum()).isFalse();

        // 정책명이 다르면(외부 ID 재사용 등) 덮지 않는다
        assertThat(catalog.applyBenefit(youthCenter("20261021005400214208", "다른 정책"), aiResult)).isSameAs(aiResult);
        // 출처가 다르면 덮지 않는다
        Policy kinfa = Policy.create("2026 청년 월세 지원", "설명", null, null, null, null, PolicyType.HOUSING, null,
                "20261021005400214208", "KINFA");
        assertThat(catalog.applyBenefit(kinfa, aiResult)).isSameAs(aiResult);
        // 대상이 아닌 정책은 그대로
        assertThat(catalog.applyBenefit(youthCenter("OTHER", "청년 월세 지원"), aiResult)).isSameAs(aiResult);
    }

    @Test
    void 혜택_보정_항목이_잘못되면_기동_시_막는다() {
        ManualBenefit cash = new ManualBenefit("CASH", 200_000L, 12, null, true, "근거");
        assertThatThrownBy(() -> new ManualPolicyCatalog(List.of(), List.of(
                new ManualPolicyCatalog.BenefitOverride("MANUAL", "X", "이름", "이유", cash))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ManualPolicyCatalog(List.of(), List.of(
                new ManualPolicyCatalog.BenefitOverride("YOUTH_CENTER", "X", "이름", "이유", cash),
                new ManualPolicyCatalog.BenefitOverride("YOUTH_CENTER", "X", "이름2", "이유", cash))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ManualPolicyCatalog(List.of(), List.of(
                new ManualPolicyCatalog.BenefitOverride("YOUTH_CENTER", "X", "이름", "이유", null))))
                .isInstanceOf(IllegalStateException.class);
    }
}
