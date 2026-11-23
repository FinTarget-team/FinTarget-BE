package kr.fintarget.api.domain.auth;

import kr.fintarget.api.domain.auth.dto.SocialUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoOAuthClientTest {

    private static final String TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info";
    private static final String USER_URI = "https://kapi.kakao.com/v2/user/me";

    private MockRestServiceServer mockServer;
    private KakaoOAuthClient kakaoOAuthClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        kakaoOAuthClient = new KakaoOAuthClient(builder, "");
    }

    @Test
    void 액세스_토큰으로_정상_응답이면_id를_반환한다() {
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("{\"id\":123456789}", MediaType.APPLICATION_JSON));

        SocialUserInfo result = kakaoOAuthClient.getKakaoUserByAccessToken("fake-access-token");

        assertThat(result.providerId()).isEqualTo("123456789");
        assertThat(result.email()).isNull();
    }

    @Test
    void 액세스_토큰_경로에서_id가_null이면_예외가_발생한다() {
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("{\"id\":null}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakaoOAuthClient.getKakaoUserByAccessToken("fake-access-token"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("카카오 사용자 정보 응답에 id가 없습니다.");
    }

    @Test
    void 액세스_토큰_경로에서_id가_빈_문자열이거나_공백이면_예외가_발생한다() {
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("{\"id\":\"\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakaoOAuthClient.getKakaoUserByAccessToken("fake-access-token"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("카카오 사용자 정보 응답에 id가 없습니다.");

        mockServer.reset();
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("{\"id\":\"   \"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakaoOAuthClient.getKakaoUserByAccessToken("fake-access-token"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("카카오 사용자 정보 응답에 id가 없습니다.");
    }

    @Test
    void 유효하지_않은_액세스_토큰으로_401_응답을_받으면_KakaoOAuthException이_발생한다() {
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> kakaoOAuthClient.getKakaoUserByAccessToken("fake-access-token"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("유효하지 않거나 만료된 카카오 인증 정보입니다.");
    }

    @Test
    void 카카오_서버가_5xx를_반환하면_KakaoOAuthException이_아닌_다른_예외가_발생한다() {
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> kakaoOAuthClient.getKakaoUserByAccessToken("fake-access-token"))
                .isNotInstanceOf(KakaoOAuthException.class);
    }

    @Test
    void 이메일_제공에_동의했고_유효하고_인증된_이메일이면_이메일을_반환한다() {
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("""
                        {"id":1,"kakao_account":{"has_email":true,"is_email_valid":true,"is_email_verified":true,"email":"user@kakao.com"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(kakaoOAuthClient.getKakaoUserByAccessToken("fake-access-token").email()).isEqualTo("user@kakao.com");
    }

    @Test
    void 이메일_제공에_동의하지_않아_이메일이_없으면_null을_반환한다() {
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("""
                        {"id":1,"kakao_account":{"has_email":true,"email_needs_agreement":true}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(kakaoOAuthClient.getKakaoUserByAccessToken("fake-access-token").email()).isNull();
    }

    @Test
    void 인증되지_않았거나_유효하지_않은_이메일이면_null을_반환한다() {
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("""
                        {"id":1,"kakao_account":{"is_email_valid":true,"is_email_verified":false,"email":"user@kakao.com"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(kakaoOAuthClient.getKakaoUserByAccessToken("fake-access-token").email()).isNull();

        mockServer.reset();
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("""
                        {"id":1,"kakao_account":{"is_email_valid":false,"is_email_verified":true,"email":"user@kakao.com"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(kakaoOAuthClient.getKakaoUserByAccessToken("fake-access-token").email()).isNull();
    }

    // ===== 발급 앱 확인 (#218) =====
    private KakaoOAuthClient clientWithAppId(String appId) {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        return new KakaoOAuthClient(builder, appId);
    }

    @Test
    void 앱_ID가_설정되어_있고_토큰의_app_id가_같으면_사용자_정보를_조회한다() {
        KakaoOAuthClient client = clientWithAppId("1234567");
        mockServer.expect(requestTo(TOKEN_INFO_URI))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("{\"id\":42,\"expires_in\":7199,\"app_id\":1234567}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));

        assertThat(client.getKakaoUserByAccessToken("token").providerId()).isEqualTo("42");
        mockServer.verify();
    }

    @Test
    void 다른_앱에서_발급된_토큰이면_사용자_정보를_조회하지_않고_KakaoOAuthException이_발생한다() {
        KakaoOAuthClient client = clientWithAppId("1234567");
        mockServer.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess("{\"id\":42,\"expires_in\":7199,\"app_id\":9999999}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getKakaoUserByAccessToken("token"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("이 앱에서 발급되지 않은 카카오 인증 정보입니다.");
        mockServer.verify();
    }

    @Test
    void 토큰_정보_응답에_app_id가_없으면_KakaoOAuthException이_발생한다() {
        KakaoOAuthClient client = clientWithAppId("1234567");
        mockServer.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getKakaoUserByAccessToken("token"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("이 앱에서 발급되지 않은 카카오 인증 정보입니다.");
    }

    @Test
    void 토큰_정보_조회가_401이나_400이면_유효하지_않은_토큰으로_처리한다() {
        KakaoOAuthClient client = clientWithAppId("1234567");
        mockServer.expect(requestTo(TOKEN_INFO_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.getKakaoUserByAccessToken("token"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("유효하지 않거나 만료된 카카오 인증 정보입니다.");

        mockServer.reset();
        mockServer.expect(requestTo(TOKEN_INFO_URI)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> client.getKakaoUserByAccessToken("token"))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("유효하지 않거나 만료된 카카오 인증 정보입니다.");
    }

    @Test
    void 앱_ID가_설정되지_않으면_토큰_정보를_조회하지_않는다() {
        // setUp의 클라이언트는 앱 ID가 비어 있다. 사용자 정보 요청만 기대하므로 토큰 정보를 조회하면 실패한다
        mockServer.expect(requestTo(USER_URI))
                .andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));

        assertThat(kakaoOAuthClient.getKakaoUserByAccessToken("token").providerId()).isEqualTo("42");
        mockServer.verify();
    }
}
