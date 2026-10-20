package kr.fintarget.api.domain.policy.controller;

import kr.fintarget.api.domain.policy.service.PolicySyncInProgressException;
import kr.fintarget.api.domain.policy.service.PolicySyncOrchestrator;
import kr.fintarget.api.domain.policy.service.PolicySyncResult;
import kr.fintarget.api.common.exception.GlobalExceptionHandler;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 SecurityConfig 필터 체인을 거쳐 관리자 API 접근 제어가 동작하는지 검증한다.
 */
@WebMvcTest(AdminPolicyController.class)
@Import({SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "ADMIN_API_KEY=test-admin-key")
class AdminPolicyControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicySyncOrchestrator policySyncOrchestrator;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void 관리자_키가_일치하면_동기화를_실행한다() throws Exception {
        when(policySyncOrchestrator.syncAll()).thenReturn(new PolicySyncResult(1, false, 2, false, 10L));

        mockMvc.perform(post("/api/admin/policy/sync").header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.youthCenterSavedCount").value(1));
    }

    @Test
    void 일반_사용자_JWT로는_호출할_수_없다() throws Exception {
        when(jwtUtil.validateToken("user-token")).thenReturn(true);
        when(jwtUtil.getUserId("user-token")).thenReturn("user-1");

        mockMvc.perform(post("/api/admin/policy/sync").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());

        verify(policySyncOrchestrator, never()).syncAll();
    }

    @Test
    void 관리자_키가_다르면_403을_반환한다() throws Exception {
        mockMvc.perform(post("/api/admin/policy/sync").header("X-Admin-Key", "wrong-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verify(policySyncOrchestrator, never()).syncAll();
    }

    @Test
    void 동기화가_이미_실행_중이면_409를_반환한다() throws Exception {
        when(policySyncOrchestrator.syncAll()).thenThrow(new PolicySyncInProgressException());

        mockMvc.perform(post("/api/admin/policy/sync").header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
