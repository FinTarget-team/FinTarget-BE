package kr.fintarget.api.domain.policy.controller;

import kr.fintarget.api.security.SecurityConfig;
import kr.fintarget.api.security.CustomAuthenticationEntryPoint;
import kr.fintarget.api.common.exception.GlobalExceptionHandler;
import kr.fintarget.api.security.JwtFilter;
import kr.fintarget.api.security.JwtUtil;
import kr.fintarget.api.domain.policy.manual.ManualPolicySyncInProgressException;
import kr.fintarget.api.domain.policy.manual.ManualPolicySyncResult;
import kr.fintarget.api.domain.policy.manual.ManualPolicySyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 직접 등록 정책 동기화 관리자 API(#245)의 접근 제어와 응답을 실제 SecurityConfig 필터 체인으로 검증한다.
 */
@WebMvcTest(AdminPolicyManualController.class)
@Import({SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "ADMIN_API_KEY=test-admin-key")
class AdminPolicyManualControllerSecurityTest {

    private static final String URL = "/api/admin/policy/manual/sync";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManualPolicySyncService manualPolicySyncService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void 관리자_키가_일치하면_등록_결과를_돌려준다() throws Exception {
        ManualPolicySyncResult.Item item = new ManualPolicySyncResult.Item(UUID.randomUUID(), "MANUAL-HOPE-DOUBLE-2026",
                "2026 희망두배 청년통장", "CREATED", 3, 18, 34, List.of("SEOUL"), "INDIVIDUAL", 2_550_000L, false, null,
                "MONEY", "CASH", 150_000L, 36, null, true);
        when(manualPolicySyncService.sync()).thenReturn(new ManualPolicySyncResult(List.of(item), List.of(), List.of(), List.of(), Map.of("rerank", "requested"), 1200L));

        mockMvc.perform(post(URL).header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policies[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data.policies[0].chunkCount").value(3))
                .andExpect(jsonPath("$.data.policies[0].monthlyAmount").value(150000))
                .andExpect(jsonPath("$.data.steps.rerank").value("requested"));
    }

    @Test
    void 관리자_키가_없거나_다르면_403이고_등록하지_않는다() throws Exception {
        mockMvc.perform(post(URL)).andExpect(status().isForbidden());
        mockMvc.perform(post(URL).header("X-Admin-Key", "wrong-key")).andExpect(status().isForbidden());

        verify(manualPolicySyncService, never()).sync();
    }

    @Test
    void 일반_사용자_JWT로는_호출할_수_없다() throws Exception {
        when(jwtUtil.validateAccessToken("user-token")).thenReturn(true);
        when(jwtUtil.getUserId("user-token")).thenReturn("user-1");

        mockMvc.perform(post(URL).header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());

        verify(manualPolicySyncService, never()).sync();
    }

    @Test
    void 이미_실행_중이면_409를_반환한다() throws Exception {
        when(manualPolicySyncService.sync()).thenThrow(new ManualPolicySyncInProgressException());

        mockMvc.perform(post(URL).header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isConflict());
    }
}
