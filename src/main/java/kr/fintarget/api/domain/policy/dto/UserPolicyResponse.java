package kr.fintarget.api.domain.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.policy.entity.UserPolicy;
import java.util.UUID;

public record UserPolicyResponse(
    @Schema(description = "사용자 정책 스크랩 ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID userPolicyId,
    @Schema(description = "스크랩한 정책 정보")
    PolicyResponse policy,
    @Schema(description = "등록 상태", example = "INTEREST", allowableValues = {"APPLIED", "INTEREST", "ABANDONED"})
    String status
) {
    public static UserPolicyResponse from(UserPolicy userPolicy) {
        return new UserPolicyResponse(
            userPolicy.getUserPolicyId(),
            PolicyResponse.from(userPolicy.getPolicy()),
            userPolicy.getStatus().name()
        );
    }
}
