package kr.fintarget.api.domain.policy.benefit;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 혜택 추출에 쓰는 정책 원문 (#162). AI 입력과 근거 문장 검증에 같은 텍스트를 쓴다.
 * 금액이 나오는 필드만 넣는다 (신청 방법·서류·문의처는 금액 판단에 쓰지 않고 토큰만 늘린다).
 */
public record BenefitSource(String source, String name, String supportContent, String description,
                            String businessPeriod, String etcNote) {

    public static BenefitSource of(Policy policy) {
        PolicyText text = policy.getText();
        return new BenefitSource(
                policy.getSource(),
                policy.getName(),
                text == null ? null : text.getSupportContent(),
                policy.getDescription(),
                text == null ? null : text.getBusinessPeriod(),
                text == null ? null : text.getEtcNote());
    }

    /** AI에 보내는 원문. 값이 없는 항목은 뺀다 */
    public String toPromptText() {
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("정책명", name);
        sections.put("지원 내용", supportContent);
        sections.put("정책 설명", description);
        sections.put("사업 기간", businessPeriod);
        sections.put("기타", etcNote);
        return sections.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> "[" + e.getKey() + "]\n" + e.getValue().strip())
                .collect(Collectors.joining("\n\n"));
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

    /** 지원 내용과 설명이 모두 비어 있으면 추출할 근거가 없다 */
    public boolean hasContent() {
        return (supportContent != null && !supportContent.isBlank())
                || (description != null && !description.isBlank());
    }
}
