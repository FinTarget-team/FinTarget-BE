package kr.fintarget.mockbank.transaction.controller;

import kr.fintarget.mockbank.config.SecurityConfig;
import kr.fintarget.mockbank.transaction.entity.Transaction;
import kr.fintarget.mockbank.transaction.service.DemoDataService;
import kr.fintarget.mockbank.transaction.service.TransactionService;
import kr.fintarget.mockbank.webhook.DeliveryResult;
import kr.fintarget.mockbank.webhook.WebhookDeliveryService;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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

    @MockitoBean
    private WebhookDeliveryService webhookDeliveryService;

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
    void 정상_입력이면_저장하고_핀타겟으로_보낸_뒤_입력한_사용자로_화면에_돌아간다() throws Exception {
        Transaction saved = mock(Transaction.class);
        given(transactionService.create(UUID.fromString(USER_ID), 12_000L, "식비", "김밥천국", TODAY)).willReturn(saved);
        given(webhookDeliveryService.deliver(List.of(saved))).willReturn(new DeliveryResult(1, 0, 0, null));

        mockMvc.perform(validPayment())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?userId=" + USER_ID))
                .andExpect(flash().attribute("message", containsString("결제가 발생했습니다")))
                .andExpect(flash().attribute("message", containsString("저장 1건")))
                .andExpect(flash().attributeCount(1));

        verify(webhookDeliveryService).deliver(List.of(saved));
    }

    @Test
    void 핀타겟_전송에_실패하면_실패_사유와_재전송_안내를_보여준다() throws Exception {
        Transaction saved = mock(Transaction.class);
        given(transactionService.create(any(), anyLong(), anyString(), any(), any())).willReturn(saved);
        given(webhookDeliveryService.deliver(List.of(saved)))
                .willReturn(new DeliveryResult(0, 0, 1, "핀타겟 서버에 연결하지 못했습니다. 서버 주소와 실행 여부를 확인해 주세요."));

        mockMvc.perform(validPayment())
                .andExpect(redirectedUrl("/?userId=" + USER_ID))
                .andExpect(flash().attribute("message", containsString("실패 1건")))
                .andExpect(flash().attribute("error", containsString("핀타겟 서버에 연결하지 못했습니다")))
                .andExpect(flash().attribute("error", containsString("재전송")));
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
        List<Transaction> created = Collections.nCopies(60, mock(Transaction.class));
        given(demoDataService.generateBaseline(UUID.fromString(USER_ID))).willReturn(created);
        given(webhookDeliveryService.deliver(created)).willReturn(new DeliveryResult(58, 2, 0, null));

        mockMvc.perform(post("/demo/baseline").with(httpBasic("test-operator", "test-password")).with(csrf())
                        .param("userId", USER_ID))
                .andExpect(redirectedUrl("/?userId=" + USER_ID))
                .andExpect(flash().attribute("message", containsString("기준선 거래 60건")))
                .andExpect(flash().attribute("message", containsString("핀타겟 전송 60건 중 저장 58건, 중복 2건")));
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

    @Test
    void 식비_급증이_이미_충족돼_만든_거래가_없으면_전송하지_않는다() throws Exception {
        given(demoDataService.generateFoodSurge(UUID.fromString(USER_ID)))
                .willReturn(new DemoDataService.SurgeResult(List.of(), 30_900, 76_100));

        mockMvc.perform(post("/demo/food-surge").with(httpBasic("test-operator", "test-password")).with(csrf())
                        .param("userId", USER_ID))
                .andExpect(flash().attribute("message", containsString("새로 생성하지 않았습니다")));

        verify(webhookDeliveryService, never()).deliver(any());
    }

    @Test
    void 재전송_버튼은_미전송_실패_거래를_다시_보낸다() throws Exception {
        given(webhookDeliveryService.resendUnsent()).willReturn(new DeliveryResult(3, 1, 0, null));

        mockMvc.perform(post("/webhooks/resend").with(httpBasic("test-operator", "test-password")).with(csrf())
                        .param("userId", USER_ID))
                .andExpect(redirectedUrl("/?userId=" + USER_ID))
                .andExpect(flash().attribute("message", containsString("다시 보냈습니다")))
                .andExpect(flash().attribute("message", containsString("저장 3건, 중복 1건")));
    }

    @Test
    void 재전송할_거래가_없으면_안내만_보여준다() throws Exception {
        given(webhookDeliveryService.resendUnsent()).willReturn(DeliveryResult.EMPTY);

        mockMvc.perform(post("/webhooks/resend").with(httpBasic("test-operator", "test-password")).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("message", "다시 보낼 미전송·실패 거래가 없습니다."));
    }

    @Test
    void 화면에_재전송_버튼과_미전송_건수가_보인다() throws Exception {
        given(transactionService.getRecentTransactions()).willReturn(List.of());
        given(transactionService.countUnsentTransactions()).willReturn(4L);

        mockMvc.perform(get("/").with(httpBasic("test-operator", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("미전송·실패 거래 재전송 (4건)")));
    }
}
