package kr.fintarget.api.domain.user.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NameValidatorTest {

    @Test
    void null이면_NullPointerException이_발생한다() {
        assertThatThrownBy(() -> NameValidator.validate(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void 빈_문자열이면_예외가_발생한다() {
        assertThatThrownBy(() -> NameValidator.validate(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이름은 비어 있을 수 없습니다");
    }

    @Test
    void 공백만_있으면_예외가_발생한다() {
        assertThatThrownBy(() -> NameValidator.validate("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이름은 비어 있을 수 없습니다");
    }

    @Test
    void 전각_공백만_있으면_예외가_발생한다() {
        assertThatThrownBy(() -> NameValidator.validate("　　"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이름은 비어 있을 수 없습니다");
    }

    @Test
    void 길이가_20자면_정상_반환된다() {
        String name20 = "가".repeat(20);
        assertThat(NameValidator.validate(name20)).isEqualTo(name20);
    }

    @Test
    void 길이가_21자면_예외가_발생한다() {
        String name21 = "가".repeat(21);
        assertThatThrownBy(() -> NameValidator.validate(name21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이름은 20자 이하여야 합니다");
    }

    @Test
    void 앞뒤_공백은_제거되어_반환된다() {
        assertThat(NameValidator.validate("  홍길동  ")).isEqualTo("홍길동");
    }

    @Test
    void 앞뒤_전각_공백도_제거되어_반환된다() {
        assertThat(NameValidator.validate("　홍길동　")).isEqualTo("홍길동");
    }
}
