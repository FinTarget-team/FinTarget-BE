package kr.fintarget.api.domain.policy.recommend;

import kr.fintarget.api.domain.policy.condition.BenefitKind;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyCondition;
import kr.fintarget.api.domain.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 규칙 기반 추천 상위 N 선택 (#225). LLM을 호출하지 않는다.
 *
 * <ol>
 *   <li>정책명에 제외 키워드(상담·교육·행사 등)가 있으면 뺀다 (policy.recommend.exclude-keywords)</li>
 *   <li>경제적 혜택 유형(benefitKind)으로 후보 순위를 나눈다: 1순위 MONEY·COST_SAVING, 2순위 LOAN(1순위로 N을 못 채울 때만).
 *       NON_FINANCIAL·NOT_FOR_INDIVIDUAL·UNKNOWN(추출 전 포함)은 넣지 않는다. 그래도 모자라면 있는 만큼만 돌려준다</li>
 *   <li>같은 후보 순위 안에서는 신청 가능 여부 → 시·군·구 한정이 아닌 정책(localScope false·null) → 사용자 지역 정책 → 조건 확인 정도
 *       → 현금 혜택(시뮬레이션 반영 혜택)이 있는 정책 → 신청 마감이 가까운 정책 → 정책 ID 순으로 정렬한다 (같은 입력이면 항상 같은 결과).
 *       온보딩 지역이 시·도 단위라 다른 구·군 한정 정책도 "사용자 지역 정책"으로 잡히므로, 시·군·구 한정 여부를 지역 비교보다 앞에 둔다.
 *       제외하지는 않고 뒤로만 보낸다</li>
 *   <li>비슷한 이름({@link SimilarPolicyName})은 최대 2건. 빠진 자리는 같은 후보 순위의 정렬 상위 30건 안에서 채우고,
 *       그래도 못 채우면 제한 없이 정렬 순서대로 채운다</li>
 * </ol>
 * 정책 유형 필터가 있는 요청도 같은 규칙을 쓴다 (입력이 이미 그 유형으로 걸러져 있다).
 */
@Component
public class RuleBasedPolicyRecommendationSelector implements PolicyRecommendationSelector {

    static final int SIMILAR_NAME_LIMIT = 2;
    static final int REFILL_WINDOW = 30;

    private final int limit;
    private final List<String> excludeKeywords;

    public RuleBasedPolicyRecommendationSelector(
            @Value("${policy.recommend.limit:10}") int limit,
            @Value("${policy.recommend.exclude-keywords:상담,콘서트,토크,강연,특강,교육,아카데미,캠프,축제,박람회,설명회,멘토링,네트워킹,행사}")
            List<String> excludeKeywords) {
        if (limit < 1) {
            throw new IllegalArgumentException("policy.recommend.limit는 1 이상이어야 합니다: " + limit);
        }
        this.limit = limit;
        this.excludeKeywords = excludeKeywords.stream().map(String::trim).filter(keyword -> !keyword.isEmpty()).toList();
    }

    @Override
    public List<Policy> select(User user, List<Policy> sortedCandidates, LocalDate today) {
        String userRegion = user.getRegion();
        List<Candidate> primary = new ArrayList<>();
        List<Candidate> loans = new ArrayList<>();
        for (Policy policy : sortedCandidates) {
            if (hasExcludedKeyword(policy.getName())) {
                continue;
            }
            BenefitKind kind = benefitKind(policy);
            if (kind.isEconomicBenefit()) {
                primary.add(candidate(policy, userRegion, today));
            } else if (kind == BenefitKind.LOAN) {
                loans.add(candidate(policy, userRegion, today));
            }
        }

        Set<Policy> selected = new LinkedHashSet<>();
        Map<String, Integer> nameCounts = new HashMap<>();
        fill(sorted(primary), selected, nameCounts);
        fill(sorted(loans), selected, nameCounts);
        return List.copyOf(selected);
    }

    /** 정책명에 제외 키워드가 있는지 */
    boolean hasExcludedKeyword(String name) {
        return name != null && excludeKeywords.stream().anyMatch(name::contains);
    }

    // 정렬 상위 30건 안에서 비슷한 이름 제한을 지키며 채우고, 모자라면 제한 없이 정렬 순서대로 채운다
    private void fill(List<Candidate> tier, Set<Policy> selected, Map<String, Integer> nameCounts) {
        if (selected.size() >= limit) {
            return;
        }
        List<Candidate> window = tier.subList(0, Math.min(REFILL_WINDOW, tier.size()));
        for (Candidate candidate : window) {
            if (selected.size() >= limit) {
                return;
            }
            if (nameCounts.getOrDefault(candidate.nameKey(), 0) < SIMILAR_NAME_LIMIT) {
                selected.add(candidate.policy());
                nameCounts.merge(candidate.nameKey(), 1, Integer::sum);
            }
        }
        for (Candidate candidate : tier) {
            if (selected.size() >= limit) {
                return;
            }
            if (selected.add(candidate.policy())) {
                nameCounts.merge(candidate.nameKey(), 1, Integer::sum);
            }
        }
    }

    private static List<Candidate> sorted(List<Candidate> candidates) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing((Candidate candidate) -> candidate.rank().applyStatus())
                .thenComparingInt(candidate -> candidate.localScope() ? 1 : 0)
                .thenComparingInt(candidate -> candidate.rank().localRank())
                .thenComparingInt(candidate -> candidate.rank().unknownCount())
                .thenComparingInt(candidate -> candidate.hasCashBenefit() ? 0 : 1)
                .thenComparing(Candidate::deadline, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(candidate -> String.valueOf(candidate.policy().getPolicyId())));
        return sorted;
    }

    private static Candidate candidate(Policy policy, String userRegion, LocalDate today) {
        PolicyCondition condition = policy.getCondition();
        boolean localScope = condition != null && Boolean.TRUE.equals(condition.getLocalScope());
        return new Candidate(policy, PolicyRecommendationOrder.rank(policy, userRegion, today), localScope,
                policy.hasBenefitForSimulation(), ApplyPeriodClassifier.deadline(policy.getText(), today),
                SimilarPolicyName.key(policy.getName()));
    }

    private static BenefitKind benefitKind(Policy policy) {
        PolicyCondition condition = policy.getCondition();
        return condition == null ? BenefitKind.UNKNOWN : condition.benefitKindOrUnknown();
    }

    private record Candidate(Policy policy, PolicyRecommendationOrder.Rank rank, boolean localScope, boolean hasCashBenefit,
                             LocalDate deadline, String nameKey) {
    }
}
