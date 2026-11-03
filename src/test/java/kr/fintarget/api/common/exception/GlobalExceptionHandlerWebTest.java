package kr.fintarget.api.common.exception;

import kr.fintarget.api.domain.expense.controller.ExpenseController;
import kr.fintarget.api.domain.expense.service.ExpenseService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 클라이언트 요청 형식 오류가 500이 아니라 4xx로, 예상하지 못한 오류는 내부 메시지 없이 500으로 나가는지 확인한다.
 * 지출 API를 대표로 사용한다.
 */
@WebMvcTest(ExpenseController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class})
class GlobalExceptionHandlerWebTest {

    private static final String USER_ID = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

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
    void JSON_문법이_깨지면_400() throws Exception {
        mockMvc.perform(post("/api/expenses/sync").contentType(MediaType.APPLICATION_JSON).content("[{\"amount\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("요청 본문의 형식이 올바르지 않습니다.")));
    }

    @Test
    void 본문의_날짜_형식이_틀리면_400() throws Exception {
        mockMvc.perform(post("/api/expenses/sync").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"amount\": 1000, \"category\": \"식비\", \"spentAt\": \"2026/11/01\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void 필수_쿼리_파라미터가_없으면_400() throws Exception {
        mockMvc.perform(get("/api/expenses/period").param("start", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("필수 파라미터가 없습니다: end"));
    }

    @Test
    void 쿼리_파라미터_날짜_형식이_틀리면_400() throws Exception {
        mockMvc.perform(get("/api/expenses/stats").param("start", "2026/11/01").param("end", "2026-11-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("파라미터 형식이 올바르지 않습니다: start"));
    }

    @Test
    void 지원하지_않는_메서드면_405() throws Exception {
        mockMvc.perform(patch("/api/expenses/sync"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void JSON이_아닌_본문이면_415() throws Exception {
        mockMvc.perform(post("/api/expenses/sync").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void 없는_경로면_404() throws Exception {
        mockMvc.perform(get("/api/expenses/unknown/path/x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("요청한 API를 찾을 수 없습니다."));
    }

    @Test
    void 예상하지_못한_오류는_내부_메시지를_숨기고_500() throws Exception {
        when(expenseService.getExpenses(any())).thenThrow(new RuntimeException("could not execute statement; SQL [insert into expense ...]"));

        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }
}
