package kr.fintarget.api.domain.ai.client;

import java.util.List;

/**
 * 텍스트 임베딩 클라이언트 (#181). 실패 시 AiClientException(AiUnavailableException 503, AiCallFailedException 502)을 던진다.
 */
public interface AiEmbeddingClient {

    /** 임베딩 차원. policy_chunk_embedding.embedding 컬럼(vector(1536), docs/sql/181_policy_chunk_embedding.sql)과 맞춰야 한다 */
    int DIMENSIONS = 1536;

    /** 임베딩을 만드는 모델명. 저장된 임베딩의 model과 다르면 다시 임베딩한다 */
    String model();

    /**
     * @param inputs 비어 있지 않은 텍스트 목록 (한 번의 요청으로 보낸다)
     * @return inputs와 같은 순서의 DIMENSIONS 차원 벡터
     */
    List<float[]> embed(List<String> inputs);
}
