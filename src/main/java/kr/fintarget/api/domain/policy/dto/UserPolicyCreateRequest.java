package kr.fintarget.api.domain.policy.dto;

import java.util.UUID;

public record UserPolicyCreateRequest(
    UUID policyId,
    String status
) {}
