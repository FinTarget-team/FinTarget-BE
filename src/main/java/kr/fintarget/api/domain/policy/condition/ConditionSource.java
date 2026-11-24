package kr.fintarget.api.domain.policy.condition;

import kr.fintarget.api.domain.policy.document.PolicySections;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 조건 추출에 쓰는 정책 원문 (#220). AI 입력과 원문 해시에 같은 텍스트를 쓴다.
 *
 * <p>자격·대상 판단에 필요한 필드를 우선순위 순서로 넣는다. 필드마다 {@link #MAX_FIELD_LENGTH}자,
 * 전체 {@link #MAX_TOTAL_LENGTH}자를 넘으면 뒤쪽(우선순위 낮은) 필드부터 자른다.
 * 신청 방법·문의처는 지역·대상 판단 근거(기관명, 시청 방문 등)가 들어 있는 경우가 많아 마지막에 넣는다.
 * 제출 서류·심사 방법·링크는 조건 판단에 쓰지 않고 토큰만 늘려 넣지 않는다.
 *
 * @param fields 라벨과 값 (값이 있는 것만, 우선순위 순서)
 */
public record ConditionSource(String source, String name, List<Field> fields) {

    static final int MAX_FIELD_LENGTH = 1_500;
    static final int MAX_TOTAL_LENGTH = 6_000;

    public record Field(String label, String value) {
    }

    public static ConditionSource of(Policy policy) {
        PolicyText text = policy.getText();
        List<Field> fields = new ArrayList<>();
        add(fields, "등록된 신청 가능 지역", PolicySections.regionLabel(policy));
        add(fields, PolicySections.isKinfa(policy) ? "지원 대상 상세 조건" : "정책 설명", policy.getDescription());
        if (text != null) {
            add(fields, "자격 조건", text.getEligibilityDetail());
            add(fields, "참여 제한 대상", text.getExclusionCriteria());
            add(fields, "소득 조건", text.getIncomeCondition());
            add(fields, "나이 조건", text.getAgeCondition());
            add(fields, "지원 내용", text.getSupportContent());
            add(fields, "기타 참고 사항", text.getEtcNote());
            add(fields, "신청 방법", text.getApplyMethod());
            add(fields, "문의처", text.getContactInfo());
        }
        return new ConditionSource(policy.getSource(), policy.getName(), List.copyOf(fields));
    }

    /** AI에 보내는 원문. 필드·전체 길이 상한을 적용한다 */
    public String toPromptText() {
        StringBuilder text = new StringBuilder("[정책명]\n").append(name == null ? "" : name.strip());
        for (Field field : fields) {
            String value = truncate(field.value().strip(), MAX_FIELD_LENGTH);
            String block = "\n\n[" + field.label() + "]\n" + value;
            if (text.length() + block.length() > MAX_TOTAL_LENGTH) {
                int remaining = MAX_TOTAL_LENGTH - text.length() - field.label().length() - 5;
                if (remaining > 100) {
                    text.append("\n\n[").append(field.label()).append("]\n").append(truncate(value, remaining));
                }
                break;
            }
            text.append(block);
        }
        return text.toString();
    }

    /** AI 입력 원문의 SHA-256 (hex). 저장된 값과 다르면 원문이 바뀐 것이라 다시 추출한다 */
    public String contentHash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(toPromptText().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    /** 정책명 외에 판단할 원문이 하나라도 있는지 (등록된 지역 표시는 원문이 아니라 세지 않는다) */
    public boolean hasContent() {
        return fields.size() > 1;
    }

    private static void add(List<Field> fields, String label, String value) {
        if (value != null && !value.isBlank()) {
            fields.add(new Field(label, value));
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
