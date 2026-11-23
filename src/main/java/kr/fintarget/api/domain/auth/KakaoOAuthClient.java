package kr.fintarget.api.domain.auth;

import kr.fintarget.api.domain.auth.dto.SocialUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 네이티브 앱(카카오 SDK)이 발급받은 카카오 액세스 토큰으로 사용자를 확인한다.
 * 인가 코드 로그인(/auth/kakao/login)은 앱이 쓰지 않아 제거했다 (#218).
 */
@Slf4j
@Component
public class KakaoOAuthClient {

    static final String TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info";
    static final String USER_URI = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final String appId;

    public KakaoOAuthClient(RestClient.Builder restClientBuilder, @Value("${kakao.app-id:${KAKAO_APP_ID:}}") String appId) {
        this.restClient = restClientBuilder.build();
        this.appId = appId == null ? "" : appId.strip();
        if (this.appId.isEmpty()) {
            log.warn("kakao.app-id(KAKAO_APP_ID)가 설정되지 않아 카카오 토큰의 발급 앱을 확인하지 않습니다. 운영에서는 반드시 설정하세요.");
        }
    }

    public SocialUserInfo getKakaoUserByAccessToken(String accessToken) {
        verifyIssuedForThisApp(accessToken);

        Map<String, Object> userResponse = restClient.get()
                .uri(USER_URI)
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
        return new SocialUserInfo(String.valueOf(id), verifiedEmail(userResponse.get("kakao_account")));
    }

    // 다른 앱에서 발급받은 카카오 토큰으로 로그인하지 못하도록, 토큰의 app_id가 우리 앱 ID와 같은지 확인한다 (#218)
    private void verifyIssuedForThisApp(String accessToken) {
        if (appId.isEmpty()) {
            return;
        }
        Map<String, Object> tokenInfo = restClient.get()
                .uri(TOKEN_INFO_URI)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.value() == 400 || status.value() == 401, (req, res) -> {
                    throw new KakaoOAuthException("유효하지 않거나 만료된 카카오 인증 정보입니다.");
                })
                .body(Map.class);

        Object tokenAppId = tokenInfo == null ? null : tokenInfo.get("app_id");
        if (tokenAppId == null || !appId.equals(String.valueOf(tokenAppId))) {
            throw new KakaoOAuthException("이 앱에서 발급되지 않은 카카오 인증 정보입니다.");
        }
    }

    // 이메일 제공에 동의했고, 카카오가 유효하고 인증된 이메일이라고 알려줄 때만 쓴다 (#173). 그 외에는 null
    private static String verifiedEmail(Object kakaoAccount) {
        if (!(kakaoAccount instanceof Map<?, ?> account)) {
            return null;
        }
        if (!Boolean.TRUE.equals(account.get("is_email_valid")) || !Boolean.TRUE.equals(account.get("is_email_verified"))) {
            return null;
        }
        return account.get("email") instanceof String email && !email.isBlank() ? email.strip() : null;
    }
}
