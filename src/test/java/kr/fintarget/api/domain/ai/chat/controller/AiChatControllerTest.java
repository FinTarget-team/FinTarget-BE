package kr.fintarget.api.domain.ai.chat.controller;

import kr.fintarget.api.common.exception.GlobalExceptionHandler;
import kr.fintarget.api.domain.ai.chat.dto.AiChatResponse;
import kr.fintarget.api.domain.ai.chat.service.AiChatConflictException;
import kr.fintarget.api.domain.ai.chat.service.AiChatNotFoundException;
import kr.fintarget.api.domain.ai.chat.service.AiChatService;
import kr.fintarget.api.domain.ai.client.AiCallFailedException;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
import kr.fintarget.api.security.CustomAuthenticationEntryPoint;
import kr.fintarget.api.security.JwtFilter;
import kr.fintarget.api.security.JwtUtil;
import kr.fintarget.api.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 SecurityConfig 필터 체인과 GlobalExceptionHandler를 거쳐 상태 코드와 응답 형식을 확인한다. AiChatService는 목.
 */
@WebMvcTest(AiChatController.class)
@Import({SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class, GlobalExceptionHandler.class,
        AiChatControllerTest.JwtFilterRegistration.class})
class AiChatControllerTest {

    /**
     * JwtFilter는 @Component라 서블릿 필터로도 자동 등록된다. MockMvc에서는 이 등록이 보안 필터 체인보다 먼저 실행되어
     * OncePerRequestFilter가 체인 안의 실행을 건너뛰고, 그 사이 SecurityContext가 초기화되어 인증이 사라진다.
     * 운영에서는 보안 체인(order -100)이 먼저 실행되므로 테스트에서만 서블릿 등록을 끈다.
     */
    @TestConfiguration
    static class JwtFilterRegistration {
        @Bean
        FilterRegistrationBean<JwtFilter> disableJwtFilterServletRegistration(JwtFilter jwtFilter) {
            FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>(jwtFilter);
            registration.setEnabled(false);
            return registration;
        }
    }

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CONVERSATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiChatService aiChatService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateToken("token")).thenReturn(true);
        when(jwtUtil.getUserId("token")).thenReturn(USER_ID.toString());
    }

    private ResultActions ask(String body) throws Exception {
        return mockMvc.perform(post("/api/policies/{policyId}/ai-chat", POLICY_ID)
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 질문하면_대화_ID_답변_남은_질문_수를_반환한다() throws Exception {
        when(aiChatService.ask(USER_ID, POLICY_ID, "신청할 수 있나요?", null))
                .thenReturn(new AiChatResponse(CONVERSATION_ID, "답변", 9, false));

        ask("{\"question\":\"  신청할 수 있나요?  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.conversationId").value(CONVERSATION_ID.toString()))
                .andExpect(jsonPath("$.data.answer").value("답변"))
                .andExpect(jsonPath("$.data.remainingTurns").value(9))
                .andExpect(jsonPath("$.data.sourceLimited").value(false));
    }

    @Test
    void conversationId를_넘기면_대화를_이어간다() throws Exception {
        when(aiChatService.ask(USER_ID, POLICY_ID, "q", CONVERSATION_ID))
                .thenReturn(new AiChatResponse(CONVERSATION_ID, "a", 8, false));

        ask("{\"question\":\"q\",\"conversationId\":\"" + CONVERSATION_ID + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingTurns").value(8));
    }

    @Test
    void 질문이_비었거나_공백이면_400() throws Exception {
        ask("{\"question\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문을 입력해주세요."));
        ask("{}").andExpect(status().isBadRequest());
        verifyNoInteractions(aiChatService);
    }

    @Test
    void 질문이_500자를_넘으면_400이고_500자는_허용한다() throws Exception {
        ask("{\"question\":\"" + "가".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문은 500자 이하로 입력해주세요."));
        verifyNoInteractions(aiChatService);

        when(aiChatService.ask(eq(USER_ID), eq(POLICY_ID), any(), isNull()))
                .thenReturn(new AiChatResponse(CONVERSATION_ID, "a", 9, false));
        ask("{\"question\":\"" + "가".repeat(500) + "\"}").andExpect(status().isOk());
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(post("/api/policies/{policyId}/ai-chat", POLICY_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"q\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(aiChatService);
    }

    @Test
    void 정책이나_대화를_찾을_수_없으면_404() throws Exception {
        when(aiChatService.ask(any(), any(), any(), any())).thenThrow(AiChatNotFoundException.conversation());

        ask("{\"question\":\"q\",\"conversationId\":\"" + CONVERSATION_ID + "\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(AiChatNotFoundException.CONVERSATION_NOT_FOUND));
    }

    @Test
    void 턴_상한이면_409() throws Exception {
        when(aiChatService.ask(any(), any(), any(), any())).thenThrow(AiChatConflictException.turnLimitExceeded());

        ask("{\"question\":\"q\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(AiChatConflictException.TURN_LIMIT_EXCEEDED));
    }

    @Test
    void AI를_쓸_수_없으면_503_호출이_실패하면_502() throws Exception {
        when(aiChatService.ask(any(), any(), any(), any()))
                .thenThrow(new AiUnavailableException(AiClientException.Reason.DISABLED))
                .thenThrow(new AiCallFailedException(AiClientException.Reason.TIMEOUT));

        ask("{\"question\":\"q\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(AiUnavailableException.MESSAGE));
        ask("{\"question\":\"q\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(AiCallFailedException.MESSAGE));
    }

    @Test
    void 대화_종료() throws Exception {
        mockMvc.perform(delete("/api/ai-chat/{conversationId}", CONVERSATION_ID).header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(aiChatService).deleteConversation(USER_ID, CONVERSATION_ID);
    }

    @Test
    void 다른_사용자의_대화를_종료하면_404() throws Exception {
        doThrow(AiChatNotFoundException.conversation()).when(aiChatService).deleteConversation(USER_ID, CONVERSATION_ID);

        mockMvc.perform(delete("/api/ai-chat/{conversationId}", CONVERSATION_ID).header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound());
    }
}
