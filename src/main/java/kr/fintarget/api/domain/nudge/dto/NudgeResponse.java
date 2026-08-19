package kr.fintarget.api.domain.nudge.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.nudge.entity.Nudge;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
@Getter
@Builder
public class NudgeResponse {
    @Schema(description = "넛지 ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private String nudgeId;

    @Schema(description = "넛지 종류", example = "GOAL_DEADLINE", allowableValues = {"GOAL_DEADLINE", "UNUSUAL_SPENDING"})
    private String type;

    @Schema(description = "넛지 알림 문구", example = "목표 '내 집 마련' 마감까지 D-15일 남았습니다.")
    private String message;

    @Schema(description = "UNUSUAL_SPENDING 타입일 때의 관련 지출 카테고리. GOAL_DEADLINE 타입이면 null입니다.", example = "식비", types = {"string", "null"})
    private String relatedCategory;

    @Schema(description = "현재 값이 채워지지 않는 예약 필드로 항상 null입니다.", types = {"integer", "null"})
    private Long savedAmount;

    @Schema(description = "현재 값이 채워지지 않는 예약 필드로 항상 null입니다.", types = {"integer", "null"})
    private Integer dDayShortenIfActed;

    @Schema(description = "넛지 생성 시각")
    private LocalDateTime createdAt;

    @Schema(description = "읽음 여부", example = "false")
    private boolean isRead;
    public static NudgeResponse from(Nudge nudge) {
        return NudgeResponse.builder()
                .nudgeId(nudge.getId())
                .type(nudge.getType())
                .message(nudge.getMessage())
                .relatedCategory(nudge.getRelatedCategory())
                .savedAmount(nudge.getSavedAmount())
                .dDayShortenIfActed(nudge.getDDayShortenIfActed())
                .createdAt(nudge.getCreatedAt())
                .isRead(nudge.isRead())
                .build();
    }
}
