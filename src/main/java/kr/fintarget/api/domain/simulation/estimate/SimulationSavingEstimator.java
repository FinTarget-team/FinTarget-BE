package kr.fintarget.api.domain.simulation.estimate;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog.SavingEstimate;
import kr.fintarget.api.domain.user.code.IncomeBracket;
import kr.fintarget.api.domain.user.code.Region;
import kr.fintarget.api.domain.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 주거 정책의 월 절감액 추정 (#257). 금액이 없어(SERVICE) 시뮬레이션에 반영되지 않는 주거 정책을,
 * 공식 기준값(지역 원룸 평균 월세)과 정책별 임대료 비율로 "월에 얼마를 덜 쓰는지"로 바꿔 준다.
 *
 * <p>DB에 쓰지 않고 요청 시점에만 계산한다. 따라서 추천 정렬 키인 {@link Policy#hasBenefitForSimulation()}과
 * DB의 혜택 값은 그대로이고, 추천 순서·리랭킹 결과는 바뀌지 않는다.
 *
 * <p>실제 지급액(CASH)이 있는 정책은 추정하지 않는다 (지급액이 우선).
 * 기준 월세가 서울 대학가 값이라 {@link SavingEstimate#userRegions()}로 서울 사용자에게만 적용한다.
 */
@Component
public class SimulationSavingEstimator {

    /** 기준 월세 기본값(원). 다방, 서울 주요 10개 대학 인근 원룸(보증금 1000만원·전용 33㎡ 이하) 평균 62.2만원 (2026-01) */
    public static final long DEFAULT_BASE_RENT = 620_000L;

    // 지역별 기준 월세의 근거를 한 단어로 (basis 문구에 들어간다). 기준값이 있는 지역만 둔다
    private static final Map<Region, String> REGION_LABELS = Map.of(Region.SEOUL, "서울 주요 대학가");

    private final ManualPolicyCatalog catalog;
    private final boolean enabled;
    private final boolean exposeInPolicyResponses;
    private final long baseRent;

    @Autowired
    public SimulationSavingEstimator(ManualPolicyCatalog catalog,
                                     @Value("${simulation.saving-estimate.enabled:true}") boolean enabled,
                                     @Value("${simulation.saving-estimate.expose-in-policy-responses:true}") boolean exposeInPolicyResponses,
                                     @Value("${simulation.saving-estimate.base-rent:" + DEFAULT_BASE_RENT + "}") long baseRent) {
        if (baseRent < 1) {
            throw new IllegalArgumentException("simulation.saving-estimate.base-rent는 1 이상이어야 합니다: " + baseRent);
        }
        this.catalog = catalog;
        this.enabled = enabled;
        this.exposeInPolicyResponses = exposeInPolicyResponses;
        this.baseRent = baseRent;
    }

    /** 추정을 끈 추정기 (테스트용) */
    public static SimulationSavingEstimator disabled() {
        return new SimulationSavingEstimator(new ManualPolicyCatalog(), false, false, DEFAULT_BASE_RENT);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 추천 목록·스크랩 응답의 simulationBenefit에도 추정값을 넣는지 (simulation.saving-estimate.expose-in-policy-responses) */
    public boolean isExposedInPolicyResponses() {
        return enabled && exposeInPolicyResponses;
    }

    /** 기준 월세(원) */
    public long baseRent() {
        return baseRent;
    }

    /**
     * 이 정책을 이 사용자가 쓸 때의 월 절감액. 추정 대상이 아니면 빈 값.
     * 시뮬레이션 실행이 쓰는 경로이므로 expose 설정과 무관하게 enabled만 본다.
     */
    public Optional<SavingEstimateResult> estimate(Policy policy, User user) {
        if (!enabled || policy == null || user == null) {
            return Optional.empty();
        }
        // 실제 지급액이 있으면 그 값으로 계산한다 (추정이 지급액을 덮지 않는다)
        if (policy.hasBenefitForSimulation()) {
            return Optional.empty();
        }
        Region region = user.getRegion() == null ? null : Region.fromCode(user.getRegion());
        String regionLabel = region == null ? null : REGION_LABELS.get(region);
        if (regionLabel == null) {
            return Optional.empty();
        }
        return catalog.savingEstimates().stream()
                .filter(estimate -> estimate.matches(policy))
                .findFirst()
                .filter(estimate -> estimate.appliesTo(user.getRegion()))
                .flatMap(estimate -> calculate(estimate, user, regionLabel));
    }

    /** 추천 목록·스크랩 응답에 넣을 추정값. expose 설정이 꺼져 있으면 빈 값 */
    public Optional<SavingEstimateResult> estimateForResponse(Policy policy, User user) {
        return isExposedInPolicyResponses() ? estimate(policy, user) : Optional.empty();
    }

    private Optional<SavingEstimateResult> calculate(SavingEstimate estimate, User user, String regionLabel) {
        return switch (estimate.rule()) {
            case SavingEstimate.FIXED_RENT -> amount(baseRent - estimate.rent(), estimate,
                    "%s 대비 기숙사비 월 %s만원".formatted(rentText(regionLabel), manwon(estimate.rent())));
            case SavingEstimate.RENT_RATIO -> ratioResult(estimate, estimate.ratio(), regionLabel);
            case SavingEstimate.INCOME_TIERED_RATIO -> tieredResult(estimate, user, regionLabel);
            default -> Optional.empty();
        };
    }

    private Optional<SavingEstimateResult> tieredResult(SavingEstimate estimate, User user, String regionLabel) {
        IncomeBracket bracket = IncomeBracket.fromIncome(user.getIncome());
        if (bracket == null) {
            return Optional.empty();
        }
        return ratioResult(estimate, estimate.ratioFor(bracket.getMinIncome()), regionLabel);
    }

    private Optional<SavingEstimateResult> ratioResult(SavingEstimate estimate, Double ratio, String regionLabel) {
        if (ratio == null) {
            return Optional.empty();
        }
        long monthly = Math.round(baseRent * (1 - ratio));
        return amount(monthly, estimate,
                "%s의 %s%% 수준 임대료".formatted(rentText(regionLabel), percent(ratio)));
    }

    private Optional<SavingEstimateResult> amount(long monthlyAmount, SavingEstimate estimate, String middle) {
        if (monthlyAmount <= 0) {
            return Optional.empty();
        }
        return Optional.of(new SavingEstimateResult(monthlyAmount, estimate.months(),
                "입주 시 %s%s 계산한 예상 절감액".formatted(middle, particle(middle))));
    }

    private String rentText(String regionLabel) {
        return "%s 원룸 평균 월세 %s만원".formatted(regionLabel, manwon(baseRent));
    }

    // 바로 앞 글자의 받침에 맞는 조사. "만원" 뒤에는 "으로", "임대료" 뒤에는 "로"
    private static String particle(String text) {
        char last = text.charAt(text.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) {
            return "으로";
        }
        int finalConsonant = (last - 0xAC00) % 28;
        // 받침이 없거나 'ㄹ'이면 "로"
        return finalConsonant == 0 || finalConsonant == 8 ? "로" : "으로";
    }

    // 원 단위 금액을 만원 단위 문구로. 소수점은 필요할 때만 한 자리 (620000 → "62", 625000 → "62.5")
    private static String manwon(long won) {
        double value = won / 10_000.0;
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(Math.round(value * 10) / 10.0);
    }

    // 비율을 백분율 문구로. 소수점은 필요할 때만 한 자리 (0.5 → "50", 0.655 → "65.5")
    private static String percent(double ratio) {
        double value = ratio * 100;
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(Math.round(value * 10) / 10.0);
    }
}
