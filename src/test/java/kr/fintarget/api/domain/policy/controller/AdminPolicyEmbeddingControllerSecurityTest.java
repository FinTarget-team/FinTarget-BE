package kr.fintarget.api.domain.policy.controller;

import kr.fintarget.api.common.exception.GlobalExceptionHandler;
import kr.fintarget.api.domain.policy.embedding.PolicyEmbeddingRefreshResult;
import kr.fintarget.api.domain.policy.service.PolicySyncInProgressException;
import kr.fintarget.api.domain.policy.service.PolicySyncOrchestrator;
import kr.fintarget.api.security.CustomAuthenticationEntryPoint;
import kr.fintarget.api.security.JwtFilter;
import kr.fintarget.api.security.JwtUtil;
import kr.fintarget.api.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 청크 임베딩 백필 관리자 API(#181)의 접근 제어와 응답을 실제 SecurityConfig 필터 체인으로 검증한다.
 */
@WebMvcTest(AdminPolicyEmbeddingController.class)
@Import({SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "ADMIN_API_KEY=test-admin-key")
class AdminPolicyEmbeddingControllerSecurityTest {

    private static final String URL = "/api/admin/policy/embedding/refresh";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicySyncOrchestrator policySyncOrchestrator;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void 관리자_키가_일치하면_임베딩이_필요한_청크를_임베딩한다() throws Exception {
        when(policySyncOrchestrator.refreshEmbeddingsNow(false)).thenReturn(new PolicyEmbeddingRefreshResult("text-embedding-3-small", 3, 2, 1, 0, 5L));

        mockMvc.perform(post(URL).header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.embeddedCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(1));
    }

    @Test
    void all이_true면_전체를_다시_임베딩한다() throws Exception {
        when(policySyncOrchestrator.refreshEmbeddingsNow(true)).thenReturn(new PolicyEmbeddingRefreshResult("text-embedding-3-small", 12, 12, 0, 0, 9L));

        mockMvc.perform(post(URL).param("all", "true").header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetCount").value(12));
    }

    @Test
    void 관리자_키가_다르면_403을_반환한다() throws Exception {
        mockMvc.perform(post(URL).header("X-Admin-Key", "wrong-key"))
                .andExpect(status().isForbidden());

        verify(policySyncOrchestrator, never()).refreshEmbeddingsNow(anyBoolean());
    }

    @Test
    void 일반_사용자_JWT로는_호출할_수_없다() throws Exception {
        when(jwtUtil.validateToken("user-token")).thenReturn(true);
        when(jwtUtil.getUserId("user-token")).thenReturn("user-1");

        mockMvc.perform(post(URL).header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());

        verify(policySyncOrchestrator, never()).refreshEmbeddingsNow(anyBoolean());
    }

    @Test
    void 동기화가_실행_중이면_409를_반환한다() throws Exception {
        when(policySyncOrchestrator.refreshEmbeddingsNow(false)).thenThrow(new PolicySyncInProgressException());

        mockMvc.perform(post(URL).header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
