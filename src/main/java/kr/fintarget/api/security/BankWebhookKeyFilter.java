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
 * /api/webhooks/** 요청은 X-Bank-Webhook-Key 헤더가 BANK_WEBHOOK_KEY 환경변수와 일치할 때만 통과시킨다.
 * 가상 금융 서버(mock-bank)가 사용자 JWT 없이 거래를 보내는 서버 간 호출용이며, 키가 설정되지 않은 서버에서는 모두 거부한다.
 * AdminApiKeyFilter와 같은 방식으로, 서블릿 필터로 자동 등록되지 않도록 빈이 아닌 SecurityConfig에서 직접 생성한다.
 */
public class BankWebhookKeyFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Bank-Webhook-Key";
    public static final String WEBHOOK_PATH_PREFIX = "/api/webhooks/";
    public static final String ROLE = "BANK_WEBHOOK";

    private final byte[] webhookKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BankWebhookKeyFilter(String webhookKey) {
        this.webhookKey = webhookKey == null ? new byte[0] : webhookKey.trim().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(WEBHOOK_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (webhookKey.length == 0) {
            writeForbidden(response, "웹훅 키가 서버에 설정되어 있지 않습니다.");
            return;
        }

        String requestKey = request.getHeader(HEADER_NAME);
        // 응답 시간 차이로 키를 추측하지 못하도록 고정 시간 비교를 사용한다
        if (requestKey == null
                || !MessageDigest.isEqual(webhookKey, requestKey.trim().getBytes(StandardCharsets.UTF_8))) {
            writeForbidden(response, "웹훅 키가 올바르지 않습니다.");
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "bank-webhook", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
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
