package kr.fintarget.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 필터 체인으로 #218의 접근 제어를 확인한다: Swagger Basic Auth, 헬스 체크 공개, 인증 API 요청 횟수 제한.
 */
@SpringBootTest(properties = {
        "SWAGGER_USERNAME=docs",
        "SWAGGER_PASSWORD=docs-password",
        "auth.rate-limit.requests-per-minute=3"
})
@AutoConfigureMockMvc
class SecurityEndpointsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void 기본_사용자_계정을_자동으로_만들지_않는다() {
        // 만들어지면 기동 로그에 생성된 비밀번호가 출력된다 (#246)
        assertThat(applicationContext.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void Swagger_문서는_계정_없이_401이다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void Swagger_문서는_잘못된_비밀번호면_401이다() throws Exception {
        mockMvc.perform(get("/v3/api-docs").header("Authorization", basic("docs", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void Swagger_문서는_올바른_계정이면_200이다() throws Exception {
        mockMvc.perform(get("/v3/api-docs").header("Authorization", basic("docs", "docs-password")))
                .andExpect(status().isOk());
    }

    @Test
    void Swagger_계정으로_사용자_API를_호출할_수_없다() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", basic("docs", "docs-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 헬스_체크는_인증_없이_UP을_응답한다() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void 헬스_체크_외의_actuator_엔드포인트는_열려_있지_않다() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_API는_IP별로_1분에_정한_횟수를_넘으면_429다() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/logout").with(request -> {
                        request.setRemoteAddr("203.0.113.7");
                        return request;
                    }))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).with(request -> {
                    request.setRemoteAddr("203.0.113.7");
                    return request;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        // 다른 IP는 영향을 받지 않는다
        mockMvc.perform(post("/auth/logout").with(request -> {
                    request.setRemoteAddr("203.0.113.8");
                    return request;
                }))
                .andExpect(status().isOk());
    }
}
