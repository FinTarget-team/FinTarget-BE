package kr.fintarget.api.domain.policy.controller;

import kr.fintarget.api.common.exception.GlobalExceptionHandler;
import kr.fintarget.api.domain.policy.chunk.PolicyChunkRefreshResult;
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
 * 청크 백필 관리자 API(#180)의 접근 제어와 응답을 실제 SecurityConfig 필터 체인으로 검증한다.
 */
@WebMvcTest(AdminPolicyChunkController.class)
@Import({SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "ADMIN_API_KEY=test-admin-key")
class AdminPolicyChunkControllerSecurityTest {

    private static final String URL = "/api/admin/policy/chunk/refresh";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicySyncOrchestrator policySyncOrchestrator;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void 관리자_키가_일치하면_변경된_정책의_청크를_갱신한다() throws Exception {
        when(policySyncOrchestrator.refreshChunksNow(false)).thenReturn(new PolicyChunkRefreshResult(10, 2, 8, 3, 0, 5L));

        mockMvc.perform(post(URL).header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regeneratedCount").value(2))
                .andExpect(jsonPath("$.data.chunkCount").value(3));
    }

    @Test
    void all이_true면_전체를_다시_만든다() throws Exception {
        when(policySyncOrchestrator.refreshChunksNow(true)).thenReturn(new PolicyChunkRefreshResult(10, 10, 0, 12, 0, 9L));

        mockMvc.perform(post(URL).param("all", "true").header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regeneratedCount").value(10));
    }

    @Test
    void 관리자_키가_다르면_403을_반환한다() throws Exception {
        mockMvc.perform(post(URL).header("X-Admin-Key", "wrong-key"))
                .andExpect(status().isForbidden());

        verify(policySyncOrchestrator, never()).refreshChunksNow(anyBoolean());
    }

    @Test
    void 일반_사용자_JWT로는_호출할_수_없다() throws Exception {
        when(jwtUtil.validateAccessToken("user-token")).thenReturn(true);
        when(jwtUtil.getUserId("user-token")).thenReturn("user-1");

        mockMvc.perform(post(URL).header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());

        verify(policySyncOrchestrator, never()).refreshChunksNow(anyBoolean());
    }

    @Test
    void 동기화가_실행_중이면_409를_반환한다() throws Exception {
        when(policySyncOrchestrator.refreshChunksNow(false)).thenThrow(new PolicySyncInProgressException());

        mockMvc.perform(post(URL).header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
