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

class KakaoOAuthClientTest {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URI = "https://kapi.kakao.com/v2/user/me";

    private MockRestServiceServer mockServer;
    private KakaoOAuthClient kakaoOAuthClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        kakaoOAuthClient = new KakaoOAuthClient(builder);
    }

    @Test
    void 액세스_토큰으로_정상_응답이면_id를_반환한다() {
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("{\"id\":123456789}", MediaType.APPLICATION_JSON));

        String result = kakaoOAuthClient.getKakaoUserIdByAccessToken("fake-access-token");

        assertThat(result).isEqualTo("123456789");
    }

    @Test
    void 액세스_토큰_경로에서_id가_null이면_예외가_발생한다() {
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("{\"id\":null}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakaoOAuthClient.getKakaoUserIdByAccessToken("fake-access-token"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("카카오 사용자 정보 응답에 id가 없습니다.");
    }

    @Test
    void 액세스_토큰_경로에서_id가_빈_문자열이거나_공백이면_예외가_발생한다() {
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("{\"id\":\"\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakaoOAuthClient.getKakaoUserIdByAccessToken("fake-access-token"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("카카오 사용자 정보 응답에 id가 없습니다.");

        mockServer.reset();
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("{\"id\":\"   \"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakaoOAuthClient.getKakaoUserIdByAccessToken("fake-access-token"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("카카오 사용자 정보 응답에 id가 없습니다.");
    }

    @Test
    void 유효하지_않은_액세스_토큰으로_401_응답을_받으면_KakaoOAuthException이_발생한다() {
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> kakaoOAuthClient.getKakaoUserIdByAccessToken("fake-access-token"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("유효하지 않거나 만료된 카카오 인증 정보입니다.");
    }

    @Test
    void 카카오_서버가_5xx를_반환하면_KakaoOAuthException이_아닌_다른_예외가_발생한다() {
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> kakaoOAuthClient.getKakaoUserIdByAccessToken("fake-access-token"))
                .isNotInstanceOf(KakaoOAuthException.class);
    }

    @Test
    void 인가_코드_경로는_토큰_교환_후_사용자_정보_조회에서_id를_반환한다() {
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"exchanged-access-token\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("{\"id\":987654321}", MediaType.APPLICATION_JSON));

        String result = kakaoOAuthClient.getKakaoUserId("fake-authorization-code");

        assertThat(result).isEqualTo("987654321");
    }

    @Test
    void 인가_코드_경로에서_사용자_정보_조회가_401이면_KakaoOAuthException이_발생한다() {
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"exchanged-access-token\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> kakaoOAuthClient.getKakaoUserId("fake-authorization-code"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("유효하지 않거나 만료된 카카오 인증 정보입니다.");
    }

    @Test
    void 인가_코드_경로에서_id가_null이면_예외가_발생한다() {
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"exchanged-access-token\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("{\"id\":null}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakaoOAuthClient.getKakaoUserId("fake-authorization-code"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("카카오 사용자 정보 응답에 id가 없습니다.");
    }

    @Test
    void 인가_코드_교환_단계가_4xx면_KakaoOAuthException이_아닌_다른_예외가_발생한다() {
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> kakaoOAuthClient.getKakaoUserId("invalid-authorization-code"))
                .isNotInstanceOf(KakaoOAuthException.class);
    }
}
