package kr.fintarget.api.domain.user.code;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgeGroupTest {

    @Test
    void 코드로_연령대를_조회하면_저장값을_반환한다() {
        assertThat(AgeGroup.fromCode("YOUTH")).isEqualTo(AgeGroup.YOUTH);
        assertThat(AgeGroup.fromCode("YOUTH").getAge()).isEqualTo(25);
        assertThat(AgeGroup.fromCode("MIDDLE")).isEqualTo(AgeGroup.MIDDLE);
        assertThat(AgeGroup.fromCode("MIDDLE").getAge()).isEqualTo(40);
    }

    @Test
    void 저장값으로_연령대를_조회하면_코드를_반환한다() {
        assertThat(AgeGroup.fromAge(25)).isEqualTo(AgeGroup.YOUTH);
        assertThat(AgeGroup.fromAge(25).getCode()).isEqualTo("YOUTH");
        assertThat(AgeGroup.fromAge(40)).isEqualTo(AgeGroup.MIDDLE);
        assertThat(AgeGroup.fromAge(40).getCode()).isEqualTo("MIDDLE");
    }

    @Test
    void 매핑에_없는_코드나_값이면_null을_반환하고_예외를_던지지_않는다() {
        assertThat(AgeGroup.fromCode("UNKNOWN")).isNull();
        assertThat(AgeGroup.fromCode(null)).isNull();
        assertThat(AgeGroup.fromAge(99)).isNull();
    }

    @Test
    void age가_null이면_null을_반환한다() {
        assertThat(AgeGroup.fromAge(null)).isNull();
    }
}
