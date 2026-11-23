package kr.fintarget.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.fintarget.api.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 인증 없이 호출할 수 있는 /auth/** 요청을 IP별로 1분에 정해진 횟수까지만 받는다 (#218).
 * 로그인 요청마다 카카오·네이버 API를 호출하므로, 반복 호출로 외부 API 한도를 소진하거나 서버에 부하를 주지 못하게 한다.
 * 서버가 한 대라 메모리로 센다. 프록시(Caddy) 뒤에서는 server.forward-headers-strategy 설정으로 실제 클라이언트 IP가 remoteAddr에 들어온다.
 * AdminApiKeyFilter와 같이 서블릿 필터로 자동 등록되지 않도록 빈이 아닌 SecurityConfig에서 직접 생성한다.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    public static final String AUTH_PATH_PREFIX = "/auth/";
    static final String LIMIT_EXCEEDED_MESSAGE = "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
    // 이보다 많은 IP를 기억하고 있으면 지난 구간 기록을 정리한다
    static final int CLEANUP_THRESHOLD = 10_000;
    private static final long WINDOW_MILLIS = 60_000;

    private final int requestsPerMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Window(long index, AtomicInteger count) {
    }

    public AuthRateLimitFilter(int requestsPerMinute) {
        this(requestsPerMinute, Clock.systemUTC());
    }

    AuthRateLimitFilter(int requestsPerMinute, Clock clock) {
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 0 이하면 제한하지 않는다
        return requestsPerMinute <= 0 || !request.getRequestURI().startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long now = clock.millis();
        long index = now / WINDOW_MILLIS;
        Window window = windows.compute(request.getRemoteAddr(),
                (ip, current) -> current == null || current.index() != index ? new Window(index, new AtomicInteger()) : current);

        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.index() < index);
        }

        if (window.count().incrementAndGet() > requestsPerMinute) {
            long retryAfterSeconds = Math.max(1, ((index + 1) * WINDOW_MILLIS - now + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(429, LIMIT_EXCEEDED_MESSAGE)));
            return;
        }
        filterChain.doFilter(request, response);
    }

    int trackedIpCount() {
        return windows.size();
    }
}
