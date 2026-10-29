package kr.fintarget.api.domain.ai.chat.service;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDocumentTest {

    private static final String LONG_SUPPORT = "월 최대 20만원, 최대 12개월 동안 월세를 지원합니다. ".repeat(3);

    private static Policy youthCenter(String description, PolicyText text) {
        return Policy.create("청년 월세 지원", description, 19, 34, null, null, PolicyType.HOUSING,
                "SEOUL,GYEONGGI", "P-1", "YOUTH_CENTER", text);
    }

    @Test
    void 온통청년_원문을_라벨_블록으로_순서대로_조립한다() {
        PolicyText text = PolicyText.builder()
                .supportContent(LONG_SUPPORT)
                .eligibilityDetail("무주택 청년")
                .requiredDocuments("임대차계약서")
                .applyPeriod("20260101 ~ 20261231")
                .referenceUrl("https://example.com/ref")
                .build();

        PolicyDocument document = PolicyDocument.of(youthCenter("청년 월세를 지원하는 사업", text));

        assertThat(document.text())
                .startsWith("정책명: 청년 월세 지원")
                .contains("제공 기관 데이터: 온통청년", "신청 가능 지역: 서울, 경기", "나이 범위(정형 값): 만 19세 ~ 만 34세",
                        "[정책 설명]\n청년 월세를 지원하는 사업", "[자격 조건]\n무주택 청년", "[참고 링크]\nhttps://example.com/ref")
                .doesNotContain("[소득 조건]", "[심사 방법]");
        assertThat(document.text().indexOf("[지원 내용]")).isLessThan(document.text().indexOf("[제출 서류]"));
        assertThat(document.text().indexOf("[제출 서류]")).isLessThan(document.text().indexOf("[신청 기간]"));
        assertThat(document.sourceLimited()).isFalse();
    }

    @Test
    void KINFA는_description을_지원_대상_상세_조건으로_붙인다() {
        Policy policy = Policy.create("햇살론", "연소득 3,500만원 이하", null, null, null, null, PolicyType.FINANCE_WELFARE,
                null, "K-1", "KINFA", PolicyText.builder().supportContent(LONG_SUPPORT).build());

        PolicyDocument document = PolicyDocument.of(policy);

        assertThat(document.text()).contains("서민금융진흥원(KINFA)", "신청 가능 지역: 전국",
                "[지원 대상 상세 조건]\n연소득 3,500만원 이하").doesNotContain("[정책 설명]", "나이 범위");
    }

    @Test
    void 원문_컬럼이_모두_비면_description만으로_조립하고_원문_부족으로_표시한다() {
        // #113 배포 전에 마지막으로 동기화되고 신청 기록 때문에 남은 정책
        PolicyDocument document = PolicyDocument.of(youthCenter("아주 긴 설명 ".repeat(30), null));

        assertThat(document.text()).contains("[정책 설명]");
        assertThat(document.sourceLimited()).isTrue();
    }

    @Test
    void 근거_텍스트_합계가_100자_미만이면_원문_부족으로_표시한다() {
        PolicyText shortText = PolicyText.builder()
                .supportContent("월세 지원")
                .referenceUrl("https://example.com/" + "a".repeat(200)) // 링크 길이는 판정에서 뺀다
                .build();

        assertThat(PolicyDocument.of(youthCenter("짧은 설명", shortText)).sourceLimited()).isTrue();
    }

    @Test
    void 근거_텍스트_합계가_100자_이상이면_원문_부족이_아니다() {
        PolicyText text = PolicyText.builder().supportContent("가".repeat(90)).build();

        assertThat(PolicyDocument.of(youthCenter("나".repeat(10), text)).sourceLimited()).isFalse();
        assertThat(PolicyDocument.of(youthCenter("나".repeat(9), text)).sourceLimited()).isTrue();
    }
}
