package kr.fintarget.api.domain.user.event;

/**
 * 맞춤 추천에 쓰는 사용자 정보가 바뀜 (온보딩 완료, 프로필 수정). 추천 리랭킹 미리 계산(#229)이 커밋 후에 받는다.
 */
public record UserProfileChangedEvent(String userId) {
}
