package kr.fintarget.api.domain.demo;

import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기본값(demo.reset.enabled 없음)에서는 초기화 API 3개가 등록되지 않아 404인지 확인한다 (#239).
 * 관리자 경로는 관리자 키를 통과한 뒤에 404가 되어야 하므로 키를 함께 보낸다.
 */
@SpringBootTest(properties = "ADMIN_API_KEY=demo-admin-key")
@AutoConfigureMockMvc
class DemoResetApiDisabledTest {

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

    @Test
    void 내_계정_초기화는_404다() throws Exception {
        mockMvc.perform(post("/api/users/me/demo-reset")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(userId, "KAKAO")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("요청한 API를 찾을 수 없습니다."));
    }

    @Test
    void 관리자_초기화는_404다() throws Exception {
        mockMvc.perform(post("/api/admin/demo/users/" + userId + "/reset").header("X-Admin-Key", "demo-admin-key"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 관리자_사용자_목록은_404다() throws Exception {
        mockMvc.perform(get("/api/admin/demo/users").header("X-Admin-Key", "demo-admin-key"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 꺼져_있어도_사용자_데이터는_그대로다() throws Exception {
        mockMvc.perform(post("/api/users/me/demo-reset")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(userId, "KAKAO")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + jwtUtil.generateToken(userId, "KAKAO")))
                .andExpect(status().isOk());
    }
}
