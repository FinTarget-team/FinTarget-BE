package kr.fintarget.api.security;

import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 필터와 컨트롤러를 거쳐 액세스 토큰은 API 호출에만, 리프레시 토큰은 재발급에만 쓰이는지 확인한다 (#195).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TokenTypeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserRepository userRepository;

    private String userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder()
                .provider(SocialProvider.KAKAO)
                .providerId("kakao-" + UUID.randomUUID())
                .build()).getId();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
    }

    private static String refreshBody(String token) {
        return "{\"refreshToken\": \"" + token + "\"}";
    }

    @Test
    void 액세스_토큰으로는_API를_호출할_수_있다() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + jwtUtil.generateToken(userId, "KAKAO")))
                .andExpect(status().isOk());
    }

    @Test
    void 리프레시_토큰으로는_API를_호출할_수_없다() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + jwtUtil.generateRefreshToken(userId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 리프레시_토큰으로_재발급받은_액세스_토큰으로_API를_호출할_수_있다() throws Exception {
        String body = mockMvc.perform(post("/auth/token/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(jwtUtil.generateRefreshToken(userId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newAccessToken = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    void 액세스_토큰으로는_재발급받을_수_없다() throws Exception {
        mockMvc.perform(post("/auth/token/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(jwtUtil.generateToken(userId, "KAKAO"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    void 리프레시_토큰_없이_재발급을_요청하면_401() throws Exception {
        mockMvc.perform(post("/auth/token/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
