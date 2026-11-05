package kr.fintarget.api.domain.ai.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 사용자별·기능별 하루 AI 호출 횟수 (#183). 날짜는 KST 기준이다.
 * 횟수 변경은 {@link AiDailyUsageRepository}의 조건부 UPDATE로만 하고, 엔티티 값을 고쳐 저장하지 않는다 (동시 요청에서 횟수가 덮어써지지 않도록).
 * 지난 날짜의 행은 쓰이지 않으며 지우지 않아도 동작에는 영향이 없다.
 */
@Entity
@Table(name = "ai_daily_usage", uniqueConstraints = @UniqueConstraint(
        name = "uk_ai_daily_usage_user_date_feature", columnNames = {"user_id", "usage_date", "feature"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiDailyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "usage_id", columnDefinition = "uuid")
    private UUID usageId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 30)
    private AiUsageFeature feature;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    AiDailyUsage(String userId, LocalDate usageDate, AiUsageFeature feature, int usedCount) {
        this.userId = userId;
        this.usageDate = usageDate;
        this.feature = feature;
        this.usedCount = usedCount;
    }
}
