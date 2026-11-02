package kr.fintarget.mockbank.transaction.controller;

import kr.fintarget.mockbank.config.SecurityConfig;
import kr.fintarget.mockbank.transaction.service.DemoDataService;
import kr.fintarget.mockbank.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionPageController.class)
@Import({SecurityConfig.class, TransactionPageControllerTest.FixedClockConfig.class})
class TransactionPageControllerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final String USER_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(TODAY.atStartOfDay(SEOUL).plusHours(12).toInstant(), SEOUL);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private DemoDataService demoDataService;

    private MockHttpServletRequestBuilder payment(String userId, String amount, String category, String spentAt) {
        return post("/transactions")
                .with(httpBasic("test-operator", "test-password"))
                .with(csrf())
                .param("userId", userId)
                .param("amount", amount)
                .param("category", category)
                .param("description", "김밥천국")
                .param("spentAt", spentAt);
    }

    private MockHttpServletRequestBuilder validPayment() {
        return payment(USER_ID, "12000", "식비", "2026-11-15");
    }

    @Test
    void 인증_없이_접근하면_401() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isUnauthorized());
    }

    @Test
    void 비밀번호가_틀리면_401() throws Exception {
        mockMvc.perform(get("/").with(httpBasic("test-operator", "wrong"))).andExpect(status().isUnauthorized());
    }

    @Test
    void 화면에_입력_폼과_시연_버튼_3개가_보인다() throws Exception {
        given(transactionService.getRecentTransactions()).willReturn(List.of());

        mockMvc.perform(get("/").with(httpBasic("test-operator", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("결제 발생")))
                .andExpect(content().string(containsString("기준선 생성")))
                .andExpect(content().string(containsString("랜덤 거래 10건")))
                .andExpect(content().string(containsString("식비 급증")))
                .andExpect(content().string(not(containsString("외식비"))));
    }

    @Test
    void 정상_입력이면_저장하고_입력한_사용자로_화면에_돌아간다() throws Exception {
        mockMvc.perform(validPayment())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?userId=" + USER_ID))
                .andExpect(flash().attribute("message", containsString("결제가 발생했습니다")));

        verify(transactionService).create(UUID.fromString(USER_ID), 12_000L, "식비", "김밥천국", TODAY);
    }

    @Test
    void CSRF_토큰이_없으면_403() throws Exception {
        mockMvc.perform(post("/transactions").with(httpBasic("test-operator", "test-password"))
                        .param("userId", USER_ID).param("amount", "1000").param("category", "식비").param("spentAt", "2026-11-15"))
                .andExpect(status().isForbidden());
        verify(transactionService, never()).create(any(), anyLong(), anyString(), any(), any());
    }

    @Test
    void 금액이_0이면_400과_한국어_메시지() throws Exception {
        mockMvc.perform(payment(USER_ID, "0", "식비", "2026-11-15"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("금액은 0보다 커야 합니다.")));
        verify(transactionService, never()).create(any(), anyLong(), anyString(), any(), any());
    }

    @Test
    void 금액이_숫자가_아니면_400과_한국어_메시지() throws Exception {
        mockMvc.perform(payment(USER_ID, "abc", "식비", "2026-11-15"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("금액은 숫자로 입력해주세요.")));
    }

    @Test
    void 필수값이_빠지면_항목별_한국어_메시지() throws Exception {
        mockMvc.perform(post("/transactions").with(httpBasic("test-operator", "test-password")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("핀타겟 사용자 UUID를 입력해주세요.")))
                .andExpect(content().string(containsString("금액을 입력해주세요.")))
                .andExpect(content().string(containsString("카테고리를 선택해주세요.")))
                .andExpect(content().string(containsString("결제 날짜를 입력해주세요.")));
    }

    @Test
    void 결제_날짜가_오늘보다_미래면_거부한다() throws Exception {
        mockMvc.perform(payment(USER_ID, "12000", "식비", "2026-11-16"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("결제 날짜는 오늘 이후일 수 없습니다.")));
        verify(transactionService, never()).create(any(), anyLong(), anyString(), any(), any());
    }

    @Test
    void 지원하지_않는_카테고리면_거부한다() throws Exception {
        mockMvc.perform(payment(USER_ID, "12000", "외식", "2026-11-15"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("지원하지 않는 카테고리입니다.")));
    }

    @Test
    void UUID_형식이_아니면_거부한다() throws Exception {
        mockMvc.perform(payment("user-1", "12000", "식비", "2026-11-15"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("핀타겟 사용자 UUID 형식이 올바르지 않습니다.")));
    }

    @Test
    void 시연_버튼은_UUID가_올바르지_않으면_생성하지_않는다() throws Exception {
        mockMvc.perform(post("/demo/baseline").with(httpBasic("test-operator", "test-password")).with(csrf())
                        .param("userId", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("사용자 UUID")));
        verify(demoDataService, never()).generateBaseline(any());
    }

    @Test
    void 기준선_버튼은_생성_건수를_알려준다() throws Exception {
        given(demoDataService.generateBaseline(UUID.fromString(USER_ID))).willReturn(60);

        mockMvc.perform(post("/demo/baseline").with(httpBasic("test-operator", "test-password")).with(csrf())
                        .param("userId", USER_ID))
                .andExpect(redirectedUrl("/?userId=" + USER_ID))
                .andExpect(flash().attribute("message", containsString("60건")));
    }

    @Test
    void 기준선_없이_식비_급증을_누르면_안내_메시지를_보여준다() throws Exception {
        given(demoDataService.generateFoodSurge(eq(UUID.fromString(USER_ID))))
                .willThrow(new IllegalStateException("이전 달 식비 기록이 없습니다. 기준선을 먼저 생성해 주세요."));

        mockMvc.perform(post("/demo/food-surge").with(httpBasic("test-operator", "test-password")).with(csrf())
                        .param("userId", USER_ID))
                .andExpect(redirectedUrl("/?userId=" + USER_ID))
                .andExpect(flash().attribute("error", containsString("기준선을 먼저 생성해 주세요")));
    }
}
