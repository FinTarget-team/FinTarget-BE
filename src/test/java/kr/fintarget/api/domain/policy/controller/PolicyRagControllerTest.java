package kr.fintarget.api.domain.policy.controller;

import kr.fintarget.api.common.exception.GlobalExceptionHandler;
import kr.fintarget.api.domain.ai.client.AiCallFailedException;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
import kr.fintarget.api.domain.ai.usage.AiUsageFeature;
import kr.fintarget.api.domain.ai.usage.AiUsageLimitExceededException;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.rag.PolicyRagResult;
import kr.fintarget.api.domain.policy.rag.PolicyRagService;
import kr.fintarget.api.domain.policy.rag.PolicyRagStatus;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 정책 AI 답변 API의 입력 검증, 응답 형식, 예외 응답 코드 (#183).
 * 인증은 검증 대상이 아니므로 시큐리티 필터를 끄고 인증 사용자를 직접 넣는다.
 */
@WebMvcTest(PolicyRagController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class})
class PolicyRagControllerTest {

    private static final String USER_ID = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyRagService policyRagService;

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

    private ResultActions answer(String body) throws Exception {
        return mockMvc.perform(post("/api/policies/ai-answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static Policy policy(String name, String region) {
        Policy policy = Policy.create(name, "설명", null, null, null, null, PolicyType.HOUSING, region, null, null);
        ReflectionTestUtils.setField(policy, "policyId", UUID.randomUUID());
        return policy;
    }

    @Test
    void 답변과_근거_정책의_ID_이름_지역코드를_반환한다() throws Exception {
        Policy ulsan = policy("전세보증금반환보증 보증료 지원", "GYEONGNAM,ULSAN");
        Policy national = policy("청년 월세 지원", null);
        when(policyRagService.answer(eq(USER_ID), eq("월세 지원")))
                .thenReturn(new PolicyRagResult(PolicyRagStatus.ANSWERED, "답변", List.of(ulsan, national)));

        answer("""
                {"question": "  월세 지원  "}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.answer").value("답변"))
                .andExpect(jsonPath("$.data.sources.length()").value(2))
                .andExpect(jsonPath("$.data.sources[0].policyId").value(ulsan.getPolicyId().toString()))
                .andExpect(jsonPath("$.data.sources[0].name").value("전세보증금반환보증 보증료 지원"))
                .andExpect(jsonPath("$.data.sources[0].regions[0]").value("GYEONGNAM"))
                .andExpect(jsonPath("$.data.sources[0].regions[1]").value("ULSAN"))
                .andExpect(jsonPath("$.data.sources[1].regions").isEmpty())
                // 청크 본문·거리는 내보내지 않는다
                .andExpect(jsonPath("$.data.sources[0].content").doesNotExist());
    }

    @Test
    void 근거_없음과_조건_정보_부족은_200과_빈_근거로_반환한다() throws Exception {
        when(policyRagService.answer(anyString(), eq("근거 없음")))
                .thenReturn(new PolicyRagResult(PolicyRagStatus.NO_EVIDENCE, "안내", List.of()));
        when(policyRagService.answer(anyString(), eq("정보 없음")))
                .thenReturn(new PolicyRagResult(PolicyRagStatus.PROFILE_INCOMPLETE, "안내", List.of()));

        answer("""
                {"question": "근거 없음"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NO_EVIDENCE"))
                .andExpect(jsonPath("$.data.sources").isEmpty());
        answer("""
                {"question": "정보 없음"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROFILE_INCOMPLETE"))
                .andExpect(jsonPath("$.data.sources").isEmpty());
    }

    @Test
    void 질문이_없거나_공백이거나_500자를_넘으면_400이고_서비스를_호출하지_않는다() throws Exception {
        answer("""
                {"question": null}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문을 입력해주세요."));
        answer("""
                {"question": "   "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문을 입력해주세요."));
        answer("{\"question\": \"" + "가".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문은 500자 이하로 입력해주세요."));

        verify(policyRagService, never()).answer(anyString(), anyString());
    }

    @Test
    void 일일_한도를_넘으면_429() throws Exception {
        when(policyRagService.answer(anyString(), anyString()))
                .thenThrow(new AiUsageLimitExceededException(AiUsageFeature.POLICY_RAG));

        answer("""
                {"question": "질문"}
                """)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("오늘 질문할 수 있는 횟수를 모두 사용했습니다. 내일 다시 시도해주세요."));
    }

    @Test
    void AI_호출_실패는_502_AI_비활성과_OpenAI_한도는_503() throws Exception {
        when(policyRagService.answer(anyString(), eq("실패")))
                .thenThrow(new AiCallFailedException(AiClientException.Reason.TIMEOUT));
        when(policyRagService.answer(anyString(), eq("비활성")))
                .thenThrow(new AiUnavailableException(AiClientException.Reason.DISABLED));
        when(policyRagService.answer(anyString(), eq("한도")))
                .thenThrow(new AiUnavailableException(AiClientException.Reason.QUOTA_EXCEEDED));

        answer("""
                {"question": "실패"}
                """).andExpect(status().isBadGateway());
        answer("""
                {"question": "비활성"}
                """).andExpect(status().isServiceUnavailable());
        answer("""
                {"question": "한도"}
                """).andExpect(status().isServiceUnavailable());
    }
}
