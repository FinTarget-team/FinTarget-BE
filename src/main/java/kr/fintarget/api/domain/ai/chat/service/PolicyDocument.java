package kr.fintarget.api.domain.ai.chat.service;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.mapper.KinfaPolicyMapper;
import kr.fintarget.api.domain.user.code.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * AI 질문의 근거로 넣을 정책 원문 문서. 정책 1건의 저장된 원문(PolicyText, description)을 라벨 붙은 블록으로 조립한다.
 *
 * <p>소스별 규칙: KINFA는 description이 지원 대상 상세 조건(자격 조건)이라 "지원 대상 상세 조건"으로,
 * 온통청년은 "정책 설명"으로 붙인다 (PolicyText 주석 참고).
 *
 * @param sourceLimited 원문이 부족함. 원문 컬럼이 모두 비었거나 근거 텍스트 합계가 LIMITED_SOURCE_LENGTH 미만
 */
public record PolicyDocument(String text, boolean sourceLimited) {

    // 2026-10-28 수집 원본 기준 온통청년 43건, KINFA 2건이 100자 미만
    static final int LIMITED_SOURCE_LENGTH = 100;

    // 근거 텍스트 블록. 링크·기간 같은 짧은 값은 길이 판정에서 뺀다 (#127 측정 SQL 2번과 같은 기준)
    private static final List<Block> BODY_BLOCKS = List.of(
            new Block("지원 내용", PolicyText::getSupportContent),
            new Block("자격 조건", PolicyText::getEligibilityDetail),
            new Block("참여 제한 대상", PolicyText::getExclusionCriteria),
            new Block("소득 조건", PolicyText::getIncomeCondition),
            new Block("나이 조건", PolicyText::getAgeCondition),
            new Block("신청 방법", PolicyText::getApplyMethod),
            new Block("심사 방법", PolicyText::getScreeningMethod),
            new Block("제출 서류", PolicyText::getRequiredDocuments),
            new Block("기타 참고 사항", PolicyText::getEtcNote));

    private static final List<Block> INFO_BLOCKS = List.of(
            new Block("신청 기간", PolicyText::getApplyPeriod),
            new Block("사업 기간", PolicyText::getBusinessPeriod),
            new Block("문의처", PolicyText::getContactInfo),
            new Block("참고 링크", PolicyText::getReferenceUrl),
            new Block("신청 링크", PolicyText::getApplyUrl));

    public static PolicyDocument of(Policy policy) {
        boolean kinfa = KinfaPolicyMapper.SOURCE.equals(policy.getSource());
        // 원문 컬럼이 모두 null이면 Hibernate가 임베디드 값을 null로 돌려준다
        PolicyText text = policy.getText();

        StringBuilder document = new StringBuilder();
        document.append("정책명: ").append(policy.getName()).append('\n');
        document.append("제공 기관 데이터: ").append(kinfa ? "서민금융진흥원(KINFA)" : "온통청년").append('\n');
        document.append("신청 가능 지역: ").append(regionLabel(policy)).append('\n');
        if (policy.getMinAge() != null || policy.getMaxAge() != null) {
            document.append("나이 범위(정형 값): ").append(ageLabel(policy.getMinAge(), policy.getMaxAge())).append('\n');
        }

        int bodyLength = 0;
        if (hasText(policy.getDescription())) {
            appendBlock(document, kinfa ? "지원 대상 상세 조건" : "정책 설명", policy.getDescription());
            bodyLength += policy.getDescription().length();
        }
        boolean anyTextColumn = false;
        if (text != null) {
            for (Block block : BODY_BLOCKS) {
                String value = block.value().apply(text);
                if (hasText(value)) {
                    appendBlock(document, block.label(), value);
                    bodyLength += value.length();
                    anyTextColumn = true;
                }
            }
            for (Block block : INFO_BLOCKS) {
                String value = block.value().apply(text);
                if (hasText(value)) {
                    appendBlock(document, block.label(), value);
                    anyTextColumn = true;
                }
            }
        }

        boolean sourceLimited = !anyTextColumn || bodyLength < LIMITED_SOURCE_LENGTH;
        return new PolicyDocument(document.toString().strip(), sourceLimited);
    }

    private static void appendBlock(StringBuilder document, String label, String value) {
        document.append("\n[").append(label).append("]\n").append(value.strip()).append('\n');
    }

    private static String regionLabel(Policy policy) {
        List<String> labels = new ArrayList<>();
        for (String code : policy.regionCodes()) {
            Region region = Region.fromCode(code);
            labels.add(region == null ? code : region.getLabel());
        }
        return labels.isEmpty() ? "전국" : String.join(", ", labels);
    }

    private static String ageLabel(Integer minAge, Integer maxAge) {
        if (minAge != null && maxAge != null) {
            return "만 " + minAge + "세 ~ 만 " + maxAge + "세";
        }
        return minAge != null ? "만 " + minAge + "세 이상" : "만 " + maxAge + "세 이하";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Block(String label, Function<PolicyText, String> value) {
    }
}
