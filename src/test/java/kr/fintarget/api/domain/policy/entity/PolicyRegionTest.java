package kr.fintarget.api.domain.policy.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyRegionTest {

    private Policy policyIn(String region) {
        return Policy.create("정책", "설명", null, null, null, null, PolicyType.ETC, region, null, null);
    }

    @Test
    void 지역이_null이나_빈_값이면_전국_정책이다() {
        assertThat(policyIn(null).regionCodes()).isEmpty();
        assertThat(policyIn("").regionCodes()).isEmpty();
        assertThat(policyIn("  ").regionCodes()).isEmpty();
        assertThat(policyIn(null).appliesToRegion("SEOUL")).isTrue();
        assertThat(policyIn("").appliesToRegion("JEJU")).isTrue();
    }

    @Test
    void 단일_지역_정책은_같은_지역_사용자에게만_적용된다() {
        Policy seoul = policyIn("SEOUL");

        assertThat(seoul.appliesToRegion("SEOUL")).isTrue();
        assertThat(seoul.appliesToRegion("BUSAN")).isFalse();
    }

    @Test
    void 다중_지역_정책은_코드_중_하나와_정확히_같으면_적용된다() {
        Policy policy = policyIn("GYEONGNAM,ULSAN");

        assertThat(policy.regionCodes()).containsExactly("GYEONGNAM", "ULSAN");
        assertThat(policy.appliesToRegion("ULSAN")).isTrue();
        assertThat(policy.appliesToRegion("GYEONGNAM")).isTrue();
        assertThat(policy.appliesToRegion("BUSAN")).isFalse();
    }

    @Test
    void 이름이_비슷한_코드끼리_섞이지_않는다() {
        assertThat(policyIn("GYEONGNAM").appliesToRegion("GYEONGBUK")).isFalse();
        assertThat(policyIn("JEONNAM").appliesToRegion("JEONBUK")).isFalse();
        assertThat(policyIn("CHUNGBUK").appliesToRegion("CHUNGNAM")).isFalse();
    }

    @Test
    void 토큰의_앞뒤_공백은_무시하고_중복은_제거한다() {
        Policy policy = policyIn(" GYEONGNAM , ULSAN,ULSAN ");

        assertThat(policy.regionCodes()).containsExactly("GYEONGNAM", "ULSAN");
        assertThat(policy.appliesToRegion("ULSAN")).isTrue();
        assertThat(policy.appliesToRegion(" ULSAN ")).isTrue();
    }

    @Test
    void 부분_문자열은_일치로_보지_않는다() {
        Policy policy = policyIn("GYEONGNAM,ULSAN");

        assertThat(policy.appliesToRegion("ULSA")).isFalse();
        assertThat(policy.appliesToRegion("NAM")).isFalse();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void Region_코드가_아닌_값이_하나라도_섞이면_정책_전체를_전국으로_보고_경고_로그를_남긴다(CapturedOutput output) {
        Policy policy = Policy.create("정책", "설명", null, null, null, null, PolicyType.ETC, "서울,BUSAN", "EXT-1", "YOUTH_CENTER");

        assertThat(policy.regionCodes()).isEmpty();
        assertThat(policy.appliesToRegion("SEOUL")).isTrue();
        assertThat(policy.appliesToRegion("BUSAN")).isTrue();
        assertThat(policy.appliesToRegion("JEJU")).isTrue();
        assertThat(output.getOut()).contains("externalId=EXT-1, region=서울,BUSAN");
    }

    @Test
    void 유효한_코드가_하나도_없으면_전국으로_본다() {
        Policy policy = policyIn("전국");

        assertThat(policy.regionCodes()).isEmpty();
        assertThat(policy.appliesToRegion("SEOUL")).isTrue();
    }

    @Test
    void 빈_토큰은_건너뛰고_나머지_코드로_판정한다() {
        Policy policy = policyIn("SEOUL,,BUSAN,");

        assertThat(policy.regionCodes()).containsExactly("SEOUL", "BUSAN");
        assertThat(policy.appliesToRegion("JEJU")).isFalse();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 유효한_코드만_있으면_경고_로그를_남기지_않는다(CapturedOutput output) {
        policyIn("GYEONGNAM,ULSAN").regionCodes();
        policyIn(null).regionCodes();

        assertThat(output.getOut()).doesNotContain("전국으로 처리");
    }

    @Test
    void 사용자_지역이_없으면_전국_정책만_적용된다() {
        assertThat(policyIn(null).appliesToRegion(null)).isTrue();
        assertThat(policyIn(null).appliesToRegion("")).isTrue();
        assertThat(policyIn("SEOUL").appliesToRegion(null)).isFalse();
        assertThat(policyIn("SEOUL").appliesToRegion("  ")).isFalse();
    }
}
