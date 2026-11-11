package kr.fintarget.api.domain.goal.controller;

import kr.fintarget.api.common.exception.GlobalExceptionHandler;
import kr.fintarget.api.domain.goal.service.GoalService;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 목표 생성·수정 요청의 입력 검증을 확인한다.
 * 인증은 검증 대상이 아니므로 시큐리티 필터를 끄고 인증 사용자를 직접 넣는다.
 */
@WebMvcTest(GoalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class})
class GoalControllerValidationTest {

    private static final String USER_ID = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoalService goalService;

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

    private static final String VALID = "{\"title\": \"내 집 마련\", \"targetAmount\": 50000000, \"currentAmount\": 0, \"deadline\": \"2028-12-31\"}";

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post("/api/goals").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void expectCreateBadRequest(String body, String message) throws Exception {
        create(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(message));
        verify(goalService, never()).createGoal(any(), any());
    }

    @Test
    void 올바른_요청은_목표를_생성한다() throws Exception {
        create(VALID).andExpect(status().isCreated());
        verify(goalService).createGoal(any(), any());
    }

    @Test
    void 제목이_없으면_400() throws Exception {
        expectCreateBadRequest("{\"targetAmount\": 50000000, \"currentAmount\": 0, \"deadline\": \"2028-12-31\"}", "목표 제목을 입력해주세요.");
    }

    @Test
    void 제목이_공백이면_400() throws Exception {
        expectCreateBadRequest("{\"title\": \"  \", \"targetAmount\": 50000000, \"currentAmount\": 0, \"deadline\": \"2028-12-31\"}", "목표 제목을 입력해주세요.");
    }

    private static String bodyWithTitle(String title) {
        return "{\"title\": \"" + title + "\", \"targetAmount\": 50000000, \"currentAmount\": 0, \"deadline\": \"2028-12-31\"}";
    }

    @Test
    void 제목이_정확히_30자면_생성한다() throws Exception {
        create(bodyWithTitle("가".repeat(30))).andExpect(status().isCreated());
        verify(goalService).createGoal(any(), any());
    }

    @Test
    void 제목이_30자를_넘으면_400() throws Exception {
        expectCreateBadRequest(bodyWithTitle("가".repeat(31)), "목표 제목은 30자 이하로 입력해주세요.");
    }

    @Test
    void DB_컬럼_길이를_넘는_제목도_500이_아니라_400() throws Exception {
        expectCreateBadRequest(bodyWithTitle("a".repeat(256)), "목표 제목은 30자 이하로 입력해주세요.");
    }

    @Test
    void 앞뒤_공백은_길이에서_빼고_제거한_제목을_서비스에_넘긴다() throws Exception {
        create(bodyWithTitle("  " + "가".repeat(30) + "  ")).andExpect(status().isCreated());
        verify(goalService).createGoal(any(), argThat(request -> request.title().equals("가".repeat(30))));
    }

    @Test
    void 수정_요청도_제목이_30자를_넘으면_400() throws Exception {
        mockMvc.perform(put("/api/goals").contentType(MediaType.APPLICATION_JSON).content(bodyWithTitle("가".repeat(31))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("목표 제목은 30자 이하로 입력해주세요."));
        verify(goalService, never()).updateGoal(any(), any());
    }

    @Test
    void 수정_요청도_앞뒤_공백을_제거한_제목을_서비스에_넘긴다() throws Exception {
        mockMvc.perform(put("/api/goals").contentType(MediaType.APPLICATION_JSON).content(bodyWithTitle(" 비상금 마련 ")))
                .andExpect(status().isOk());
        verify(goalService).updateGoal(any(), argThat(request -> request.title().equals("비상금 마련")));
    }

    @Test
    void 현재_모은_금액이_없으면_400() throws Exception {
        expectCreateBadRequest("{\"title\": \"내 집 마련\", \"targetAmount\": 50000000, \"deadline\": \"2028-12-31\"}", "현재 모은 금액을 입력해주세요.");
    }

    @Test
    void 현재_모은_금액이_음수면_400() throws Exception {
        expectCreateBadRequest("{\"title\": \"내 집 마련\", \"targetAmount\": 50000000, \"currentAmount\": -1, \"deadline\": \"2028-12-31\"}", "현재 모은 금액은 0 이상이어야 합니다.");
    }

    @Test
    void 기한이_없으면_400() throws Exception {
        expectCreateBadRequest("{\"title\": \"내 집 마련\", \"targetAmount\": 50000000, \"currentAmount\": 0}", "목표 기한을 입력해주세요.");
    }

    @Test
    void 수정_요청도_같은_규칙으로_검증한다() throws Exception {
        mockMvc.perform(put("/api/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"내 집 마련\", \"targetAmount\": 50000000, \"currentAmount\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("목표 기한을 입력해주세요."));
        verify(goalService, never()).updateGoal(any(), any());
    }
}
