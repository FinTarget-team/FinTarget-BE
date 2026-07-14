package kr.fintarget.api.domain.policy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_policy_id", columnDefinition = "uuid")
    private UUID userPolicyId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserPolicyStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum UserPolicyStatus {
        APPLIED, INTEREST, ABANDONED
    }

    public UserPolicy(UUID userId, Policy policy, UserPolicyStatus status) {
        this.userId = userId;
        this.policy = policy;
        this.status = status;
    }
}
