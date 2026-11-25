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
 * 기준 월세는 지역별 값이 있는 지역(서울·경기)에만 있고, 정책별 적용 지역은 {@link SavingEstimate#userRegions()}로 제한한다.
 */
@Component
public class SimulationSavingEstimator {

    /** 서울 기준 월세 기본값(원). 다방, 서울 주요 10개 대학 인근 원룸(보증금 1000만원·전용 33㎡ 이하) 평균 62.2만원 (2026-01) */
    public static final long DEFAULT_SEOUL_BASE_RENT = 620_000L;
    /**
     * 경기 기준 월세 기본값(원). 공식 발표 중 서울 기준값과 비교할 수 있는 경기 원룸 평균 월세가 없어
     * 민간 집계(house.manuz.com, 국토교통부 전월세 실거래 중앙값, 전용 33㎡ 이하, 보증금 500만원, 계약일 2025-12~2026-11) 45만원을 쓴다.
     * 공식 통계가 아니고 대학가 기준도 아니므로 basis 문구에 "민간 집계 추정치"임을 밝힌다
     */
    public static final long DEFAULT_GYEONGGI_BASE_RENT = 450_000L;

    // 지역별 기준 월세 문구 (%s에 만원 단위 금액). 기준값이 있는 지역만 둔다.
    // 서울은 공식 발표(다방 대학가 평균)라 "평균 월세", 경기는 민간 집계라 "약 ...(민간 집계 추정치)"로 성격을 밝힌다
    private static final Map<Region, String> RENT_PHRASES = Map.of(
            Region.SEOUL, "서울 주요 대학가 원룸 평균 월세 %s만원",
            Region.GYEONGGI, "경기 원룸 월세 약 %s만원(민간 집계 추정치)");

    private final ManualPolicyCatalog catalog;
    private final boolean enabled;
    private final boolean exposeInPolicyResponses;
    private final Map<Region, Long> baseRents;

    /**
     * 기준 월세는 지역별 설정(simulation.saving-estimate.base-rent.SEOUL·GYEONGGI)으로 받는다.
     * 서울 값은 지역별 키가 없으면 이전 단일 키(simulation.saving-estimate.base-rent)를 그대로 쓴다 (호환).
     */
    @Autowired
    public SimulationSavingEstimator(ManualPolicyCatalog catalog,
                                     @Value("${simulation.saving-estimate.enabled:true}") boolean enabled,
                                     @Value("${simulation.saving-estimate.expose-in-policy-responses:true}") boolean exposeInPolicyResponses,
                                     @Value("${simulation.saving-estimate.base-rent.SEOUL:${simulation.saving-estimate.base-rent:"
                                             + DEFAULT_SEOUL_BASE_RENT + "}}") long seoulBaseRent,
                                     @Value("${simulation.saving-estimate.base-rent.GYEONGGI:"
                                             + DEFAULT_GYEONGGI_BASE_RENT + "}") long gyeonggiBaseRent) {
        requirePositive(seoulBaseRent, "SEOUL");
        requirePositive(gyeonggiBaseRent, "GYEONGGI");
        this.catalog = catalog;
        this.enabled = enabled;
        this.exposeInPolicyResponses = exposeInPolicyResponses;
        this.baseRents = Map.of(Region.SEOUL, seoulBaseRent, Region.GYEONGGI, gyeonggiBaseRent);
    }

    private static void requirePositive(long baseRent, String region) {
        if (baseRent < 1) {
            throw new IllegalArgumentException(
                    "simulation.saving-estimate.base-rent." + region + "는 1 이상이어야 합니다: " + baseRent);
        }
    }

    /** 추정을 끈 추정기 (테스트용) */
    public static SimulationSavingEstimator disabled() {
        return new SimulationSavingEstimator(new ManualPolicyCatalog(), false, false,
                DEFAULT_SEOUL_BASE_RENT, DEFAULT_GYEONGGI_BASE_RENT);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 추천 목록·스크랩 응답의 simulationBenefit에도 추정값을 넣는지 (simulation.saving-estimate.expose-in-policy-responses) */
    public boolean isExposedInPolicyResponses() {
        return enabled && exposeInPolicyResponses;
    }

    /** 이 지역의 기준 월세(원). 기준값이 없는 지역이면 null */
    public Long baseRent(Region region) {
        return region == null ? null : baseRents.get(region);
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
        Long baseRent = baseRent(region);
        if (baseRent == null) {
            return Optional.empty();
        }
        String rentText = RENT_PHRASES.get(region).formatted(manwon(baseRent));
        return catalog.savingEstimates().stream()
                .filter(estimate -> estimate.matches(policy))
                .findFirst()
                .filter(estimate -> estimate.appliesTo(user.getRegion()))
                .flatMap(estimate -> calculate(estimate, user, baseRent, rentText));
    }

    /** 추천 목록·스크랩 응답에 넣을 추정값. expose 설정이 꺼져 있으면 빈 값 */
    public Optional<SavingEstimateResult> estimateForResponse(Policy policy, User user) {
        return isExposedInPolicyResponses() ? estimate(policy, user) : Optional.empty();
    }

    private Optional<SavingEstimateResult> calculate(SavingEstimate estimate, User user, long baseRent, String rentText) {
        return switch (estimate.rule()) {
            // 기준 월세가 원문 임대료 이하면 절감액이 0 이하라 추정하지 않는다 (amount에서 걸러짐)
            case SavingEstimate.FIXED_RENT -> amount(baseRent - estimate.rent(), estimate,
                    "%s 대비 기숙사비 월 %s만원".formatted(rentText, manwon(estimate.rent())));
            case SavingEstimate.RENT_RATIO -> ratioResult(estimate, estimate.ratio(), baseRent, rentText);
            case SavingEstimate.INCOME_TIERED_RATIO -> tieredResult(estimate, user, baseRent, rentText);
            default -> Optional.empty();
        };
    }

    private Optional<SavingEstimateResult> tieredResult(SavingEstimate estimate, User user, long baseRent, String rentText) {
        IncomeBracket bracket = IncomeBracket.fromIncome(user.getIncome());
        if (bracket == null) {
            return Optional.empty();
        }
        return ratioResult(estimate, estimate.ratioFor(bracket.getMinIncome()), baseRent, rentText);
    }

    private Optional<SavingEstimateResult> ratioResult(SavingEstimate estimate, Double ratio, long baseRent, String rentText) {
        if (ratio == null) {
            return Optional.empty();
        }
        long monthly = Math.round(baseRent * (1 - ratio));
        return amount(monthly, estimate, "%s의 %s%% 수준 임대료".formatted(rentText, percent(ratio)));
    }

    private Optional<SavingEstimateResult> amount(long monthlyAmount, SavingEstimate estimate, String middle) {
        if (monthlyAmount <= 0) {
            return Optional.empty();
        }
        return Optional.of(new SavingEstimateResult(monthlyAmount, estimate.months(),
                "입주 시 %s%s 계산한 예상 절감액".formatted(middle, particle(middle))));
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
