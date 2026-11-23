package kr.fintarget.api.domain.policy.recommend;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.user.entity.User;

import java.time.LocalDate;
import java.util.List;

/**
 * 정렬된 추천 후보에서 사용자에게 보여줄 정책을 고른다 (#225).
 * 지금은 규칙 기반 구현({@link RuleBasedPolicyRecommendationSelector}) 하나이며,
 * 나중에 LLM 리랭킹(상위 후보 중 N건 선택)으로 바꾸거나 규칙 구현 뒤에 덧붙일 수 있게 인터페이스로 둔다.
 * 사용자 요청 중에 호출되므로 구현은 응답 시간을 고려해야 한다.
 */
public interface PolicyRecommendationSelector {

    /**
     * @param user             추천 대상 사용자
     * @param sortedCandidates #220 필터와 정렬({@link PolicyRecommendationOrder})을 거친 후보
     * @param today            오늘 날짜 (KST)
     * @return 보여줄 정책 (순서 그대로 응답에 쓴다)
     */
    List<Policy> select(User user, List<Policy> sortedCandidates, LocalDate today);
}
