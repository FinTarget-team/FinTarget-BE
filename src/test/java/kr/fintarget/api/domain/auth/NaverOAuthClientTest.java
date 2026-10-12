package kr.fintarget.api.domain.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NaverOAuthClientTest {

    private static final String PROFILE_URI = "https://openapi.naver.com/v1/nid/me";

    private MockRestServiceServer mockServer;
    private NaverOAuthClient naverOAuthClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        naverOAuthClient = new NaverOAuthClient(builder);
    }

    @Test
    void 정상_응답이면_id를_반환한다() {
        mockServer.expect(requestTo(PROFILE_URI))
                .andRespond(withSuccess("{\"response\":{\"id\":\"abc123\"}}", MediaType.APPLICATION_JSON));

        String result = naverOAuthClient.getNaverUserId("fake-access-token");

        assertThat(result).isEqualTo("abc123");
    }

    @Test
    void id_필드가_누락되면_예외가_발생한다() {
        mockServer.expect(requestTo(PROFILE_URI))
                .andRespond(withSuccess("{\"response\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> naverOAuthClient.getNaverUserId("fake-access-token"))
                .isInstanceOf(NaverOAuthException.class)
                .hasMessage("네이버 사용자 정보 응답에 id가 없습니다.");
    }

    @Test
    void id가_null이면_예외가_발생한다() {
        mockServer.expect(requestTo(PROFILE_URI))
                .andRespond(withSuccess("{\"response\":{\"id\":null}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> naverOAuthClient.getNaverUserId("fake-access-token"))
                .isInstanceOf(NaverOAuthException.class)
                .hasMessage("네이버 사용자 정보 응답에 id가 없습니다.");
    }

    @Test
    void id가_빈_문자열이거나_공백이면_예외가_발생한다() {
        mockServer.expect(requestTo(PROFILE_URI))
                .andRespond(withSuccess("{\"response\":{\"id\":\"\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> naverOAuthClient.getNaverUserId("fake-access-token"))
                .isInstanceOf(NaverOAuthException.class)
                .hasMessage("네이버 사용자 정보 응답에 id가 없습니다.");

        mockServer.reset();
        mockServer.expect(requestTo(PROFILE_URI))
                .andRespond(withSuccess("{\"response\":{\"id\":\"   \"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> naverOAuthClient.getNaverUserId("fake-access-token"))
                .isInstanceOf(NaverOAuthException.class)
                .hasMessage("네이버 사용자 정보 응답에 id가 없습니다.");
    }

    @Test
    void response_필드_자체가_없으면_예외가_발생한다() {
        mockServer.expect(requestTo(PROFILE_URI))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> naverOAuthClient.getNaverUserId("fake-access-token"))
                .isInstanceOf(NaverOAuthException.class)
                .hasMessage("네이버 사용자 정보 응답 형식이 올바르지 않습니다.");
    }

    @Test
    void 유효하지_않은_토큰으로_401_응답을_받으면_NaverOAuthException이_발생한다() {
        mockServer.expect(requestTo(PROFILE_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> naverOAuthClient.getNaverUserId("fake-access-token"))
                .isInstanceOf(NaverOAuthException.class)
                .hasMessage("유효하지 않은 네이버 액세스 토큰입니다.");
    }

    @Test
    void 네이버_서버가_5xx를_반환하면_NaverOAuthException이_아닌_다른_예외가_발생한다() {
        mockServer.expect(requestTo(PROFILE_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> naverOAuthClient.getNaverUserId("fake-access-token"))
                .isNotInstanceOf(NaverOAuthException.class);
    }
}
