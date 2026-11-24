package kr.fintarget.api.domain.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 비상용 시연 계정 요약 (#239). 이메일·소셜 ID·토큰 같은 민감 정보는 넣지 않는다 */
@Schema(description = "시연 계정 요약")
public record DemoUserSummary(
        @Schema(description = "사용자 ID (초기화 API의 userId)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") String userId,
        @Schema(description = "닉네임. 초기화 직후나 온보딩 1단계 전에는 null", example = "지원", types = {"string", "null"}) String name,
        @Schema(description = "로그인 방식", example = "KAKAO", allowableValues = {"KAKAO", "NAVER"}) String provider,
        @Schema(description = "온보딩 전체 완료 여부", example = "false") boolean onboardingCompleted
) {
}
