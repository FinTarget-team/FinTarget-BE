package kr.fintarget.api.domain.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record UserPolicyCreateRequest(
    @Schema(description = "등록할 정책 ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID policyId,
    @Schema(description = "등록 상태", example = "INTEREST", allowableValues = {"APPLIED", "INTEREST", "ABANDONED"})
    String status
) {}
