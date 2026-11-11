package kr.fintarget.mockbank.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 결제 발생 폼. 카테고리 값 목록과 미래 날짜 검사는 한국 시간 기준 "오늘"이 필요해 컨트롤러에서 한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class TransactionForm {

    public static final String UUID_REGEX =
            "^\\s*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\s*$";

    @NotBlank(message = "핀타겟 사용자 UUID를 입력해주세요.")
    @Pattern(regexp = UUID_REGEX, message = "핀타겟 사용자 UUID 형식이 올바르지 않습니다.")
    private String userId;

    @NotNull(message = "금액을 입력해주세요.")
    @Positive(message = "금액은 0보다 커야 합니다.")
    private Long amount;

    @NotBlank(message = "카테고리를 선택해주세요.")
    private String category;

    @Size(max = 100, message = "설명은 100자 이하로 입력해주세요.")
    private String description;

    @NotNull(message = "결제 날짜를 입력해주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate spentAt;
}
