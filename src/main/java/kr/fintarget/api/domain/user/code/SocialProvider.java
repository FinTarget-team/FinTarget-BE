package kr.fintarget.api.domain.user.code;

/**
 * 가입에 사용한 소셜 로그인 제공자 (#173). DB(users.provider)에는 이름 그대로(KAKAO, NAVER) 문자열로 저장한다
 */
public enum SocialProvider {
    KAKAO,
    NAVER
}
