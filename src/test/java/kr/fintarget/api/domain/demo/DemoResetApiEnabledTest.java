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
 * demo.reset.enabled=true일 때 초기화 API 3개의 권한과 응답을 실제 필터 체인으로 확인한다 (#239).
 */
@SpringBootTest(properties = {
        "demo.reset.enabled=true",
        "ADMIN_API_KEY=demo-admin-key"
})
@AutoConfigureMockMvc
class DemoResetApiEnabledTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserRepository userRepository;

    private String userId;
    private String otherUserId;

    @BeforeEach
    void setUp() {
        userId = createUser();
        otherUserId = createUser();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
        userRepository.deleteById(otherUserId);
    }

    private String createUser() {
        return userRepository.save(User.builder()
                .provider(SocialProvider.KAKAO)
                .providerId("kakao-" + UUID.randomUUID())
                .name("지원")
                .onboardingCompleted(true)
                .build()).getId();
    }

    private String bearer(String id) {
        return "Bearer " + jwtUtil.generateToken(id, "KAKAO");
    }

    @Test
    void 내_계정_초기화는_토큰의_본인만_초기화한다() throws Exception {
        mockMvc.perform(post("/api/users/me/demo-reset").header("Authorization", bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reset").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.deleted.onboardingAnswers").value(0));

        // 다른 사용자는 그대로다 (경로에 사용자 ID가 없어 남의 계정을 초기화할 방법이 없다)
        mockMvc.perform(get("/users/me").header("Authorization", bearer(otherUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));
    }

    @Test
    void 내_계정_초기화_직후_users_me는_미완료와_빈_프로필을_돌려준다() throws Exception {
        mockMvc.perform(post("/api/users/me/demo-reset").header("Authorization", bearer(userId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/me").header("Authorization", bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.name").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.region").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.housingStatus").value("NOT_SPECIFIED"));
    }

    @Test
    void 내_계정_초기화_후에도_같은_토큰으로_다시_호출할_수_있다() throws Exception {
        String token = bearer(userId);
        mockMvc.perform(post("/api/users/me/demo-reset").header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users/me/demo-reset").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reset").value(true));
    }

    @Test
    void 내_계정_초기화는_토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(post("/api/users/me/demo-reset"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자_초기화는_관리자_키가_있어야_한다() throws Exception {
        mockMvc.perform(post("/api/admin/demo/users/" + userId + "/reset"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("관리자 권한이 필요합니다."));

        mockMvc.perform(post("/api/admin/demo/users/" + userId + "/reset").header("X-Admin-Key", "demo-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId));
    }

    @Test
    void 관리자_초기화는_사용자_토큰으로는_호출할_수_없다() throws Exception {
        mockMvc.perform(post("/api/admin/demo/users/" + userId + "/reset").header("Authorization", bearer(userId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자_사용자_목록은_민감_정보를_내려주지_않는다() throws Exception {
        mockMvc.perform(get("/api/admin/demo/users").header("X-Admin-Key", "demo-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").exists())
                .andExpect(jsonPath("$.data[0].provider").exists())
                .andExpect(jsonPath("$.data[0].onboardingCompleted").exists())
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.data[0].providerId").doesNotExist());
    }

    @Test
    void 관리자_초기화는_없는_사용자면_400이다() throws Exception {
        mockMvc.perform(post("/api/admin/demo/users/" + UUID.randomUUID() + "/reset")
                        .header("X-Admin-Key", "demo-admin-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));
    }
}
