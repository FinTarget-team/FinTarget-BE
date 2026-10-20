package kr.fintarget.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.fintarget.api.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * /api/admin/** 요청은 X-Admin-Key 헤더가 ADMIN_API_KEY 환경변수와 일치할 때만 통과시킨다.
 * 사용자 JWT와 무관하게 키만으로 판단하며, 키가 설정되지 않은 서버에서는 모든 관리자 요청을 거부한다.
 * 서블릿 필터로 자동 등록되지 않도록 빈이 아닌 SecurityConfig에서 직접 생성한다.
 */
public class AdminApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Admin-Key";
    public static final String ADMIN_PATH_PREFIX = "/api/admin/";

    private final byte[] adminApiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminApiKeyFilter(String adminApiKey) {
        this.adminApiKey = adminApiKey == null ? new byte[0] : adminApiKey.trim().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (adminApiKey.length == 0) {
            writeForbidden(response, "관리자 API 키가 서버에 설정되어 있지 않습니다.");
            return;
        }

        String requestKey = request.getHeader(HEADER_NAME);
        // 응답 시간 차이로 키를 추측하지 못하도록 고정 시간 비교를 사용한다
        if (requestKey == null
                || !MessageDigest.isEqual(adminApiKey, requestKey.trim().getBytes(StandardCharsets.UTF_8))) {
            writeForbidden(response, "관리자 권한이 필요합니다.");
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(403, message)));
    }
}
