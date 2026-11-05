package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.user.code.Region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 검색 결과가 평가 프로필 조건(나이·소득·지역)에 맞는지 평가기가 따로 계산한다 (#184 조건 위반 건수).
 * 운영 필터(findMatchingPolicies + Policy.appliesToRegion)와 같은 규칙을 코드를 공유하지 않고 다시 적었다.
 * 운영 필터가 바뀌어 결과가 달라지면 조건 위반으로 드러난다.
 *
 * <ul>
 *   <li>나이: 프로필 나이 구간 [lower, upper]와 정책 [minAge, maxAge]가 겹치면 통과 (값이 없으면 제한 없음)</li>
 *   <li>소득: incomeLimit이 없거나 프로필 소득 이상이면 통과</li>
 *   <li>지역: 지역 값이 없거나, 코드가 아닌 값이 하나라도 섞이면 전국(regionFallback). 아니면 프로필 지역이 목록에 있어야 통과</li>
 * </ul>
 */
public final class EligibilityCheck {

    private EligibilityCheck() {
    }

    /**
     * @param ageLower 프로필 나이 구간 하한 (User.ageRange().lower())
     */
    public static Result check(int ageLower, int ageUpper, long income, String userRegion,
                               Integer minAge, Integer maxAge, Long incomeLimit, String policyRegion) {
        List<String> violations = new ArrayList<>();
        if (minAge != null && minAge > ageUpper) {
            violations.add("최소 나이 " + minAge + " > " + ageUpper);
        }
        if (maxAge != null && maxAge < ageLower) {
            violations.add("최대 나이 " + maxAge + " < " + ageLower);
        }
        if (incomeLimit != null && incomeLimit < income) {
            violations.add("소득 한도 " + incomeLimit + " < " + income);
        }
        boolean regionFallback = false;
        if (policyRegion != null && !policyRegion.isBlank()) {
            List<String> codes = Arrays.stream(policyRegion.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (codes.stream().anyMatch(code -> Region.fromCode(code) == null)) {
                regionFallback = true;
            } else if (userRegion == null || !codes.contains(userRegion.trim())) {
                violations.add("지역 " + policyRegion + "에 " + userRegion + " 없음");
            }
        }
        return new Result(violations, regionFallback);
    }

    /**
     * @param regionFallback 지역 값에 코드가 아닌 값이 섞여 전국으로 처리됨 (운영과 같은 규칙. 따로 집계한다)
     */
    public record Result(List<String> violations, boolean regionFallback) {

        public Result {
            violations = List.copyOf(violations);
        }

        public boolean eligible() {
            return violations.isEmpty();
        }
    }
}
