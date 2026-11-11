package kr.fintarget.mockbank.transaction.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = @Index(name = "idx_transactions_user_spent_at", columnList = "fin_target_user_id, spent_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "fin_target_user_id", nullable = false)
    private UUID finTargetUserId;

    @Column(nullable = false)
    private Long amount;

    // 메인 서버로 그대로 전송되는 카테고리 문자열 (예: 식비)
    @Column(nullable = false)
    private String category;

    private String description;

    @Column(name = "spent_at", nullable = false)
    private LocalDate spentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "webhook_status", nullable = false)
    private WebhookStatus webhookStatus;

    public Transaction(UUID finTargetUserId, Long amount, String category, String description, LocalDate spentAt) {
        this.finTargetUserId = finTargetUserId;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.spentAt = spentAt;
        this.webhookStatus = WebhookStatus.PENDING;
    }

    public void changeWebhookStatus(WebhookStatus webhookStatus) {
        this.webhookStatus = webhookStatus;
    }
}
