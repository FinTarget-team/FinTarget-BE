package kr.fintarget.api.domain.user.dto;

import kr.fintarget.api.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileResponseTest {

    @Test
    void 온보딩을_완료한_사용자는_연령대_소득구간_코드와_기존_필드를_그대로_반환한다() {
        User user = User.builder()
                .id("user-1")
                .provider("KAKAO")
                .providerId("kakao-1")
                .name("홍길동")
                .email("user@example.com")
                .region("SEOUL")
                .age(25)
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
        assertThat(response.getAgeGroup()).isEqualTo("YOUTH");
        assertThat(response.getEmploymentType()).isEqualTo("EMPLOYED");
        assertThat(response.getIncomeBracket()).isEqualTo("150_260");
        assertThat(response.isOnboardingCompleted()).isTrue();
    }

    @Test
    void 온보딩을_완료하지_않은_사용자는_연령대_소득구간_등이_null이다() {
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
        assertThat(response.getAgeGroup()).isNull();
        assertThat(response.getEmploymentType()).isNull();
        assertThat(response.getIncomeBracket()).isNull();
        assertThat(response.isOnboardingCompleted()).isFalse();
    }

    @Test
    void age_income이_매핑에_없는_값이면_예외없이_ageGroup_incomeBracket이_null이다() {
        User user = User.builder()
                .id("user-3")
                .provider("KAKAO")
                .providerId("kakao-3")
                .age(99)
                .income(999L)
                .build();

        UserProfileResponse response = UserProfileResponse.from(user);

        assertThat(response.getAgeGroup()).isNull();
        assertThat(response.getIncomeBracket()).isNull();
    }
}
