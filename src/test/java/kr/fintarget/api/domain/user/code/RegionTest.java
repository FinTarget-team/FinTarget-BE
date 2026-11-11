package kr.fintarget.api.domain.user.code;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionTest {

    @Test
    void 전국_17개_시도_코드가_모두_존재한다() {
        assertThat(Region.values()).hasSize(17);
        assertThat(Region.values())
                .extracting(Region::getCode)
                .containsExactly(
                        "SEOUL", "BUSAN", "DAEGU", "INCHEON", "GWANGJU", "DAEJEON", "ULSAN",
                        "SEJONG", "GYEONGGI", "GANGWON", "CHUNGBUK", "CHUNGNAM", "JEONBUK",
                        "JEONNAM", "GYEONGBUK", "GYEONGNAM", "JEJU"
                );
    }

    @Test
    void 코드로_조회하면_일치하는_enum과_한글_label을_반환한다() {
        assertThat(Region.fromCode("SEOUL")).isEqualTo(Region.SEOUL);
        assertThat(Region.fromCode("SEOUL").getLabel()).isEqualTo("서울");
        assertThat(Region.fromCode("JEJU")).isEqualTo(Region.JEJU);
        assertThat(Region.fromCode("JEJU").getLabel()).isEqualTo("제주");
    }

    @Test
    void 매핑에_없는_코드나_null이면_예외없이_null을_반환한다() {
        assertThat(Region.fromCode("ATLANTIS")).isNull();
        assertThat(Region.fromCode(null)).isNull();
        assertThat(Region.fromCode("")).isNull();
    }

    @Test
    void label로_조회하면_일치하는_enum을_반환한다() {
        assertThat(Region.fromLabel("서울")).isEqualTo(Region.SEOUL);
        assertThat(Region.fromLabel("제주")).isEqualTo(Region.JEJU);
    }

    @Test
    void label_앞뒤_공백은_제거하고_비교한다() {
        assertThat(Region.fromLabel(" 서울 ")).isEqualTo(Region.SEOUL);
    }

    @Test
    void label이_매핑에_없거나_null이면_예외없이_null을_반환한다() {
        assertThat(Region.fromLabel("아틀란티스")).isNull();
        assertThat(Region.fromLabel(null)).isNull();
        assertThat(Region.fromLabel("")).isNull();
    }
}
