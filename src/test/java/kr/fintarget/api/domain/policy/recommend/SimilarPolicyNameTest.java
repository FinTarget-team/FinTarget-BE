package kr.fintarget.api.domain.policy.recommend;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarPolicyNameTest {

    @Test
    void 괄호_앞_이름이_같으면_같은_키다() {
        assertThat(SimilarPolicyName.key("생활안정자금 융자(부모요양비)"))
                .isEqualTo(SimilarPolicyName.key("생활안정자금 융자(혼례비)"))
                .isEqualTo("생활안정자금융자");
    }

    @Test
    void 앞쪽의_연도_지역_낱말과_괄호_머리말만_다르면_같은_키다() {
        assertThat(SimilarPolicyName.key("2025년 중구 소상공인 경영안정자금 지원 특례보증"))
                .isEqualTo(SimilarPolicyName.key("2025년 북구 소상공인 경영안정자금 지원 특례보증(Ⅱ)"));
        assertThat(SimilarPolicyName.key("부산 전세보증금 반환보증 보증료 지원"))
                .isEqualTo(SimilarPolicyName.key("전세보증금반환보증 보증료 지원"));
        assertThat(SimilarPolicyName.key("(옹진군) 인천시 청년월세 지원사업"))
                .isEqualTo(SimilarPolicyName.key("(강화군) 인천시 청년월세 지원사업"));
        assertThat(SimilarPolicyName.key("경상북도 청년 월세 지원")).isEqualTo(SimilarPolicyName.key("화순군 청년 월세 지원"));
    }

    @Test
    void 이름_가운데의_시군구로_끝나는_일반_낱말은_지우지_않는다() {
        assertThat(SimilarPolicyName.key("청년 1인 가구 지원")).isEqualTo("청년1인가구지원");
        assertThat(SimilarPolicyName.key("청년 전시 관람 지원")).isNotEqualTo(SimilarPolicyName.key("청년 관람 지원"));
    }

    @Test
    void 지역_낱말뿐인_이름이나_null도_키를_만든다() {
        assertThat(SimilarPolicyName.key("서울")).isEqualTo("서울");
        assertThat(SimilarPolicyName.key(null)).isEmpty();
    }
}
