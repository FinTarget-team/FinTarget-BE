package kr.fintarget.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Swagger 계정을 설정하지 않은 서버는 이전처럼 문서를 공개한다 (#218).
 * 배포 직후 서버의 .env에 계정을 넣기 전까지 기존 헬스 체크(/v3/api-docs)와 팀원 문서 열람이 끊기지 않게 하기 위함이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerPublicWhenUnconfiguredTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 계정이_없으면_Swagger_문서는_공개된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
