package kr.fintarget.api.domain.expense.controller;

import kr.fintarget.api.common.exception.GlobalExceptionHandler;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지출 동기화 요청의 입력 검증을 확인한다.
 * 인증은 검증 대상이 아니므로 시큐리티 필터를 끄고 인증 사용자를 직접 넣는다.
 */
@WebMvcTest(ExpenseController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class})
class ExpenseControllerValidationTest {

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

    private ResultActions sync(String body) throws Exception {
        return mockMvc.perform(post("/api/expenses/sync").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void expectBadRequest(String body, String message) throws Exception {
        sync(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(message));
        verify(expenseService, never()).syncExpenses(any(), any());
    }

    @Test
    void 올바른_요청은_동기화한다() throws Exception {
        sync("[{\"amount\": 15000, \"category\": \"식비\", \"description\": null, \"spentAt\": \"2026-10-12\"}]")
                .andExpect(status().isOk());
        verify(expenseService).syncExpenses(any(), any());
    }

    @Test
    void 지출_날짜가_없으면_400() throws Exception {
        expectBadRequest("[{\"amount\": 15000, \"category\": \"식비\"}]", "지출 날짜를 입력해주세요.");
    }

    @Test
    void 지출_금액이_없으면_400() throws Exception {
        expectBadRequest("[{\"category\": \"식비\", \"spentAt\": \"2026-10-12\"}]", "지출 금액을 입력해주세요.");
    }

    @Test
    void 지출_금액이_0이하면_400() throws Exception {
        expectBadRequest("[{\"amount\": 0, \"category\": \"식비\", \"spentAt\": \"2026-10-12\"}]", "지출 금액은 0보다 커야 합니다.");
    }

    @Test
    void 카테고리가_비어_있으면_400() throws Exception {
        expectBadRequest("[{\"amount\": 15000, \"category\": \" \", \"spentAt\": \"2026-10-12\"}]", "지출 카테고리를 입력해주세요.");
    }

    @Test
    void 목록에_null_항목이_있으면_400() throws Exception {
        expectBadRequest("[null]", "지출 내역에 빈 항목(null)이 있습니다.");
    }

    @Test
    void 두번째_항목만_잘못돼도_전체를_거부한다() throws Exception {
        expectBadRequest("[{\"amount\": 15000, \"category\": \"식비\", \"spentAt\": \"2026-10-12\"},"
                + "{\"amount\": 3000, \"category\": \"교통\"}]", "지출 날짜를 입력해주세요.");
    }
}
