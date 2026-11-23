package kr.fintarget.api.domain.policy.recommend;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyCondition;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 정책 추천 목록의 정렬 순서 (#220). 응답 형식은 바꾸지 않고 순서만 정한다.
 *
 * <ol>
 *   <li>지금 신청할 수 있는 정책 먼저: OPEN → UNKNOWN(판정 불가) → NOT_OPEN ({@link ApplyPeriodClassifier})</li>
 *   <li>사용자 지역 정책(region 또는 원문 추출 지역이 사용자 지역과 일치)이 전국 정책보다 먼저</li>
 *   <li>조건(소득·주택·대상)이 확인된 정책 먼저 (UNKNOWN이 적은 순, 추출 전 정책은 3개 모두 UNKNOWN)</li>
 *   <li>나머지는 기존 순서 유지 (List.sort는 안정 정렬)</li>
 * </ol>
 */
public final class PolicyRecommendationOrder {

    private PolicyRecommendationOrder() {
    }

    public static List<Policy> sort(List<Policy> policies, String userRegion, LocalDate today) {
        // 비교할 때마다 신청 기간을 다시 해석하지 않도록 정렬 키를 먼저 계산한다
        List<Ranked> ranked = new ArrayList<>(policies.size());
        for (Policy policy : policies) {
            ranked.add(new Ranked(policy, rank(policy, userRegion, today)));
        }
        ranked.sort(Comparator.comparing(Ranked::rank, RANK_ORDER));
        return ranked.stream().map(Ranked::policy).toList();
    }

    /**
     * 정렬 키 (#225 상위 N 선택이 같은 순위 안에서 추가 신호로 다시 정렬할 때 재사용한다)
     *
     * @param localRank    사용자 지역 정책이면 0, 아니면 1
     * @param unknownCount 확인되지 않은 조건 수 (0~3)
     */
    public record Rank(ApplyPeriodStatus applyStatus, int localRank, int unknownCount) {
    }

    /** 정렬 키 비교: 신청 가능 여부 → 사용자 지역 → 확인되지 않은 조건 수 */
    public static final Comparator<Rank> RANK_ORDER = Comparator.comparing(Rank::applyStatus)
            .thenComparingInt(Rank::localRank)
            .thenComparingInt(Rank::unknownCount);

    public static Rank rank(Policy policy, String userRegion, LocalDate today) {
        return new Rank(ApplyPeriodClassifier.classify(policy.getText(), today),
                isLocal(policy, userRegion) ? 0 : 1, unknownCount(policy));
    }

    private record Ranked(Policy policy, Rank rank) {
    }

    // 지역 정책이면서 사용자 지역과 일치. 전국 정책(지역 코드 없음)은 false
    static boolean isLocal(Policy policy, String userRegion) {
        return userRegion != null && !userRegion.isBlank()
                && policy.matchingRegionCodes().contains(userRegion.trim());
    }

    private static int unknownCount(Policy policy) {
        PolicyCondition condition = policy.getCondition();
        return condition == null ? PolicyCondition.MAX_UNKNOWN_COUNT : condition.unknownCount();
    }
}
