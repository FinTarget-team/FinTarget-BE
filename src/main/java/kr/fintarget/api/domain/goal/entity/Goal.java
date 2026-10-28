package kr.fintarget.api.domain.goal.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "goal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Goal {

    // 목표 금액 상한(50억 원). 오입력(0 추가 등) 방지 목적의 안전장치
    public static final long MAX_TARGET_AMOUNT = 5_000_000_000L;
    static final String DEFAULT_TITLE = "내 목표";
    static final int DEFAULT_DEADLINE_YEARS = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "goal_id", columnDefinition = "uuid")
    private UUID goalId;

    @Column(name = "user_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "target_amount", nullable = false)
    private Long targetAmount;

    @Column(name = "current_amount", nullable = false)
    private Long currentAmount;

    @Column(name = "deadline", nullable = false)
    private LocalDate deadline;

    // 온보딩 자동 생성 시 임의로 채운 마감일인지 여부. 사용자가 마감일을 바꾸면 false
    @Column(name = "deadline_defaulted", nullable = false, columnDefinition = "boolean default false")
    private boolean deadlineDefaulted;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GoalStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum GoalStatus {
        ACTIVE, COMPLETED, ABANDONED
    }

    public Goal(UUID userId, String title, Long targetAmount,
                Long currentAmount, LocalDate deadline) {
        this.userId = userId;
        this.title = title;
        this.targetAmount = targetAmount;
        this.currentAmount = currentAmount;
        this.deadline = deadline;
        this.status = GoalStatus.ACTIVE;
        this.deadlineDefaulted = false;
    }

    // 온보딩 마지막 단계(목표 금액, 현재 7단계)에서 받은 목표 금액만으로 목표를 만든다. 나머지 값은 Goal 화면에서 수정
    public static Goal createFromOnboarding(UUID userId, long targetAmount) {
        Goal goal = new Goal(userId, DEFAULT_TITLE, targetAmount, 0L,
                LocalDate.now().plusYears(DEFAULT_DEADLINE_YEARS));
        goal.deadlineDefaulted = true;
        return goal;
    }

    public static void validateTargetAmount(Long targetAmount) {
        if (targetAmount == null || targetAmount <= 0) {
            throw new IllegalArgumentException("목표 금액은 0보다 커야 합니다");
        }
        if (targetAmount > MAX_TARGET_AMOUNT) {
            throw new IllegalArgumentException("목표 금액은 50억 원 이하로 입력해주세요");
        }
    }

    public void update(String title, Long targetAmount,
                       Long currentAmount, LocalDate deadline) {
        if (!Objects.equals(this.deadline, deadline)) {
            this.deadlineDefaulted = false;
        }
        this.title = title;
        this.targetAmount = targetAmount;
        this.currentAmount = currentAmount;
        this.deadline = deadline;
    }

    public void updateTargetAmount(long targetAmount) {
        this.targetAmount = targetAmount;
    }
}
