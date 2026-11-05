package kr.fintarget.api.domain.policy.search;

import kr.fintarget.api.domain.policy.entity.Policy;

import java.util.UUID;

/**
 * 검색된 정책 1건 (#182). 서비스 계층 반환값이라 청크 본문(content)을 들고 있다 —
 * 통합 AI 답변(#183)이 이 본문을 [1], [2] 번호를 붙여 프롬프트에 넣고, 모델이 답한 번호를
 * 목록 순서로 정책 ID로 되돌린다. 외부 응답(PolicySearchResponse)에는 본문을 넣지 않는다.
 *
 * @param sectionLabel 가장 가까웠던 청크의 섹션 라벨
 * @param content      가장 가까웠던 청크의 본문
 * @param distance     코사인 거리 (0에 가까울수록 유사)
 */
public record PolicySearchHit(Policy policy, UUID chunkId, String sectionLabel, String content, double distance) {

    /** 외부에 노출하는 유사도 점수. 거리를 그대로 주면 "작을수록 좋음"이라 뒤집어 해석할 위험이 있다 */
    public double score() {
        return 1.0 - distance;
    }
}
