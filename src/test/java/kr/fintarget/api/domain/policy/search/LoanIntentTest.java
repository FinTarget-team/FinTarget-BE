package kr.fintarget.api.domain.policy.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대출 의도 판정 (#201).
 */
class LoanIntentTest {

    @Test
    void 대출_표현이_있으면_참이다() {
        assertThat(LoanIntent.of("창업 자금 빌릴 수 있는 곳 있어?")).isTrue();
        assertThat(LoanIntent.of("전세자금대출 알아보고 있어")).isTrue();
        assertThat(LoanIntent.of("생활비 융자 받을 수 있나")).isTrue();
        assertThat(LoanIntent.of("금리가 낮은 상품 있어?")).isTrue();
        assertThat(LoanIntent.of("상환 기간이 궁금해")).isTrue();
    }

    @Test
    void 지원금을_찾는_질문은_거짓이다() {
        assertThat(LoanIntent.of("자취하는데 월세가 너무 부담돼")).isFalse();
        assertThat(LoanIntent.of("목돈 모으고 싶은데 도움되는 제도 있어?")).isFalse();
        assertThat(LoanIntent.of("교통비가 많이 나가는데 할인받을 방법 있어?")).isFalse();
        assertThat(LoanIntent.of("2026 청년 월세 지원 알려줘")).isFalse();
    }

    // 평가셋 region-01·02. 보증료 환급 정책은 benefit_type이 LOAN이라 대출 의도로 판정해야 검색에서 빠지지 않는다
    @Test
    void 보증료_반환보증_질문은_대출_의도다() {
        assertThat(LoanIntent.matched("전세보증금반환보증 보증료 지원받을 수 있어?"))
                .containsExactlyInAnyOrder("보증료", "반환보증");
        assertThat(LoanIntent.matched("전세보증금 반환보증 보증료 돌려받는 거 있잖아"))
                .containsExactlyInAnyOrder("보증료", "반환보증");
    }

    // "보증" 단독은 판정에서 뺐다. 보증금·보증보험처럼 대출과 무관한 표현에 흔해서 오탐이 많다
    @Test
    void 보증이_들어간_지원_정책_질문은_대출_의도가_아니다() {
        assertThat(LoanIntent.KEYWORDS).doesNotContain("보증");
        assertThat(LoanIntent.of("보증금 올려달래서 돈이 모자라")).isFalse();
        assertThat(LoanIntent.of("보증보험 가입해야 해?")).isFalse();
    }

    // "보증"을 뺀 대가. 대출을 찾는 질문인데 놓친다 — 평가 결과를 보고 넣을지 정한다
    @Test
    void 보증만_쓴_대출_질문은_놓친다() {
        assertThat(LoanIntent.of("특례보증이 뭐야")).isFalse();
        assertThat(LoanIntent.of("신용보증 받을 수 있어?")).isFalse();
        assertThat(LoanIntent.MISSED_WITHOUT_GUARANTEE).contains("특례보증", "신용보증");
    }

    // 대출 어휘가 함께 있으면 잡힌다 (실제 질문은 보통 이렇게 쓴다)
    @Test
    void 보증과_대출_어휘가_함께_있으면_대출_의도다() {
        assertThat(LoanIntent.of("신용보증으로 대출받을 수 있어?")).isTrue();
        assertThat(LoanIntent.of("청년 전월세 대출보증이 뭐야")).isTrue();
    }

    @Test
    void 빈_질문은_거짓이다() {
        assertThat(LoanIntent.of(null)).isFalse();
        assertThat(LoanIntent.of("   ")).isFalse();
    }

    @Test
    void 걸린_표현을_돌려준다() {
        assertThat(LoanIntent.matched("전세자금대출 금리 알려줘")).contains("대출", "금리");
        assertThat(LoanIntent.matched("월세가 부담돼")).isEmpty();
    }

    // 띄어쓰기를 지우고 비교하므로 "대 출"처럼 띄어 쓴 경우도 잡힌다
    @Test
    void 띄어쓰기는_무시한다() {
        assertThat(LoanIntent.of("전세 자금 대출")).isTrue();
    }
}
