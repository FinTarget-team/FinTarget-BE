package kr.fintarget.api.domain.policy.controller;

import kr.fintarget.api.common.exception.GlobalExceptionHandler;
import kr.fintarget.api.domain.policy.service.PolicyService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내 정책 추가 요청의 입력 검증을 확인한다.
 * 인증은 검증 대상이 아니므로 시큐리티 필터를 끄고 인증 사용자를 직접 넣는다.
 */
@WebMvcTest(PolicyController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtFilter.class, CustomAuthenticationEntryPoint.class})
class PolicyControllerValidationTest {

    private static final String USER_ID = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyService policyService;

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

    private ResultActions addMyPolicy(String body) throws Exception {
        return mockMvc.perform(post("/api/policies/my").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 올바른_요청은_내_정책을_추가한다() throws Exception {
        addMyPolicy("{\"policyId\": \"" + UUID.randomUUID() + "\", \"status\": \"INTEREST\"}")
                .andExpect(status().isCreated());
        verify(policyService).createUserPolicy(any(), any());
    }

    @Test
    void 정책_ID가_없으면_400() throws Exception {
        addMyPolicy("{\"status\": \"INTEREST\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("정책을 선택해주세요."));
        verify(policyService, never()).createUserPolicy(any(), any());
    }

    @Test
    void 상태가_없으면_400() throws Exception {
        addMyPolicy("{\"policyId\": \"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("정책 상태를 입력해주세요."));
        verify(policyService, never()).createUserPolicy(any(), any());
    }
}
