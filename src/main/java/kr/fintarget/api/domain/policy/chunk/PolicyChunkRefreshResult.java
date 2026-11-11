package kr.fintarget.api.domain.policy.chunk;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 정책 청크 갱신 결과 (#180).
 */
public record PolicyChunkRefreshResult(
        @Schema(description = "검사한 정책 수 (전체 정책)", example = "864")
        int policyCount,
        @Schema(description = "청크를 새로 만든 정책 수 (청크가 없거나 원문·분할 규칙이 바뀐 정책. all=true면 전체)", example = "12")
        int regeneratedCount,
        @Schema(description = "원문이 같아 그대로 둔 정책 수", example = "852")
        int skippedCount,
        @Schema(description = "새로 저장한 청크 수", example = "15")
        int chunkCount,
        @Schema(description = "정책 테이블에 없는 정책의 청크를 지운 건수", example = "0")
        int orphanDeletedCount,
        @Schema(description = "소요 시간(ms)", example = "850")
        long elapsedMs
) {
}
