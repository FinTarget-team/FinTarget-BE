package kr.fintarget.api.domain.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정책 원문 텍스트 (#113). 정책 상세 AI 질문의 근거로 쓰려고 소스 응답의 원문을 의미별 컬럼에 나눠 저장한다.
 * 여러 컬럼을 하나의 문서로 합치는 조립은 AI 질문 단계에서 한다. 정책 목록 응답(PolicyResponse)에는 노출하지 않는다.
 * 모든 컬럼은 nullable이며, 소스에 해당 내용이 없거나 빈 값/"-"이면 null이다 (PolicyTextCleaner 참고).
 *
 * 소스별 매핑 (상세는 각 매퍼 참고)
 * - 온통청년: 컬럼마다 원문 필드 하나를 그대로 정제해 넣는다. business_period만 시작·종료일과 기타 문구를 합친다
 * - KINFA: 원문이 짧은 값 여러 개로 나뉘어 있어 support_content, eligibility_detail, contact_info는
 *   "라벨: 값" 줄로 묶는다. KINFA의 지원 대상 상세 조건(suprTgtDtlCond)은 이미 Policy.description에 들어가므로
 *   (KINFA는 description = 자격 조건) eligibility_detail에는 중복해 넣지 않는다
 */
@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PolicyText {

    // 지원 내용
    @Column(name = "support_content", columnDefinition = "TEXT")
    private String supportContent;

    // 추가 자격 조건
    @Column(name = "eligibility_detail", columnDefinition = "TEXT")
    private String eligibilityDetail;

    // 참여 제한 대상
    @Column(name = "exclusion_criteria", columnDefinition = "TEXT")
    private String exclusionCriteria;

    // 소득 조건 원문 (정형화된 incomeLimit과 별개)
    @Column(name = "income_condition", columnDefinition = "TEXT")
    private String incomeCondition;

    // 나이 조건 원문 (정형화된 minAge/maxAge와 별개)
    @Column(name = "age_condition", columnDefinition = "TEXT")
    private String ageCondition;

    // 신청 방법
    @Column(name = "apply_method", columnDefinition = "TEXT")
    private String applyMethod;

    // 심사 방법
    @Column(name = "screening_method", columnDefinition = "TEXT")
    private String screeningMethod;

    // 제출 서류
    @Column(name = "required_documents", columnDefinition = "TEXT")
    private String requiredDocuments;

    // 신청 기간
    @Column(name = "apply_period", columnDefinition = "TEXT")
    private String applyPeriod;

    // 사업 기간 (신청 기간과 별개)
    @Column(name = "business_period", columnDefinition = "TEXT")
    private String businessPeriod;

    // 참고 링크
    @Column(name = "reference_url", columnDefinition = "TEXT")
    private String referenceUrl;

    // 신청 링크
    @Column(name = "apply_url", columnDefinition = "TEXT")
    private String applyUrl;

    // 문의처·관련 사이트 안내 (URL이 아닌 텍스트)
    @Column(name = "contact_info", columnDefinition = "TEXT")
    private String contactInfo;

    // 기타 참고 사항
    @Column(name = "etc_note", columnDefinition = "TEXT")
    private String etcNote;
}
