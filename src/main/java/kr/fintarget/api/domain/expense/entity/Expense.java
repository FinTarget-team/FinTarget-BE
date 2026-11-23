package kr.fintarget.api.domain.expense.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "expense", uniqueConstraints = @UniqueConstraint(
    name = "uk_expense_user_category_amount_spent_at_description",
    columnNames = {"user_id", "category", "amount", "spent_at", "description"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense {

    // 입력 상한 (#219). 넘는 값은 DB 오류(500) 대신 요청 검증에서 400으로 거른다
    public static final long MAX_AMOUNT = 1_000_000_000L;     // 10억 원
    public static final int MAX_CATEGORY_LENGTH = 20;        // 실제 값은 `문화/여가` 등 6개 한글 카테고리
    public static final int MAX_DESCRIPTION_LENGTH = 255;    // description 컬럼 varchar(255)

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "expense_id", columnDefinition = "uuid")
    private UUID expenseId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "description")
    private String description;

    @Column(name = "spent_at", nullable = false)
    private LocalDate spentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Expense(UUID userId, Long amount, String category,
                   String description, LocalDate spentAt) {
        this.userId = userId;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.spentAt = spentAt;
    }
}
