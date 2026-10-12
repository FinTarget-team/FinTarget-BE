package kr.fintarget.api.domain.policy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "policy_id", columnDefinition = "uuid")
    private UUID policyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Column(name = "income_limit")
    private Long incomeLimit;

    @Column(name = "benefit_amount")
    private Long benefitAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false)
    private PolicyType policyType;

    @Column(name = "region")
    private String region;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "source")
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Policy create(String name, String description, Integer minAge, Integer maxAge,
                                Long incomeLimit, Long benefitAmount, PolicyType policyType, String region,
                                String externalId, String source) {
        Policy policy = new Policy();
        policy.name = name;
        policy.description = description;
        policy.minAge = minAge;
        policy.maxAge = maxAge;
        policy.incomeLimit = incomeLimit;
        policy.benefitAmount = benefitAmount;
        policy.policyType = policyType;
        policy.region = region;
        policy.externalId = externalId;
        policy.source = source;
        return policy;
    }

    // 외부 API에서 다시 받아온 값으로 내용을 갱신한다 (externalId/source는 식별자라 유지)
    public void updateFrom(Policy fetched) {
        this.name = fetched.name;
        this.description = fetched.description;
        this.minAge = fetched.minAge;
        this.maxAge = fetched.maxAge;
        this.incomeLimit = fetched.incomeLimit;
        this.benefitAmount = fetched.benefitAmount;
        this.policyType = fetched.policyType;
        this.region = fetched.region;
    }
}