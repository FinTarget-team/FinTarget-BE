package kr.fintarget.api.domain.policy.service;

import io.swagger.v3.oas.annotations.media.Schema;

public record PolicySyncResult(
        @Schema(description = "온통청년(청년정책) API에서 이번 실행으로 새로 저장된 정책 건수. 이미 저장된 정책은 중복 저장하지 않으므로 0일 수 있습니다.", example = "12")
        int youthCenterSavedCount,
        @Schema(description = "온통청년 API 동기화 실패 여부. true여도 HTTP 상태 코드는 200입니다.", example = "false")
        boolean youthCenterFailed,
        @Schema(description = "KINFA(서민금융상품) API에서 이번 실행으로 새로 저장된 정책 건수. 이미 저장된 정책은 중복 저장하지 않으므로 0일 수 있습니다.", example = "5")
        int kinfaSavedCount,
        @Schema(description = "KINFA API 동기화 실패 여부. true여도 HTTP 상태 코드는 200입니다.", example = "false")
        boolean kinfaFailed,
        @Schema(description = "두 소스 동기화에 소요된 총 시간(ms)", example = "3200")
        long elapsedMs
) {
}
