package kr.fintarget.api.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    // 토큰 용도. 리프레시 토큰으로 API를 호출하거나 액세스 토큰으로 재발급받지 못하게 구분한다 (#195)
    static final String TYPE_CLAIM = "type";
    static final String TYPE_ACCESS = "access";
    static final String TYPE_REFRESH = "refresh";
    static final String PROVIDER_CLAIM = "provider";

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration}")
    private long expiration;

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public long getExpirationMillis() {
        return expiration;
    }

    public String generateToken(String userId, String provider) {
        return Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .claim(TYPE_CLAIM, TYPE_ACCESS)
                .claim(PROVIDER_CLAIM, provider)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }

    public String generateRefreshToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .claim(TYPE_CLAIM, TYPE_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration * 7))
                .signWith(getKey())
                .compact();
    }

    public String getUserId(String token) {
        return getClaims(token).getSubject();
    }

    public String getJti(String token) {
        return getClaims(token).getId();
    }

    public LocalDateTime getExpirationTime(String token) {
        Date exp = getClaims(token).getExpiration();
        return exp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** API 인증용. 서명, 만료, 블랙리스트에 더해 액세스 토큰인지 확인한다 */
    public boolean validateAccessToken(String token) {
        return validate(token, TYPE_ACCESS);
    }

    /** 액세스 토큰 재발급용. 서명, 만료, 블랙리스트에 더해 리프레시 토큰인지 확인한다 */
    public boolean validateRefreshToken(String token) {
        return validate(token, TYPE_REFRESH);
    }

    private boolean validate(String token, String expectedType) {
        try {
            Claims claims = getClaims(token);
            String jti = claims.getId();
            if (jti != null && blacklistedTokenRepository.existsById(jti)) {
                return false;
            }
            // type이 없거나 다른 용도면 거부한다. #195 이전 형식(type 없음) 호환은 모든 이전 토큰이 만료돼 제거했다 (#197)
            return expectedType.equals(claims.get(TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}