package kr.fintarget.api.domain.user.dto;

import kr.fintarget.api.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileResponseTest {

    @Test
    void 온보딩을_완료한_사용자는_출생연도_소득구간_코드와_기존_필드를_그대로_반환한다() {
        User user = User.builder()
                .id("user-1")
                .provider("KAKAO")
                .providerId("kakao-1")
                .name("홍길동")
                .email("user@example.com")
                .region("SEOUL")
                .birthYear(2000)
                .income(2_050_000L)
                .employmentType("EMPLOYED")
                .onboardingCompleted(true)
                .build();

        UserProfileResponse response = UserProfileResponse.from(user);

        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getName()).isEqualTo("홍길동");
        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getRegion()).isEqualTo("SEOUL");
        assertThat(response.getProvider()).isEqualTo("KAKAO");
        assertThat(response.getBirthYear()).isEqualTo(2000);
        assertThat(response.getEmploymentType()).isEqualTo("EMPLOYED");
        assertThat(response.getIncomeBracket()).isEqualTo("150_260");
        assertThat(response.isOnboardingCompleted()).isTrue();
    }

    @Test
    void 온보딩을_완료하지_않은_사용자는_출생연도_소득구간_등이_null이다() {
        User user = User.builder()
                .id("user-2")
                .provider("KAKAO")
                .providerId("kakao-2")
                .onboardingCompleted(false)
                .build();

        UserProfileResponse response = UserProfileResponse.from(user);

        assertThat(response.getUserId()).isEqualTo("user-2");
        assertThat(response.getName()).isNull();
        assertThat(response.getEmail()).isNull();
        assertThat(response.getRegion()).isNull();
        assertThat(response.getBirthYear()).isNull();
        assertThat(response.getEmploymentType()).isNull();
        assertThat(response.getIncomeBracket()).isNull();
        assertThat(response.isOnboardingCompleted()).isFalse();
    }

    @Test
    void income이_매핑에_없는_값이면_예외없이_incomeBracket이_null이다() {
        User user = User.builder()
                .id("user-3")
                .provider("KAKAO")
                .providerId("kakao-3")
                .income(999L)
                .build();

        UserProfileResponse response = UserProfileResponse.from(user);

        assertThat(response.getIncomeBracket()).isNull();
    }

    @Test
    void 출생연도_입력_전에_연령대만_저장된_사용자는_birthYear가_null이다() {
        User user = User.builder()
                .id("user-4")
                .provider("KAKAO")
                .providerId("kakao-4")
                .age(25)
                .build();

        UserProfileResponse response = UserProfileResponse.from(user);

        assertThat(response.getBirthYear()).isNull();
    }

    @Test
    void housingStatus가_저장되어_있으면_코드를_그대로_반환한다() {
        User user = User.builder().id("user-5").provider("KAKAO").providerId("kakao-5").housingStatus("HOMEOWNER").build();

        assertThat(UserProfileResponse.from(user).getHousingStatus()).isEqualTo("HOMEOWNER");
    }

    @Test
    void housingStatus가_null이면_선택_안_함과_미응답_모두_NOT_SPECIFIED로_반환한다() {
        User user = User.builder().id("user-6").provider("KAKAO").providerId("kakao-6").onboardingCompleted(true).build();

        assertThat(UserProfileResponse.from(user).getHousingStatus()).isEqualTo("NOT_SPECIFIED");
    }
}
