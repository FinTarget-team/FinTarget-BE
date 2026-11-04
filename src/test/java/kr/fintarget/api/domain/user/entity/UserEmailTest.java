package kr.fintarget.api.domain.user.entity;

import kr.fintarget.api.domain.user.code.SocialProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserEmailTest {

    @Test
    void 이메일이_없으면_소셜_이메일로_채우고_true를_반환한다() {
        User user = user(null);

        assertThat(user.fillEmailIfAbsent("  social@kakao.com ")).isTrue();
        assertThat(user.getEmail()).isEqualTo("social@kakao.com");
    }

    @Test
    void 이메일이_이미_있으면_덮어쓰지_않는다() {
        User user = user("saved@example.com");

        assertThat(user.fillEmailIfAbsent("social@kakao.com")).isFalse();
        assertThat(user.getEmail()).isEqualTo("saved@example.com");
    }

    @Test
    void 소셜_이메일이_null이거나_비어_있으면_그대로_둔다() {
        User user = user(null);

        assertThat(user.fillEmailIfAbsent(null)).isFalse();
        assertThat(user.fillEmailIfAbsent("   ")).isFalse();
        assertThat(user.getEmail()).isNull();
    }

    @Test
    void 컬럼_길이를_넘는_이메일은_저장하지_않는다() {
        String tooLong = "a".repeat(250) + "@kakao.com";

        assertThat(User.acceptableEmail(tooLong)).isNull();
        assertThat(user(null).fillEmailIfAbsent(tooLong)).isFalse();
    }

    private static User user(String email) {
        return User.builder().id("user-1").provider(SocialProvider.KAKAO).providerId("kakao-1").email(email).build();
    }
}
