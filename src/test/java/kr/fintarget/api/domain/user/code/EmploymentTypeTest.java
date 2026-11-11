package kr.fintarget.api.domain.user.code;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmploymentTypeTest {

    @Test
    void 코드로_고용형태를_조회하면_일치하는_enum을_반환한다() {
        assertThat(EmploymentType.fromCode("EMPLOYED")).isEqualTo(EmploymentType.EMPLOYED);
        assertThat(EmploymentType.fromCode("UNEMPLOYED")).isEqualTo(EmploymentType.UNEMPLOYED);
        assertThat(EmploymentType.fromCode("STUDENT")).isEqualTo(EmploymentType.STUDENT);
        assertThat(EmploymentType.fromCode("SELF_EMPLOYED")).isEqualTo(EmploymentType.SELF_EMPLOYED);
    }

    @Test
    void getCode는_enum_이름과_동일한_코드_문자열을_반환한다() {
        assertThat(EmploymentType.EMPLOYED.getCode()).isEqualTo("EMPLOYED");
    }

    @Test
    void 매핑에_없는_코드나_null이면_예외없이_null을_반환한다() {
        assertThat(EmploymentType.fromCode("FREELANCER")).isNull();
        assertThat(EmploymentType.fromCode(null)).isNull();
        assertThat(EmploymentType.fromCode("")).isNull();
    }
}
