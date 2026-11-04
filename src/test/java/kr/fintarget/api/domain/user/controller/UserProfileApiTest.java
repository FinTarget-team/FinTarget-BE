package kr.fintarget.api.domain.user.controller;

import kr.fintarget.api.common.exception.GlobalExceptionHandler;
import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.simulation.repository.SimulationRepository;
import kr.fintarget.api.domain.user.UserController;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.domain.user.service.UserService;
import kr.fintarget.api.security.BlacklistedTokenRepository;
import kr.fintarget.api.security.CustomAuthenticationEntryPoint;
import kr.fintarget.api.security.JwtFilter;
import kr.fintarget.api.security.JwtUtil;
import kr.fintarget.api.security.SecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프로필 조회·수정 API를 JSON 요청·응답 기준으로 확인한다 (#173).
 * 이메일은 소셜 로그인에서 받은 값으로만 저장되므로, 수정 요청에 email을 보내도 무시되어야 한다.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({UserService.class, GlobalExceptionHandler.class, SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class})
class UserProfileApiTest {

    private static final String USER_ID = "user-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private GoalRepository goalRepository;
    @MockitoBean
    private ExpenseRepository expenseRepository;
    @MockitoBean
    private UserPolicyRepository userPolicyRepository;
    @MockitoBean
    private SimulationRepository simulationRepository;
    @MockitoBean
    private BlacklistedTokenRepository blacklistedTokenRepository;
    @MockitoBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 수정_요청에_email을_보내도_무시되고_기존_이메일이_유지된다() throws Exception {
        User user = user(SocialProvider.KAKAO, "social@kakao.com");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        mockMvc.perform(put("/users/me/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"새이름\", \"email\": \"hacker@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.name").value("새이름"))
                .andExpect(jsonPath("$.data.email").value("social@kakao.com"));

        assertThat(user.getEmail()).isEqualTo("social@kakao.com");
    }

    @Test
    void 이메일이_없는_사용자가_수정_요청에_email만_보내도_이메일은_null로_남는다() throws Exception {
        User user = user(SocialProvider.NAVER, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        mockMvc.perform(put("/users/me/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"new@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(org.hamcrest.Matchers.nullValue()));

        assertThat(user.getEmail()).isNull();
    }

    @Test
    void 프로필_조회_응답에_provider와_이메일이_내려간다() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(SocialProvider.KAKAO, "social@kakao.com")));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("KAKAO"))
                .andExpect(jsonPath("$.data.email").value("social@kakao.com"));
    }

    @Test
    void 이메일이_없으면_조회_응답의_email은_null이고_오류가_나지_않는다() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(SocialProvider.NAVER, null)));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("NAVER"))
                .andExpect(jsonPath("$.data.email").value(org.hamcrest.Matchers.nullValue()));
    }

    private static User user(SocialProvider provider, String email) {
        return User.builder().id(USER_ID).provider(provider).providerId("provider-1").name("기존이름").email(email).build();
    }
}
