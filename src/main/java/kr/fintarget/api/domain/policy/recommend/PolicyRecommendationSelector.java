package kr.fintarget.api.domain.policy.recommend;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.user.entity.User;

import java.time.LocalDate;
import java.util.List;

/**
 * 정렬된 추천 후보에서 사용자에게 보여줄 정책을 고른다 (#225).
 * 규칙 기반 구현({@link RuleBasedPolicyRecommendationSelector})과, 규칙 후보 중 LLM이 최종 N건을 고르는 구현
 * ({@link LlmPolicyRecommendationSelector}, #229, 기본으로 주입됨)이 있다.
 * 사용자 요청 중에 호출되므로 구현은 응답 시간을 고려해야 한다.
 */
public interface PolicyRecommendationSelector {

    /**
     * 전체 유형 추천 목록 (정책 유형 필터 없음).
     *
     * @param user             추천 대상 사용자
     * @param sortedCandidates #220 필터와 정렬({@link PolicyRecommendationOrder})을 거친 후보
     * @param today            오늘 날짜 (KST)
     * @return 보여줄 정책 (순서 그대로 응답에 쓴다)
     */
    List<Policy> select(User user, List<Policy> sortedCandidates, LocalDate today);

    /**
     * 정책 유형 필터가 있는 추천 목록. 기본은 {@link #select}와 같다.
     * LLM 리랭킹(#229)은 사용자당 전체 목록 1건만 캐시하므로 유형별 목록은 규칙 결과를 쓴다.
     */
    default List<Policy> selectWithinType(User user, List<Policy> sortedCandidates, LocalDate today) {
        return select(user, sortedCandidates, today);
    }
}
