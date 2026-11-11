package kr.fintarget.api.domain.ai.chat.service;

import kr.fintarget.api.domain.policy.document.PolicySections;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;

import java.util.List;
import java.util.function.Function;

/**
 * AI 질문의 근거로 넣을 정책 원문 문서. 정책 1건의 저장된 원문(PolicyText, description)을 라벨 붙은 블록으로 조립한다.
 * 섹션 정의·순서·라벨 규칙(KINFA description = 지원 대상 상세 조건)은 PolicySections에 있다 (#180).
 *
 * @param sourceLimited 원문이 부족함. 원문 컬럼이 모두 비었거나 근거 텍스트 합계가 LIMITED_SOURCE_LENGTH 미만
 */
public record PolicyDocument(String text, boolean sourceLimited) {

    // 2026-10-28 수집 원본 기준 온통청년 43건, KINFA 2건이 100자 미만
    static final int LIMITED_SOURCE_LENGTH = 100;

    // 링크 컬럼은 프롬프트에 넣지 않는다 (#147). 모델이 URL을 답변에 옮기거나 만들어내지 않도록 하고,
    // 신청 링크는 응답의 applyUrl로 따로 내려준다. 원문 부족 판정(anyTextColumn)에는 그대로 센다
    private static final List<Function<PolicyText, String>> LINK_COLUMNS = List.of(
            PolicyText::getReferenceUrl,
            PolicyText::getApplyUrl);

    public static PolicyDocument of(Policy policy) {
        StringBuilder document = new StringBuilder();
        document.append("정책명: ").append(policy.getName()).append('\n');
        document.append("제공 기관 데이터: ").append(PolicySections.isKinfa(policy) ? "서민금융진흥원(KINFA)" : "온통청년").append('\n');
        document.append("신청 가능 지역: ").append(PolicySections.regionLabel(policy)).append('\n');
        String ageLabel = PolicySections.ageLabel(policy);
        if (ageLabel != null) {
            document.append("나이 범위(정형 값): ").append(ageLabel).append('\n');
        }

        // 원문 본문 안의 URL(신청 방법, 문의처의 관련 사이트 등)도 지운다 (#147). 원문 부족 판정은 지우기 전 길이로 한다
        int bodyLength = 0;
        boolean anyTextColumn = false;
        for (PolicySections.Section section : PolicySections.of(policy)) {
            appendBlock(document, section.label(), section.value());
            if (section.kind() != PolicySections.Kind.INFO) {
                bodyLength += section.value().length();
            }
            if (section.kind() != PolicySections.Kind.DESCRIPTION) {
                anyTextColumn = true;
            }
        }
        PolicyText text = policy.getText();
        if (text != null && LINK_COLUMNS.stream().anyMatch(column -> hasText(column.apply(text)))) {
            anyTextColumn = true;
        }

        boolean sourceLimited = !anyTextColumn || bodyLength < LIMITED_SOURCE_LENGTH;
        return new PolicyDocument(document.toString().strip(), sourceLimited);
    }

    // URL을 지우고 남은 내용이 없으면 블록째 뺀다
    private static void appendBlock(StringBuilder document, String label, String value) {
        String withoutUrls = AiAnswerFormatter.removeUrls(value.strip());
        if (withoutUrls == null) {
            return;
        }
        document.append("\n[").append(label).append("]\n").append(withoutUrls).append('\n');
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
