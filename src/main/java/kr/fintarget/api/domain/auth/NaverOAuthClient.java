package kr.fintarget.api.domain.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class NaverOAuthClient {

    private final RestClient restClient;

    public NaverOAuthClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public String getNaverUserId(String accessToken) {
        Map<String, Object> apiResponse = restClient.get()
                .uri("https://openapi.naver.com/v1/nid/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.value() == 401, (req, res) -> {
                    throw new NaverOAuthException("유효하지 않은 네이버 액세스 토큰입니다.");
                })
                .body(Map.class);

        if (!(apiResponse.get("response") instanceof Map<?, ?> profile)) {
            throw new NaverOAuthException("네이버 사용자 정보 응답 형식이 올바르지 않습니다.");
        }

        Object id = profile.get("id");
        if (id == null || String.valueOf(id).isBlank()) {
            throw new NaverOAuthException("네이버 사용자 정보 응답에 id가 없습니다.");
        }
        return String.valueOf(id);
    }
}
