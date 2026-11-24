package kr.fintarget.api.domain.policy.condition;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 조건 추출 실행 결과 (#220). 운영 DB에 들어가지 않고도 백필 결과를 확인할 수 있도록 건수를 돌려준다.
 */
public record ConditionExtractionResult(
        @Schema(description = "추출 대상 정책 수 (원문·프롬프트 버전이 바뀌었거나 아직 추출하지 않은 정책. all=true면 전체)", example = "900")
        int targetCount,
        @Schema(description = "추출해 저장한 정책 수 (응답 해석에 실패해 모든 조건을 UNKNOWN으로 저장한 정책 포함)", example = "895")
        int savedCount,
        @Schema(description = "저장한 정책 중 AI 응답을 해석하지 못해 모든 조건을 UNKNOWN으로 저장한 정책 수", example = "2")
        int unparsedCount,
        @Schema(description = "AI 호출에 실패해 저장하지 않은 정책 수. 다음 동기화 때 다시 시도합니다.", example = "3")
        int failedCount,
        @Schema(description = "AI를 쓸 수 없는 상태(키 없음, 한도 초과)여서 중간에 멈춰 시도하지 않은 정책 수", example = "0")
        int skippedCount,
        @Schema(description = "소요 시간(ms)", example = "1500000")
        long elapsedMs
) {
}
