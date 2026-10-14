package kr.fintarget.api.domain.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class KakaoOAuthClient {

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.client-secret}")
    private String clientSecret;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    private final RestClient restClient;

    public KakaoOAuthClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public String getKakaoUserId(String authorizationCode) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", authorizationCode);

        Map<String, Object> tokenResponse = restClient.post()
                .uri("https://kauth.kakao.com/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(params)
                .retrieve()
                .body(Map.class);

        String kakaoAccessToken = (String) tokenResponse.get("access_token");

        return getKakaoUserIdByAccessToken(kakaoAccessToken);
    }

    public String getKakaoUserIdByAccessToken(String accessToken) {
        Map<String, Object> userResponse = restClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.value() == 401, (req, res) -> {
                    throw new KakaoOAuthException("유효하지 않거나 만료된 카카오 인증 정보입니다.");
                })
                .body(Map.class);

        Object id = userResponse.get("id");
        if (id == null || String.valueOf(id).isBlank()) {
            throw new KakaoOAuthException("카카오 사용자 정보 응답에 id가 없습니다.");
        }
        return String.valueOf(id);
    }
}