package kr.fintarget.api.domain.policy.search.eval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 평가기의 조건 재계산이 운영 필터와 같은 규칙인지 (#184).
 */
class EligibilityCheckTest {

    // 프로필: 나이 구간 24~25, 소득 250만, 서울
    private static EligibilityCheck.Result check(Integer minAge, Integer maxAge, Long incomeLimit, String region) {
        return EligibilityCheck.check(24, 25, 2_500_000L, "SEOUL", minAge, maxAge, incomeLimit, region);
    }

    @Test
    void 조건이_없는_전국_정책은_통과한다() {
        assertThat(check(null, null, null, null).eligible()).isTrue();
        assertThat(check(null, null, null, " ").eligible()).isTrue();
    }

    @Test
    void 나이_구간이_정책_나이와_겹치면_통과한다() {
        assertThat(check(25, 39, null, null).eligible()).isTrue();
        assertThat(check(19, 24, null, null).eligible()).isTrue();
        assertThat(check(26, 39, null, null).violations()).containsExactly("최소 나이 26 > 25");
        assertThat(check(19, 23, null, null).violations()).containsExactly("최대 나이 23 < 24");
    }

    @Test
    void 소득_한도는_프로필_소득_이상이어야_한다() {
        assertThat(check(null, null, 2_500_000L, null).eligible()).isTrue();
        assertThat(check(null, null, 2_499_999L, null).eligible()).isFalse();
    }

    @Test
    void 지역_목록에_프로필_지역이_있어야_하고_코드가_아닌_값이_섞이면_전국이다() {
        assertThat(check(null, null, null, "SEOUL").eligible()).isTrue();
        assertThat(check(null, null, null, "GYEONGNAM, SEOUL").eligible()).isTrue();
        assertThat(check(null, null, null, "BUSAN").violations()).containsExactly("지역 BUSAN에 SEOUL 없음");

        EligibilityCheck.Result fallback = check(null, null, null, "BUSAN,부산광역시");
        assertThat(fallback.eligible()).isTrue();
        assertThat(fallback.regionFallback()).isTrue();
    }

    @Test
    void 여러_조건을_어기면_모두_적는다() {
        assertThat(check(30, null, 1_000_000L, "JEJU").violations()).hasSize(3);
    }
}
