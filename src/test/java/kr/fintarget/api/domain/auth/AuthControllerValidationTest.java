package kr.fintarget.api.domain.auth;

import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.security.BlacklistedTokenRepository;
import kr.fintarget.api.security.JwtUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    @MockitoBean
    private NaverOAuthClient naverOAuthClient;

    @MockitoBean
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 카카오_액세스_토큰이_비어있으면_400이고_카카오_클라이언트는_호출되지_않는다(String blankToken) throws Exception {
        String body = blankToken == null ? "{}" : "{\"accessToken\":\"" + blankToken + "\"}";

        mockMvc.perform(post("/auth/kakao/login/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(kakaoOAuthClient, never()).getKakaoUserIdByAccessToken(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 네이버_액세스_토큰이_비어있으면_400이고_네이버_클라이언트는_호출되지_않는다(String blankToken) throws Exception {
        String body = blankToken == null ? "{}" : "{\"accessToken\":\"" + blankToken + "\"}";

        mockMvc.perform(post("/auth/naver/login/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(naverOAuthClient, never()).getNaverUserId(any());
    }
}
