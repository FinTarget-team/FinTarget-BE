package kr.fintarget.api.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 액세스 토큰과 리프레시 토큰을 용도에 맞게만 통과시키는지, #195 이전에 발급된 토큰도 용도대로 동작하는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    private static final String SECRET = "test-jwt-secret-for-ci-only-0123456789abcdef0123456789abcdef";

    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(blacklistedTokenRepository);
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3_600_000L);
        lenient().when(blacklistedTokenRepository.existsById(anyString())).thenReturn(false);
    }

    // #195 이전 형식: type 없음. 액세스 토큰에만 provider가 있다
    private static String legacyToken(boolean withProvider) {
        var builder = Jwts.builder()
                .subject("user-1")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000));
        if (withProvider) {
            builder.claim("provider", "KAKAO");
        }
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes())).compact();
    }

    @Test
    void 액세스_토큰은_인증에만_쓸_수_있다() {
        String access = jwtUtil.generateToken("user-1", "KAKAO");

        assertThat(jwtUtil.validateAccessToken(access)).isTrue();
        assertThat(jwtUtil.validateRefreshToken(access)).isFalse();
    }

    @Test
    void 리프레시_토큰은_재발급에만_쓸_수_있다() {
        String refresh = jwtUtil.generateRefreshToken("user-1");

        assertThat(jwtUtil.validateRefreshToken(refresh)).isTrue();
        assertThat(jwtUtil.validateAccessToken(refresh)).isFalse();
    }

    @Test
    void 재발급으로_받은_액세스_토큰도_다시_재발급에_쓸_수_없다() {
        String reissued = jwtUtil.generateToken("user-1", "REFRESH");

        assertThat(jwtUtil.validateAccessToken(reissued)).isTrue();
        assertThat(jwtUtil.validateRefreshToken(reissued)).isFalse();
    }

    @Test
    void 이전에_발급된_액세스_토큰은_provider로_구분해_인증에만_쓸_수_있다() {
        String legacyAccess = legacyToken(true);

        assertThat(jwtUtil.validateAccessToken(legacyAccess)).isTrue();
        assertThat(jwtUtil.validateRefreshToken(legacyAccess)).isFalse();
    }

    @Test
    void 이전에_발급된_리프레시_토큰은_재발급에만_쓸_수_있다() {
        String legacyRefresh = legacyToken(false);

        assertThat(jwtUtil.validateRefreshToken(legacyRefresh)).isTrue();
        assertThat(jwtUtil.validateAccessToken(legacyRefresh)).isFalse();
    }

    @Test
    void 블랙리스트에_있으면_용도와_상관없이_거부한다() {
        String access = jwtUtil.generateToken("user-1", "KAKAO");
        String refresh = jwtUtil.generateRefreshToken("user-1");
        when(blacklistedTokenRepository.existsById(jwtUtil.getJti(access))).thenReturn(true);
        when(blacklistedTokenRepository.existsById(jwtUtil.getJti(refresh))).thenReturn(true);

        assertThat(jwtUtil.validateAccessToken(access)).isFalse();
        assertThat(jwtUtil.validateRefreshToken(refresh)).isFalse();
    }

    @Test
    void 비어_있거나_형식이_틀린_토큰은_예외_없이_거부한다() {
        assertThat(jwtUtil.validateRefreshToken(null)).isFalse();
        assertThat(jwtUtil.validateRefreshToken("")).isFalse();
        assertThat(jwtUtil.validateAccessToken("not-a-jwt")).isFalse();
    }

    @Test
    void 다른_키로_서명한_토큰은_거부한다() {
        String forged = Jwts.builder()
                .subject("user-1")
                .claim("type", "access")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("another-secret-another-secret-another-secret-0123".getBytes()))
                .compact();

        assertThat(jwtUtil.validateAccessToken(forged)).isFalse();
    }
}
