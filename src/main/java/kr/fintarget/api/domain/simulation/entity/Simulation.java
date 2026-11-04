package kr.fintarget.api.domain.simulation.entity;

import jakarta.persistence.*;
import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.policy.entity.UserPolicy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "simulation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Simulation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "simulation_id", columnDefinition = "uuid")
    private UUID simulationId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_policy_id", nullable = true)
    private UserPolicy userPolicy;

    @Column(name = "monthly_saving", nullable = false)
    private Long monthlySaving;

    @Column(name = "expected_completion_date", nullable = false)
    private LocalDate expectedCompletionDate;

    @Column(name = "policy_completion_date")
    private LocalDate policyCompletionDate;

    @Column(name = "optimistic_completion_date")
    private LocalDate optimisticCompletionDate;

    @Column(name = "pessimistic_completion_date")
    private LocalDate pessimisticCompletionDate;

    @Column(name = "success_probability")
    private Double successProbability;

    // 정책 혜택을 반영한 달성 확률 (#163). 정책을 안 골랐거나 혜택 금액이 없으면 null
    @Column(name = "policy_success_probability")
    private Double policySuccessProbability;

    // 실행 당시 목표 값 (#186). 목표를 나중에 수정해도 이 기록이 어떤 조건에서 나온 결과인지 알 수 있게 복사해 둔다.
    // 이 컬럼이 생기기 전에 저장된 기록은 null이다
    @Column(name = "goal_title")
    private String goalTitle;

    @Column(name = "goal_target_amount")
    private Long goalTargetAmount;

    @Column(name = "goal_current_amount")
    private Long goalCurrentAmount;

    @Column(name = "goal_deadline")
    private LocalDate goalDeadline;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Simulation(UUID userId, Goal goal, UserPolicy userPolicy,
                      Long monthlySaving, LocalDate expectedCompletionDate,
                      LocalDate policyCompletionDate, LocalDate optimisticCompletionDate,
                      LocalDate pessimisticCompletionDate, Double successProbability,
                      Double policySuccessProbability) {
        this.userId = userId;
        this.goal = goal;
        this.goalTitle = goal.getTitle();
        this.goalTargetAmount = goal.getTargetAmount();
        this.goalCurrentAmount = goal.getCurrentAmount();
        this.goalDeadline = goal.getDeadline();
        this.userPolicy = userPolicy;
        this.monthlySaving = monthlySaving;
        this.expectedCompletionDate = expectedCompletionDate;
        this.policyCompletionDate = policyCompletionDate;
        this.optimisticCompletionDate = optimisticCompletionDate;
        this.pessimisticCompletionDate = pessimisticCompletionDate;
        this.successProbability = successProbability;
        this.policySuccessProbability = policySuccessProbability;
    }
}
