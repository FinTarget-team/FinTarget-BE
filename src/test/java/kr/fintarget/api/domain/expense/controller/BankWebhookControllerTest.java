package kr.fintarget.api.domain.expense.controller;

import kr.fintarget.api.common.exception.GlobalExceptionHandler;
import kr.fintarget.api.domain.expense.dto.BankTransactionResult;
import kr.fintarget.api.domain.expense.service.ExpenseService;
import kr.fintarget.api.security.CustomAuthenticationEntryPoint;
import kr.fintarget.api.security.JwtFilter;
import kr.fintarget.api.security.JwtUtil;
import kr.fintarget.api.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 SecurityConfig 필터 체인을 거쳐 웹훅 키 인증과 입력 검증이 동작하는지 확인한다.
 */
@WebMvcTest(BankWebhookController.class)
@Import({SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {"BANK_WEBHOOK_KEY=test-webhook-key", "ADMIN_API_KEY=test-admin-key"})
class BankWebhookControllerTest {

    private static final String URL = "/api/webhooks/bank-transactions";
    private static final String KEY = "test-webhook-key";
    private static final UUID TRANSACTION_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final UUID USER_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    @MockitoBean
    private JwtUtil jwtUtil;

    private static String item(String transactionId, String userId, String amount, String category, String spentAt) {
        return """
                {"transactionId": %s, "userId": %s, "amount": %s, "category": %s, "description": "김밥천국", "spentAt": %s}
                """.formatted(transactionId, userId, amount, category, spentAt);
    }

    private static String validItem() {
        return item("\"" + TRANSACTION_ID + "\"", "\"" + USER_ID + "\"", "12000", "\"식비\"", "\"2026-11-02\"");
    }

    private ResultActions send(String body, String key) throws Exception {
        var request = post(URL).contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header("X-Bank-Webhook-Key", key);
        }
        return mockMvc.perform(request);
    }

    @Test
    void 웹훅_키가_맞으면_거래별_처리_결과를_반환한다() throws Exception {
        when(expenseService.receiveBankTransactions(any())).thenReturn(
                List.of(new BankTransactionResult(TRANSACTION_ID, BankTransactionResult.Result.CREATED)));

        send("[" + validItem() + "]", KEY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].transactionId").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.data[0].result").value("CREATED"));
    }

    @Test
    void 웹훅_키가_틀리면_403이고_저장하지_않는다() throws Exception {
        send("[" + validItem() + "]", "wrong-key")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("웹훅 키가 올바르지 않습니다."));

        verify(expenseService, never()).receiveBankTransactions(any());
    }

    @Test
    void 웹훅_키가_없으면_403() throws Exception {
        send("[" + validItem() + "]", null).andExpect(status().isForbidden());

        verify(expenseService, never()).receiveBankTransactions(any());
    }

    @Test
    void 사용자_JWT로는_호출할_수_없다() throws Exception {
        when(jwtUtil.validateToken("user-token")).thenReturn(true);
        when(jwtUtil.getUserId("user-token")).thenReturn(USER_ID.toString());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("[" + validItem() + "]")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());

        verify(expenseService, never()).receiveBankTransactions(any());
    }

    @Test
    void 관리자_키로는_호출할_수_없다() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("[" + validItem() + "]")
                        .header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isForbidden());

        verify(expenseService, never()).receiveBankTransactions(any());
    }

    @Test
    void 금액이_0이면_400이고_저장하지_않는다() throws Exception {
        String body = "[" + item("\"" + TRANSACTION_ID + "\"", "\"" + USER_ID + "\"", "0", "\"식비\"", "\"2026-11-02\"") + "]";

        send(body, KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지출 금액은 0보다 커야 합니다."));

        verify(expenseService, never()).receiveBankTransactions(any());
    }

    @Test
    void 거래_ID가_없으면_400() throws Exception {
        String body = "[" + item("null", "\"" + USER_ID + "\"", "12000", "\"식비\"", "\"2026-11-02\"") + "]";

        send(body, KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("거래 ID를 입력해주세요."));
    }

    @Test
    void 사용자_ID가_없으면_400() throws Exception {
        String body = "[" + item("\"" + TRANSACTION_ID + "\"", "null", "12000", "\"식비\"", "\"2026-11-02\"") + "]";

        send(body, KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("사용자 ID를 입력해주세요."));
    }

    @Test
    void 카테고리가_공백이면_400() throws Exception {
        String body = "[" + item("\"" + TRANSACTION_ID + "\"", "\"" + USER_ID + "\"", "12000", "\" \"", "\"2026-11-02\"") + "]";

        send(body, KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지출 카테고리를 입력해주세요."));
    }

    @Test
    void 지출_날짜가_없으면_400() throws Exception {
        String body = "[" + item("\"" + TRANSACTION_ID + "\"", "\"" + USER_ID + "\"", "12000", "\"식비\"", "null") + "]";

        send(body, KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지출 날짜를 입력해주세요."));
    }

    @Test
    void 목록에_null_항목이_있으면_400() throws Exception {
        send("[" + validItem() + ", null]", KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("거래 목록에 빈 항목(null)이 있습니다."));

        verify(expenseService, never()).receiveBankTransactions(any());
    }

    @Test
    void 한_번에_1000건을_넘으면_400() throws Exception {
        String body = IntStream.range(0, BankWebhookController.MAX_TRANSACTIONS_PER_REQUEST + 1)
                .mapToObj(i -> validItem())
                .collect(Collectors.joining(",", "[", "]"));

        send(body, KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("거래는 한 번에 최대 1000건까지 보낼 수 있습니다."));

        verify(expenseService, never()).receiveBankTransactions(any());
    }

    @Test
    void 존재하지_않는_사용자면_서비스의_400_메시지를_그대로_반환한다() throws Exception {
        when(expenseService.receiveBankTransactions(any()))
                .thenThrow(new IllegalArgumentException("존재하지 않는 사용자입니다: " + USER_ID));

        send("[" + validItem() + "]", KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다: " + USER_ID));
    }

    @Test
    void 빈_목록이면_빈_결과를_반환한다() throws Exception {
        when(expenseService.receiveBankTransactions(List.of())).thenReturn(List.of());

        send("[]", KEY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
