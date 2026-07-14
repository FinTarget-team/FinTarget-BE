package kr.fintarget.api.domain.policy.dto;

import kr.fintarget.api.domain.policy.entity.UserPolicy;
import java.util.UUID;

public record UserPolicyResponse(
    UUID userPolicyId,
    PolicyResponse policy,
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
