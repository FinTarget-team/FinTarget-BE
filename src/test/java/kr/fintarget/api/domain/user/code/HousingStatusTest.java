package kr.fintarget.api.domain.user.code;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HousingStatusTest {

    @Test
    void 코드로_주택_보유_여부를_조회한다() {
        assertThat(HousingStatus.fromCode("HOMELESS")).isEqualTo(HousingStatus.HOMELESS);
        assertThat(HousingStatus.fromCode("HOMEOWNER")).isEqualTo(HousingStatus.HOMEOWNER);
        assertThat(HousingStatus.fromCode("NOT_SPECIFIED")).isEqualTo(HousingStatus.NOT_SPECIFIED);
    }

    @Test
    void 매핑에_없는_코드면_null을_반환한다() {
        assertThat(HousingStatus.fromCode("homeless")).isNull();
        assertThat(HousingStatus.fromCode("")).isNull();
        assertThat(HousingStatus.fromCode(" ")).isNull();
        assertThat(HousingStatus.fromCode(null)).isNull();
    }

    @Test
    void 선택_안_함만_저장값이_null이다() {
        assertThat(HousingStatus.HOMELESS.storedValue()).isEqualTo("HOMELESS");
        assertThat(HousingStatus.HOMEOWNER.storedValue()).isEqualTo("HOMEOWNER");
        assertThat(HousingStatus.NOT_SPECIFIED.storedValue()).isNull();
    }

    @Test
    void 저장값이_null이면_응답_코드는_NOT_SPECIFIED다() {
        assertThat(HousingStatus.responseCodeOf(null)).isEqualTo("NOT_SPECIFIED");
        assertThat(HousingStatus.responseCodeOf("HOMELESS")).isEqualTo("HOMELESS");
        assertThat(HousingStatus.responseCodeOf("HOMEOWNER")).isEqualTo("HOMEOWNER");
    }

    @Test
    void 라벨을_반환한다() {
        assertThat(HousingStatus.HOMELESS.getLabel()).isEqualTo("무주택");
        assertThat(HousingStatus.HOMEOWNER.getLabel()).isEqualTo("유주택");
        assertThat(HousingStatus.NOT_SPECIFIED.getLabel()).isEqualTo("선택 안 함");
    }
}
