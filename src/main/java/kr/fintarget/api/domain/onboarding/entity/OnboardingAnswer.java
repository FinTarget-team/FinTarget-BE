package kr.fintarget.api.domain.onboarding.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
// 사용자당 단계별 답변은 1건만 둔다. 다시 제출하면 값을 갱신한다 (#195)
@Table(name = "onboarding_answer", uniqueConstraints = @UniqueConstraint(
        name = "uk_onboarding_answer_user_step", columnNames = {"user_id", "step"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnboardingAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "onboarding_id")
    private String id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "step", nullable = false)
    private Integer step;

    @Column(name = "value", nullable = false)
    private String value;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public OnboardingAnswer(UUID userId, Integer step, String value) {
        this.userId = userId;
        this.step = step;
        this.value = value;
    }

    public void updateValue(String value) {
        this.value = value;
    }
}