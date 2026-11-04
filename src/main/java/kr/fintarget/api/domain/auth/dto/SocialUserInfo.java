package kr.fintarget.api.domain.auth.dto;

/**
 * 소셜 제공자에게서 받은 사용자 정보 (#173).
 * email은 사용자가 이메일 제공에 동의하지 않았거나 제공자가 확인되지 않은 이메일이라고 알려주면 null이다
 */
public record SocialUserInfo(String providerId, String email) {
}
