package kr.fintarget.api.domain.user.service;

import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.simulation.repository.SimulationRepository;
import kr.fintarget.api.domain.user.dto.UpdateProfileRequest;
import kr.fintarget.api.domain.user.dto.UserProfileResponse;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private GoalRepository goalRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private UserPolicyRepository userPolicyRepository;
    @Mock
    private SimulationRepository simulationRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void 프로필_조회시_온보딩으로_저장된_age_income이_코드로_변환되어_내려간다() {
        User user = User.builder()
                .id("user-1")
                .provider("KAKAO")
                .providerId("kakao-1")
                .region("SEOUL")
                .age(40)
                .income(3_500_000L)
                .employmentType("STUDENT")
                .onboardingCompleted(true)
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getProfile("user-1");

        assertThat(response.getAgeGroup()).isEqualTo("MIDDLE");
        assertThat(response.getIncomeBracket()).isEqualTo("OVER_3M");
        assertThat(response.getEmploymentType()).isEqualTo("STUDENT");
        assertThat(response.getRegion()).isEqualTo("SEOUL");
        assertThat(response.isOnboardingCompleted()).isTrue();
    }

    private UpdateProfileRequest updateRequest(String name, String email, String region,
                                                String ageGroup, String employmentType, String incomeBracket) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "region", region);
        ReflectionTestUtils.setField(request, "ageGroup", ageGroup);
        ReflectionTestUtils.setField(request, "employmentType", employmentType);
        ReflectionTestUtils.setField(request, "incomeBracket", incomeBracket);
        return request;
    }

    @Test
    void 프로필_수정시_ageGroup_employmentType_incomeBracket이_저장값으로_변환되어_반영된다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, "MIDDLE", "STUDENT", "OVER_3M");

        UserProfileResponse response = userService.updateProfile("user-1", request);

        assertThat(user.getAge()).isEqualTo(40);
        assertThat(user.getIncome()).isEqualTo(3_500_000L);
        assertThat(user.getEmploymentType()).isEqualTo("STUDENT");
        assertThat(response.getAgeGroup()).isEqualTo("MIDDLE");
        assertThat(response.getIncomeBracket()).isEqualTo("OVER_3M");
        assertThat(response.getEmploymentType()).isEqualTo("STUDENT");
    }

    @Test
    void ageGroup이_유효하지_않은_코드면_400_예외가_발생하고_값이_바뀌지_않는다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, "SENIOR", null, null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 나이 코드입니다");
        assertThat(user.getAge()).isNull();
    }

    @Test
    void employmentType이_유효하지_않은_코드면_400_예외가_발생하고_값이_바뀌지_않는다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, null, "FREELANCER", null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 고용 형태 코드입니다");
        assertThat(user.getEmploymentType()).isNull();
    }

    @Test
    void incomeBracket이_유효하지_않은_코드면_400_예외가_발생하고_값이_바뀌지_않는다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, null, null, "OVER_10M");

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 소득 코드입니다");
        assertThat(user.getIncome()).isNull();
    }

    @Test
    void ageGroup이_빈_문자열이면_400_예외가_발생한다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, "", null, null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 나이 코드입니다");
    }

    @Test
    void employmentType이_빈_문자열이면_400_예외가_발생한다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, null, "", null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 고용 형태 코드입니다");
    }

    @Test
    void incomeBracket이_빈_문자열이면_400_예외가_발생한다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, null, null, "");

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 소득 코드입니다");
    }

    @Test
    void 온보딩_항목이_모두_null이면_기존_값을_유지한다() {
        User user = User.builder()
                .id("user-1").provider("KAKAO").providerId("kakao-1")
                .age(25).income(1_500_000L).employmentType("EMPLOYED")
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, null, null, null);

        userService.updateProfile("user-1", request);

        assertThat(user.getAge()).isEqualTo(25);
        assertThat(user.getIncome()).isEqualTo(1_500_000L);
        assertThat(user.getEmploymentType()).isEqualTo("EMPLOYED");
    }

    @Test
    void 일부_필드만_요청하면_나머지_온보딩_필드와_기존_name은_유지된다() {
        User user = User.builder()
                .id("user-1").provider("KAKAO").providerId("kakao-1")
                .name("기존이름")
                .age(25).income(1_500_000L).employmentType("EMPLOYED")
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, null, null, "OVER_3M");

        UserProfileResponse response = userService.updateProfile("user-1", request);

        assertThat(user.getAge()).isEqualTo(25);
        assertThat(user.getEmploymentType()).isEqualTo("EMPLOYED");
        assertThat(user.getIncome()).isEqualTo(3_500_000L);
        assertThat(response.getName()).isEqualTo("기존이름");
    }

    @Test
    void name_email_region_수정은_기존과_동일하게_동작한다() {
        User user = User.builder()
                .id("user-1").provider("KAKAO").providerId("kakao-1")
                .name("기존이름").email("old@example.com").region("BUSAN")
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest("새이름", "new@example.com", "SEOUL", null, null, null);

        UserProfileResponse response = userService.updateProfile("user-1", request);

        assertThat(response.getName()).isEqualTo("새이름");
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getRegion()).isEqualTo("SEOUL");
    }
}
