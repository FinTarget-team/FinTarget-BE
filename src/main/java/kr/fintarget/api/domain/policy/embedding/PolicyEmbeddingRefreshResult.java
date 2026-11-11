package kr.fintarget.api.domain.policy.embedding;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 정책 청크 임베딩 갱신 결과 (#181).
 */
public record PolicyEmbeddingRefreshResult(
        @Schema(description = "임베딩 모델", example = "text-embedding-3-small")
        String model,
        @Schema(description = "임베딩 대상 청크 수 (임베딩이 없거나 모델이 다른 청크. all=true면 전체)", example = "15")
        int targetCount,
        @Schema(description = "임베딩을 저장한 청크 수", example = "15")
        int embeddedCount,
        @Schema(description = "호출·저장에 실패한 청크 수 (다음 실행 때 다시 시도)", example = "0")
        int failedCount,
        @Schema(description = "AI를 쓸 수 없어(키 없음, 한도 초과 등) 건너뛴 청크 수", example = "0")
        int skippedCount,
        @Schema(description = "소요 시간(ms)", example = "2300")
        long elapsedMs
) {
}
