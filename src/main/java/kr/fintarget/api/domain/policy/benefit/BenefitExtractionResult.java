package kr.fintarget.api.domain.policy.benefit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 혜택 추출 실행 결과 (#162). 운영 DB에 들어가지 않고도 백필 결과를 확인할 수 있도록 유형별 건수를 돌려준다.
 */
public record BenefitExtractionResult(
        @Schema(description = "추출 대상 정책 수 (원문·프롬프트 버전이 바뀌었거나 아직 추출하지 않은 정책. all=true면 전체)", example = "220")
        int targetCount,
        @Schema(description = "추출해 저장한 정책의 유형별 건수. CASH만 시뮬레이션에 반영됩니다.",
                example = "{\"CASH\": 80, \"LOAN\": 40, \"SERVICE\": 90, \"UNKNOWN\": 10}")
        Map<BenefitType, Integer> savedByType,
        @Schema(description = "AI 호출·응답 해석에 실패해 기존 값을 유지한 정책 수. 다음 동기화 때 다시 시도합니다.", example = "0")
        int failedCount,
        @Schema(description = "AI를 쓸 수 없는 상태(키 없음, 한도 초과)여서 중간에 멈춰 시도하지 않은 정책 수", example = "0")
        int skippedCount,
        @Schema(description = "소요 시간(ms)", example = "95000")
        long elapsedMs
) {
}
