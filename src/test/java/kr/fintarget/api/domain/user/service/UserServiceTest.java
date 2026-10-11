package kr.fintarget.api.domain.user.service;

import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.simulation.repository.SimulationRepository;
import kr.fintarget.api.domain.user.dto.UserProfileResponse;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
}
