package kr.fintarget.api.domain.policy.dto;

import kr.fintarget.api.domain.policy.entity.Policy;
import java.util.UUID;

public record PolicyResponse(
    UUID policyId,
    String name,
    String description,
    Integer minAge,
    Integer maxAge,
    Long incomeLimit,
    Long benefitAmount,
    String policyType
) {
    public static PolicyResponse from(Policy policy) {
        return new PolicyResponse(
            policy.getPolicyId(),
            policy.getName(),
            policy.getDescription(),
            policy.getMinAge(),
            policy.getMaxAge(),
            policy.getIncomeLimit(),
            policy.getBenefitAmount(),
            policy.getPolicyType()
        );
    }
}
