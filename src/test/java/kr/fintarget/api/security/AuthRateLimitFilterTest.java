package kr.fintarget.api.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    /** 테스트에서 시간을 직접 옮길 수 있는 시계 */
    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    // 분 경계에서 10초 지난 시각
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-23T10:00:10Z"));

    private MockHttpServletResponse call(AuthRateLimitFilter filter, String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void 한도까지는_통과시키고_넘으면_429와_Retry_After를_응답한다() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(3, clock);

        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, "/auth/kakao/login/token", "1.1.1.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse limited = call(filter, "/auth/kakao/login/token", "1.1.1.1");

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("50");
        assertThat(limited.getContentAsString()).contains("요청이 너무 많습니다");
    }

    @Test
    void IP별로_따로_센다() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(1, clock);

        assertThat(call(filter, "/auth/token/refresh", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(call(filter, "/auth/token/refresh", "2.2.2.2").getStatus()).isEqualTo(200);
        assertThat(call(filter, "/auth/token/refresh", "1.1.1.1").getStatus()).isEqualTo(429);
    }

    @Test
    void 다음_1분_구간이_되면_다시_받는다() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(1, clock);
        call(filter, "/auth/logout", "1.1.1.1");
        assertThat(call(filter, "/auth/logout", "1.1.1.1").getStatus()).isEqualTo(429);

        clock.advanceSeconds(50);

        assertThat(call(filter, "/auth/logout", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void 인증_경로가_아니면_세지_않는다() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(1, clock);

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "/api/goals", "1.1.1.1").getStatus()).isEqualTo(200);
        }
        assertThat(filter.trackedIpCount()).isZero();
    }

    @Test
    void 한도가_0_이하면_제한하지_않는다() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(0, clock);

        for (int i = 0; i < 100; i++) {
            assertThat(call(filter, "/auth/token/refresh", "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void 기억하는_IP가_많아지면_지난_구간_기록을_정리한다() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(10, clock);
        for (int i = 0; i < AuthRateLimitFilter.CLEANUP_THRESHOLD; i++) {
            call(filter, "/auth/logout", "10.0." + (i / 256) + "." + (i % 256));
        }
        clock.advanceSeconds(60);

        call(filter, "/auth/logout", "1.1.1.1");
        call(filter, "/auth/logout", "1.1.1.2");

        assertThat(filter.trackedIpCount()).isEqualTo(2);
    }
}
