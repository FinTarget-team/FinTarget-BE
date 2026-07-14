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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Simulation(UUID userId, Goal goal, UserPolicy userPolicy,
                      Long monthlySaving, LocalDate expectedCompletionDate,
                      LocalDate policyCompletionDate) {
        this.userId = userId;
        this.goal = goal;
        this.userPolicy = userPolicy;
        this.monthlySaving = monthlySaving;
        this.expectedCompletionDate = expectedCompletionDate;
        this.policyCompletionDate = policyCompletionDate;
    }
}
