package kr.fintarget.api.domain.policy.search;

import java.util.List;
import java.util.UUID;

/**
 * 후보 정책 안에서 질문 벡터에 가까운 청크를 거리순으로 가져온다 (#182).
 * 운영 구현은 {@link JdbcPolicyChunkSearchRepository}(pgvector <=>)이고, 검색 평가(#184)는 스냅샷 벡터로
 * 같은 순서를 계산하는 메모리 구현으로 바꿔 끼운다. 정책별 묶기·유사도 하한·k는 구현이 아니라 {@link PolicySearchService}가 한다.
 */
public interface PolicyChunkSearchRepository {

    /**
     * @param policyIds      사용자 조건에 맞는 정책 ID. 비어 있으면 조회하지 않고 빈 목록
     * @param questionVector 질문 임베딩
     * @param limit          가져올 청크 수. 한 정책이 청크 여러 개를 차지하므로 원하는 정책 수보다 넉넉해야 한다
     * @return 코사인 거리 오름차순
     */
    List<ChunkMatch> search(List<UUID> policyIds, float[] questionVector, int limit);

    /**
     * @param distance 코사인 거리 (0에 가까울수록 유사)
     */
    record ChunkMatch(UUID policyId, UUID chunkId, String sectionLabel, String content, double distance) {
    }
}
