package kr.fintarget.api.domain.policy.controller;

import kr.fintarget.api.common.exception.GlobalExceptionHandler;
import kr.fintarget.api.domain.ai.client.AiCallFailedException;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.search.PolicySearchHit;
import kr.fintarget.api.domain.policy.search.PolicySearchService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정책 검색 API의 입력 검증과 응답 형식을 확인한다 (#182).
 * 인증은 검증 대상이 아니므로 시큐리티 필터를 끄고 인증 사용자를 직접 넣는다.
 */
@WebMvcTest(PolicySearchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class})
class PolicySearchControllerTest {

    private static final String USER_ID = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicySearchService policySearchService;

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

    private ResultActions search(String body) throws Exception {
        return mockMvc.perform(post("/api/policies/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private PolicySearchHit hit(String name, String sectionLabel, double distance) {
        Policy policy = Policy.create(name, "설명", null, null, null, null, PolicyType.HOUSING, null, null, null);
        ReflectionTestUtils.setField(policy, "policyId", UUID.randomUUID());
        return new PolicySearchHit(policy, UUID.randomUUID(), sectionLabel, "청크 본문", distance);
    }

    @Test
    void 검색_결과를_유사도_점수와_섹션_라벨과_함께_반환한다() throws Exception {
        when(policySearchService.search(eq(USER_ID), eq("월세 지원")))
                .thenReturn(List.of(hit("청년 월세 지원", "지원 내용", 0.1366)));

        search("""
                {"question": "월세 지원"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].policy.name").value("청년 월세 지원"))
                .andExpect(jsonPath("$.data.results[0].sectionLabel").value("지원 내용"))
                .andExpect(jsonPath("$.data.results[0].score").value(0.8634));
    }

    // 청크 본문은 외부 응답에 넣지 않는다 (#183만 서비스 계층에서 받아 쓴다)
    @Test
    void 응답에_청크_본문은_포함하지_않는다() throws Exception {
        when(policySearchService.search(anyString(), anyString()))
                .thenReturn(List.of(hit("정책", "전체", 0.2)));

        search("""
                {"question": "질문"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].content").doesNotExist());
    }

    @Test
    void 맞는_정책이_없으면_빈_배열과_200을_반환한다() throws Exception {
        when(policySearchService.search(anyString(), anyString())).thenReturn(List.of());

        search("""
                {"question": "해당 없는 질문"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results").isEmpty());
    }

    @Test
    void 질문이_없거나_공백이면_400이고_검색을_호출하지_않는다() throws Exception {
        search("""
                {"question": null}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문을 입력해주세요."));

        search("""
                {"question": "   "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문을 입력해주세요."));

        verify(policySearchService, never()).search(anyString(), anyString());
    }

    @Test
    void 질문이_상한을_넘으면_400이다() throws Exception {
        String tooLong = "가".repeat(501);

        search("{\"question\": \"" + tooLong + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문은 500자 이하로 입력해주세요."));

        verify(policySearchService, never()).search(anyString(), anyString());
    }

    @Test
    void 임베딩을_쓸_수_없으면_503이다() throws Exception {
        when(policySearchService.search(anyString(), anyString()))
                .thenThrow(new AiUnavailableException(AiClientException.Reason.DISABLED));

        search("""
                {"question": "질문"}
                """)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void 임베딩_호출이_실패하면_502이다() throws Exception {
        when(policySearchService.search(anyString(), anyString()))
                .thenThrow(new AiCallFailedException(AiClientException.Reason.SERVER_ERROR));

        search("""
                {"question": "질문"}
                """)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }
}
