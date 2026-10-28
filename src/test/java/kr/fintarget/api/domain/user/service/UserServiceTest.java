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
    void 프로필_조회시_온보딩으로_저장된_birthYear와_income_코드가_내려간다() {
        User user = User.builder()
                .id("user-1")
                .provider("KAKAO")
                .providerId("kakao-1")
                .region("SEOUL")
                .birthYear(1986)
                .income(3_400_000L)
                .employmentType("STUDENT")
                .onboardingCompleted(true)
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getProfile("user-1");

        assertThat(response.getBirthYear()).isEqualTo(1986);
        assertThat(response.getIncomeBracket()).isEqualTo("300_380");
        assertThat(response.getEmploymentType()).isEqualTo("STUDENT");
        assertThat(response.getRegion()).isEqualTo("SEOUL");
        assertThat(response.isOnboardingCompleted()).isTrue();
    }

    private UpdateProfileRequest updateRequest(String name, String email, String region,
                                                String birthYear, String employmentType, String incomeBracket) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "region", region);
        ReflectionTestUtils.setField(request, "birthYear", birthYear);
        ReflectionTestUtils.setField(request, "employmentType", employmentType);
        ReflectionTestUtils.setField(request, "incomeBracket", incomeBracket);
        return request;
    }

    @Test
    void 프로필_수정시_birthYear_employmentType_incomeBracket이_저장값으로_변환되어_반영된다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, " 1990 ", "STUDENT", "OVER_410");

        UserProfileResponse response = userService.updateProfile("user-1", request);

        assertThat(user.getBirthYear()).isEqualTo(1990);
        assertThat(user.getIncome()).isEqualTo(4_500_000L);
        assertThat(user.getEmploymentType()).isEqualTo("STUDENT");
        assertThat(response.getBirthYear()).isEqualTo(1990);
        assertThat(response.getIncomeBracket()).isEqualTo("OVER_410");
        assertThat(response.getEmploymentType()).isEqualTo("STUDENT");
    }

    @Test
    void birthYear가_4자리_숫자가_아니면_400_예외가_발생하고_값이_바뀌지_않는다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").birthYear(1990).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, "1995-03-01", null, null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출생연도는 4자리 숫자로 입력해주세요");
        assertThat(user.getBirthYear()).isEqualTo(1990);
    }

    @Test
    void birthYear가_구버전_연령대_코드면_400_예외가_발생한다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").age(25).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, "YOUTH", null, null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출생연도는 4자리 숫자로 입력해주세요");
        assertThat(user.getBirthYear()).isNull();
        assertThat(user.getAge()).isEqualTo(25);
    }

    @Test
    void birthYear가_범위_밖이면_400_예외가_발생한다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, "1939", null, null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("출생연도는 1940년부터");
        assertThat(user.getBirthYear()).isNull();
    }

    @Test
    void 기존_연령대만_있는_사용자가_birthYear를_입력하면_나이가_출생연도_기준으로_바뀐다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").age(40).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        userService.updateProfile("user-1", updateRequest(null, null, null, "2000", null, null));

        assertThat(user.getBirthYear()).isEqualTo(2000);
        assertThat(user.getAge()).isEqualTo(java.time.Year.now(java.time.ZoneId.of("Asia/Seoul")).getValue() - 2000 - 1);
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
    void incomeBracket이_구간_개편_전_코드면_400_예외가_발생하고_기존_값이_유지된다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").income(1_500_000L).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, null, null, "UNDER_2M");

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 소득 코드입니다");
        assertThat(user.getIncome()).isEqualTo(1_500_000L);
    }

    @Test
    void 구간_개편_전_소득이_저장된_사용자는_프로필_조회시_incomeBracket이_null이다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").income(1_500_000L).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getProfile("user-1");

        assertThat(response.getIncomeBracket()).isNull();
    }

    @Test
    void birthYear가_빈_문자열이면_400_예외가_발생한다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").birthYear(1990).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, "", null, null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출생연도는 4자리 숫자로 입력해주세요");
        assertThat(user.getBirthYear()).isEqualTo(1990);
    }

    @Test
    void birthYear가_공백만이면_400_예외가_발생한다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, "   ", null, null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출생연도는 4자리 숫자로 입력해주세요");
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
                .age(25).birthYear(1995).income(1_500_000L).employmentType("EMPLOYED")
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, null, null, null);

        userService.updateProfile("user-1", request);

        assertThat(user.getBirthYear()).isEqualTo(1995);
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

        UpdateProfileRequest request = updateRequest(null, null, null, null, null, "OVER_410");

        UserProfileResponse response = userService.updateProfile("user-1", request);

        assertThat(user.getAge()).isEqualTo(25);
        assertThat(user.getEmploymentType()).isEqualTo("EMPLOYED");
        assertThat(user.getIncome()).isEqualTo(4_500_000L);
        assertThat(response.getName()).isEqualTo("기존이름");
    }

    @Test
    void region이_정상_코드면_변경된다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").region("BUSAN").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, "SEOUL", null, null, null);

        UserProfileResponse response = userService.updateProfile("user-1", request);

        assertThat(user.getRegion()).isEqualTo("SEOUL");
        assertThat(response.getRegion()).isEqualTo("SEOUL");
    }

    @Test
    void region이_유효하지_않은_코드면_400_예외가_발생하고_값이_바뀌지_않는다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").region("BUSAN").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, "ATLANTIS", null, null, null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 지역 코드입니다");
        assertThat(user.getRegion()).isEqualTo("BUSAN");
    }

    @Test
    void region이_빈_문자열이면_400_예외가_발생한다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").region("BUSAN").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, "", null, null, null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 지역 코드입니다");
        assertThat(user.getRegion()).isEqualTo("BUSAN");
    }

    @Test
    void region이_null이면_기존_값을_유지한다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").region("BUSAN").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, null, null, null);

        UserProfileResponse response = userService.updateProfile("user-1", request);

        assertThat(user.getRegion()).isEqualTo("BUSAN");
        assertThat(response.getRegion()).isEqualTo("BUSAN");
    }

    @Test
    void name이_null이면_기존_값을_유지한다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").name("기존이름").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest(null, null, null, null, null, null);

        UserProfileResponse response = userService.updateProfile("user-1", request);

        assertThat(user.getName()).isEqualTo("기존이름");
        assertThat(response.getName()).isEqualTo("기존이름");
    }

    @Test
    void name이_빈_문자열이면_400_예외가_발생하고_값이_바뀌지_않는다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").name("기존이름").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest("", null, null, null, null, null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이름은 비어 있을 수 없습니다");
        assertThat(user.getName()).isEqualTo("기존이름");
    }

    @Test
    void name이_공백만_있으면_400_예외가_발생하고_값이_바뀌지_않는다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").name("기존이름").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest("   ", null, null, null, null, null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이름은 비어 있을 수 없습니다");
        assertThat(user.getName()).isEqualTo("기존이름");
    }

    @Test
    void name의_앞뒤_공백은_제거되어_저장된다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest("  홍길동  ", null, null, null, null, null);

        UserProfileResponse response = userService.updateProfile("user-1", request);

        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(response.getName()).isEqualTo("홍길동");
    }

    @Test
    void name이_전각_공백만_있으면_400_예외가_발생하고_값이_바뀌지_않는다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").name("기존이름").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest("　　", null, null, null, null, null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이름은 비어 있을 수 없습니다");
        assertThat(user.getName()).isEqualTo("기존이름");
    }

    @Test
    void name의_앞뒤_전각_공백은_제거되어_저장된다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UpdateProfileRequest request = updateRequest("　홍길동　", null, null, null, null, null);

        UserProfileResponse response = userService.updateProfile("user-1", request);

        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(response.getName()).isEqualTo("홍길동");
    }

    @Test
    void name이_20자면_정상_저장된다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        String name20 = "가".repeat(20);
        UpdateProfileRequest request = updateRequest(name20, null, null, null, null, null);

        UserProfileResponse response = userService.updateProfile("user-1", request);

        assertThat(user.getName()).isEqualTo(name20);
        assertThat(response.getName()).isEqualTo(name20);
    }

    @Test
    void name이_21자면_400_예외가_발생하고_값이_바뀌지_않는다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").name("기존이름").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        String name21 = "가".repeat(21);
        UpdateProfileRequest request = updateRequest(name21, null, null, null, null, null);

        assertThatThrownBy(() -> userService.updateProfile("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이름은 20자 이하여야 합니다");
        assertThat(user.getName()).isEqualTo("기존이름");
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

    private UpdateProfileRequest housingRequest(String housingStatus) {
        UpdateProfileRequest request = updateRequest(null, null, null, null, null, null);
        ReflectionTestUtils.setField(request, "housingStatus", housingStatus);
        return request;
    }

    @Test
    void housingStatus가_null이면_기존_값을_유지한다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").housingStatus("HOMEOWNER").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.updateProfile("user-1", housingRequest(null));

        assertThat(user.getHousingStatus()).isEqualTo("HOMEOWNER");
        assertThat(response.getHousingStatus()).isEqualTo("HOMEOWNER");
    }

    @Test
    void housingStatus가_HOMELESS면_저장된다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.updateProfile("user-1", housingRequest("HOMELESS"));

        assertThat(user.getHousingStatus()).isEqualTo("HOMELESS");
        assertThat(response.getHousingStatus()).isEqualTo("HOMELESS");
    }

    @Test
    void housingStatus가_NOT_SPECIFIED면_기존_값이_null로_바뀌고_NOT_SPECIFIED로_응답한다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").housingStatus("HOMEOWNER").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.updateProfile("user-1", housingRequest("NOT_SPECIFIED"));

        assertThat(user.getHousingStatus()).isNull();
        assertThat(response.getHousingStatus()).isEqualTo("NOT_SPECIFIED");
    }

    @Test
    void housingStatus가_빈_문자열이면_400_예외가_발생하고_값이_바뀌지_않는다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").housingStatus("HOMELESS").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateProfile("user-1", housingRequest("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 주택 보유 코드입니다");
        assertThat(user.getHousingStatus()).isEqualTo("HOMELESS");
    }

    @Test
    void housingStatus가_공백만이면_400_예외가_발생한다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateProfile("user-1", housingRequest("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 주택 보유 코드입니다");
    }

    @Test
    void housingStatus가_유효하지_않은_코드면_400_예외가_발생하고_값이_바뀌지_않는다() {
        User user = User.builder().id("user-1").provider("KAKAO").providerId("kakao-1").housingStatus("HOMELESS").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateProfile("user-1", housingRequest("RENT")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 주택 보유 코드입니다");
        assertThat(user.getHousingStatus()).isEqualTo("HOMELESS");
    }
}
