package kr.fintarget.api.domain.policy.embedding;

import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiEmbeddingClient;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
import kr.fintarget.api.domain.policy.embedding.PolicyChunkEmbeddingRepository.ChunkEmbedding;
import kr.fintarget.api.domain.policy.embedding.PolicyChunkEmbeddingRepository.EmbeddingTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 정책 청크를 임베딩해 policy_chunk_embedding에 저장한다 (#181). 동기화 후 자동 실행과 관리자 백필이 공유한다.
 * 동시 실행은 호출하는 PolicySyncOrchestrator의 동기화 잠금으로 막는다 (청크 갱신·삭제와 겹치지 않음).
 *
 * <ul>
 *   <li>대상: 임베딩이 없거나 다른 모델로 만든 청크. 원문이 바뀐 청크는 새 chunk_id로 다시 만들어지고
 *       기존 임베딩은 ON DELETE CASCADE로 지워지므로 원문 해시를 따로 비교하지 않는다</li>
 *   <li>batch-size개씩 한 요청으로 보낸다. 호출은 트랜잭션 밖에서 하고, 배치 하나씩 짧은 트랜잭션으로 저장한다</li>
 *   <li>배치 하나가 실패해도 나머지는 계속한다. 실패한 청크는 다음 실행 때 다시 대상이 된다</li>
 *   <li>AI를 쓸 수 없는 상태(키 없음, 호출 한도·잔액 초과, 인증 실패)면 나머지도 실패할 것이므로 바로 멈춘다</li>
 * </ul>
 */
@Slf4j
@Service
public class PolicyChunkEmbeddingService {

    // API 상한은 요청당 입력 2048개·전체 30만 토큰. 청크(800자 + 머리말) 1개가 1.5천 토큰 안팎이라 100개면 상한의 절반 이하
    static final int MAX_BATCH_SIZE = 2048;

    private final PolicyChunkEmbeddingRepository repository;
    private final AiEmbeddingClient embeddingClient;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;

    public PolicyChunkEmbeddingService(PolicyChunkEmbeddingRepository repository, AiEmbeddingClient embeddingClient,
                                       TransactionTemplate transactionTemplate,
                                       @Value("${policy.embedding.batch-size:100}") int batchSize) {
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("policy.embedding.batch-size는 1 이상 " + MAX_BATCH_SIZE + " 이하여야 합니다: " + batchSize);
        }
        this.repository = repository;
        this.embeddingClient = embeddingClient;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
    }

    /** 임베딩이 없거나 모델이 바뀐 청크만 임베딩한다 */
    public PolicyEmbeddingRefreshResult refreshPending() {
        return run(false);
    }

    /** 모든 청크를 다시 임베딩한다 */
    public PolicyEmbeddingRefreshResult refreshAll() {
        return run(true);
    }

    private PolicyEmbeddingRefreshResult run(boolean all) {
        long start = System.currentTimeMillis();
        String model = embeddingClient.model();
        List<EmbeddingTarget> targets = repository.findTargets(all, model);

        int embedded = 0;
        int failed = 0;
        int skipped = 0;
        boolean aiUnavailable = false;
        for (int from = 0; from < targets.size(); from += batchSize) {
            List<EmbeddingTarget> batch = targets.subList(from, Math.min(from + batchSize, targets.size()));
            if (aiUnavailable) {
                skipped += batch.size();
                continue;
            }
            switch (embedBatch(batch, model)) {
                case SAVED -> embedded += batch.size();
                case FAILED -> failed += batch.size();
                case AI_UNAVAILABLE -> {
                    aiUnavailable = true;
                    skipped += batch.size();
                }
            }
        }

        long elapsedMs = System.currentTimeMillis() - start;
        log.info("정책 청크 임베딩 완료 model={} all={} 대상 {}건, 저장 {}건, 실패 {}건, 건너뜀 {}건, 소요 {}ms",
                model, all, targets.size(), embedded, failed, skipped, elapsedMs);
        return new PolicyEmbeddingRefreshResult(model, targets.size(), embedded, failed, skipped, elapsedMs);
    }

    private Outcome embedBatch(List<EmbeddingTarget> batch, String model) {
        List<float[]> vectors;
        try {
            vectors = embeddingClient.embed(batch.stream().map(EmbeddingTarget::content).toList());
        } catch (AiClientException e) {
            // 키 없음·한도 초과·과부하·인증 실패는 다음 배치도 똑같이 실패하므로 바로 멈춘다
            if (e instanceof AiUnavailableException || e.getReason() == AiClientException.Reason.AUTH_FAILED) {
                log.warn("AI를 쓸 수 없어 정책 청크 임베딩을 멈춥니다 reason={}", e.getReason());
                return Outcome.AI_UNAVAILABLE;
            }
            log.warn("정책 청크 임베딩 호출 실패 청크 {}건 reason={}", batch.size(), e.getReason());
            return Outcome.FAILED;
        }

        List<ChunkEmbedding> embeddings = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            embeddings.add(new ChunkEmbedding(batch.get(i).chunkId(), vectors.get(i)));
        }
        try {
            transactionTemplate.executeWithoutResult(status ->
                    repository.upsertAll(embeddings, model, LocalDateTime.now()));
            return Outcome.SAVED;
        } catch (DataAccessException e) {
            // 테이블이 없거나(SQL 미실행), 저장 중 청크가 지워진 경우(FK) 등. 배치 단위로 롤백되고 다음 실행 때 다시 시도
            log.warn("정책 청크 임베딩 저장 실패 청크 {}건 error={}", batch.size(), e.getClass().getSimpleName());
            return Outcome.FAILED;
        }
    }

    private enum Outcome { SAVED, FAILED, AI_UNAVAILABLE }
}
