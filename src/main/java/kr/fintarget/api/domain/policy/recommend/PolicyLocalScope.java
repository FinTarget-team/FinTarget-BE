package kr.fintarget.api.domain.policy.recommend;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyCondition;

/**
 * 시·군·구 거주자로 한정된 정책인지 (#225 판정 기준과 같음).
 * 온통청년 zipCd 정형값(Policy.regionLocalScope)이 있으면 그 값을, 없으면 AI 추출값(condition.localScope)을 쓴다.
 * 온보딩 지역이 시·도 단위라 사용자가 그 시·군·구에 사는지 알 수 없다.
 */
public final class PolicyLocalScope {

    private PolicyLocalScope() {
    }

    public static boolean isLocal(Policy policy) {
        Boolean structured = policy.getRegionLocalScope();
        if (structured != null) {
            return structured;
        }
        PolicyCondition condition = policy.getCondition();
        return condition != null && Boolean.TRUE.equals(condition.getLocalScope());
    }
}
