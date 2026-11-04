package kr.fintarget.api.domain.policy.document;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.mapper.KinfaPolicyMapper;
import kr.fintarget.api.domain.user.code.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 정책 원문을 라벨 붙은 섹션 목록으로 나눈다. 단건 AI 질문 문서(PolicyDocument)와 검색용 청크(PolicyChunker, #180)가
 * 같은 섹션 정의·순서·라벨 규칙을 쓰도록 한곳에 둔다.
 *
 * <p>소스별 규칙: KINFA는 description이 지원 대상 상세 조건(자격 조건)이라 "지원 대상 상세 조건"으로,
 * 온통청년은 "정책 설명"으로 붙인다 (PolicyText 주석 참고).
 *
 * <p>값은 원문 그대로(strip·URL 제거 전) 돌려준다. URL 제거와 조립은 쓰는 쪽에서 한다.
 * 링크 컬럼(참고 링크, 신청 링크)은 섹션에 넣지 않는다 (#147).
 */
public final class PolicySections {

    /**
     * DESCRIPTION: Policy.description, BODY: 근거 텍스트 블록, INFO: 기간·문의처 같은 짧은 안내 값.
     * PolicyDocument의 원문 부족 판정이 BODY·DESCRIPTION 길이만 세고, 원문 컬럼 존재 여부는 BODY·INFO로 보기 때문에 구분한다.
     */
    public enum Kind {
        DESCRIPTION, BODY, INFO
    }

    public record Section(String label, String value, Kind kind) {
    }

    // 근거 텍스트 블록. 링크·기간 같은 짧은 값은 길이 판정에서 뺀다 (#127 측정 SQL 2번과 같은 기준)
    private static final List<Column> BODY_COLUMNS = List.of(
            new Column("지원 내용", PolicyText::getSupportContent),
            new Column("자격 조건", PolicyText::getEligibilityDetail),
            new Column("참여 제한 대상", PolicyText::getExclusionCriteria),
            new Column("소득 조건", PolicyText::getIncomeCondition),
            new Column("나이 조건", PolicyText::getAgeCondition),
            new Column("신청 방법", PolicyText::getApplyMethod),
            new Column("심사 방법", PolicyText::getScreeningMethod),
            new Column("제출 서류", PolicyText::getRequiredDocuments),
            new Column("기타 참고 사항", PolicyText::getEtcNote));

    private static final List<Column> INFO_COLUMNS = List.of(
            new Column("신청 기간", PolicyText::getApplyPeriod),
            new Column("사업 기간", PolicyText::getBusinessPeriod),
            new Column("문의처", PolicyText::getContactInfo));

    private PolicySections() {
    }

    /** 값이 있는 섹션만 정책 설명 → 근거 블록 → 안내 블록 순서로 돌려준다 */
    public static List<Section> of(Policy policy) {
        List<Section> sections = new ArrayList<>();
        if (hasText(policy.getDescription())) {
            sections.add(new Section(descriptionLabel(policy), policy.getDescription(), Kind.DESCRIPTION));
        }
        // 원문 컬럼이 모두 null이면 Hibernate가 임베디드 값을 null로 돌려준다
        PolicyText text = policy.getText();
        if (text != null) {
            addColumns(sections, text, BODY_COLUMNS, Kind.BODY);
            addColumns(sections, text, INFO_COLUMNS, Kind.INFO);
        }
        return sections;
    }

    public static boolean isKinfa(Policy policy) {
        return KinfaPolicyMapper.SOURCE.equals(policy.getSource());
    }

    /** 신청 가능 지역 표시 값. 지역 제한이 없으면 "전국" */
    public static String regionLabel(Policy policy) {
        List<String> labels = new ArrayList<>();
        for (String code : policy.regionCodes()) {
            Region region = Region.fromCode(code);
            labels.add(region == null ? code : region.getLabel());
        }
        return labels.isEmpty() ? "전국" : String.join(", ", labels);
    }

    /** 정형 나이 범위 표시 값. minAge·maxAge가 모두 없으면 null */
    public static String ageLabel(Policy policy) {
        Integer minAge = policy.getMinAge();
        Integer maxAge = policy.getMaxAge();
        if (minAge == null && maxAge == null) {
            return null;
        }
        if (minAge != null && maxAge != null) {
            return "만 " + minAge + "세 ~ 만 " + maxAge + "세";
        }
        return minAge != null ? "만 " + minAge + "세 이상" : "만 " + maxAge + "세 이하";
    }

    private static String descriptionLabel(Policy policy) {
        return isKinfa(policy) ? "지원 대상 상세 조건" : "정책 설명";
    }

    private static void addColumns(List<Section> sections, PolicyText text, List<Column> columns, Kind kind) {
        for (Column column : columns) {
            String value = column.value().apply(text);
            if (hasText(value)) {
                sections.add(new Section(column.label(), value, kind));
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Column(String label, Function<PolicyText, String> value) {
    }
}
