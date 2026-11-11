package kr.fintarget.api.domain.realtime;

import kr.fintarget.api.common.exception.GlobalExceptionHandler;
import kr.fintarget.api.domain.expense.dto.ExpenseResponse;
import kr.fintarget.api.security.CustomAuthenticationEntryPoint;
import kr.fintarget.api.security.JwtFilter;
import kr.fintarget.api.security.JwtUtil;
import kr.fintarget.api.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 SecurityConfig 필터 체인과 실제 SseEmitterRegistry로 SSE 연결과 이벤트 전달을 확인한다.
 */
@WebMvcTest(RealtimeEventController.class)
@Import({SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class, GlobalExceptionHandler.class,
        SseEmitterRegistry.class})
@TestPropertySource(properties = {"BANK_WEBHOOK_KEY=test-webhook-key", "ADMIN_API_KEY=test-admin-key"})
class RealtimeEventControllerTest {

    // AiChatControllerTest와 같은 이유로, MockMvc에서만 JwtFilter의 서블릿 필터 자동 등록을 끈다
    @TestConfiguration
    static class JwtFilterRegistration {
        @Bean
        FilterRegistrationBean<JwtFilter> disableJwtFilterServletRegistration(JwtFilter jwtFilter) {
            FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>(jwtFilter);
            registration.setEnabled(false);
            return registration;
        }
    }

    private static final String URL = "/api/events/stream";
    // 레지스트리는 테스트 컨텍스트에서 공유되므로, 테스트마다 다른 사용자를 써서 서로의 연결이 섞이지 않게 한다
    private String userA;
    private String userB;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SseEmitterRegistry registry;

    @MockitoBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        userA = UUID.randomUUID().toString();
        userB = UUID.randomUUID().toString();
        when(jwtUtil.validateAccessToken("token-a")).thenReturn(true);
        when(jwtUtil.getUserId("token-a")).thenReturn(userA);
        when(jwtUtil.validateAccessToken("token-b")).thenReturn(true);
        when(jwtUtil.getUserId("token-b")).thenReturn(userB);
    }

    // SSE는 항상 UTF-8이지만 Content-Type에 charset이 없어 MockHttpServletResponse 기본값(ISO-8859-1)으로 읽히므로 직접 지정한다
    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private MvcResult connect(String token) throws Exception {
        return mockMvc.perform(get(URL).accept(MediaType.TEXT_EVENT_STREAM).header("Authorization", "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private static ExpenseResponse expense(long amount, String description) {
        return ExpenseResponse.of(UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7"), amount, "식비", description,
                LocalDate.of(2026, 11, 4), LocalDateTime.of(2026, 11, 4, 14, 30));
    }

    @Test
    void 토큰이_없으면_401이고_연결하지_않는다() throws Exception {
        mockMvc.perform(get(URL).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());

        assertThat(registry.connectionCount(userA)).isZero();
    }

    @Test
    void 연결하면_connected_이벤트를_받는다() throws Exception {
        MvcResult result = connect("token-a");

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(body(result))
                .contains("event:connected")
                .contains("\"userId\":\"" + userA + "\"");
        assertThat(registry.connectionCount(userA)).isEqualTo(1);
    }

    @Test
    void 이벤트는_그_사용자의_연결로만_전달된다() throws Exception {
        MvcResult streamA = connect("token-a");
        MvcResult streamB = connect("token-b");

        registry.sendToUser(userA, "expense-created", Map.of("expenses", List.of(expense(15000L, "스타벅스"))));

        assertThat(body(streamA))
                .contains("event:expense-created")
                .contains("\"amount\":15000")
                .contains("\"description\":\"스타벅스\"")
                .contains("\"spentAt\":\"2026-11-04\"");
        assertThat(body(streamB)).doesNotContain("expense-created");
    }

    @Test
    void 같은_계정의_여러_기기_연결에_모두_전달된다() throws Exception {
        MvcResult phone = connect("token-a");
        MvcResult tablet = connect("token-a");

        registry.sendToUser(userA, "expense-created", Map.of("expenses", List.of(expense(3000L, "편의점"))));

        assertThat(body(phone)).contains("\"amount\":3000");
        assertThat(body(tablet)).contains("\"amount\":3000");
    }

    @Test
    void 사용자당_연결이_최대치를_넘으면_가장_오래된_연결을_닫는다() throws Exception {
        for (int i = 0; i < SseEmitterRegistry.MAX_CONNECTIONS_PER_USER + 2; i++) {
            connect("token-b");
        }

        assertThat(registry.connectionCount(userB)).isEqualTo(SseEmitterRegistry.MAX_CONNECTIONS_PER_USER);
    }

    @Test
    void 연결이_없는_사용자에게_보내도_예외가_없다() {
        registry.sendToUser("no-connection-user", "expense-created", Map.of("expenses", List.of()));
    }

    @Test
    void heartbeat는_주석_줄로_전달되어_앱_이벤트로_처리되지_않는다() throws Exception {
        MvcResult stream = connect("token-a");

        registry.sendHeartbeat();

        assertThat(body(stream)).contains("\n:heartbeat\n");
    }
}
