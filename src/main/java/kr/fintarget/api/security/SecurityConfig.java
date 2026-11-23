package kr.fintarget.api.security;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    static final String[] SWAGGER_PATHS = {"/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html"};

    private final JwtFilter jwtFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;

    @Value("${ADMIN_API_KEY:}")
    private String adminApiKey;

    @Value("${BANK_WEBHOOK_KEY:}")
    private String bankWebhookKey;

    // /auth/** IP별 1분당 요청 수 (#218). 0 이하면 제한하지 않는다
    @Value("${auth.rate-limit.requests-per-minute:30}")
    private int authRequestsPerMinute;

    // Swagger 문서 Basic Auth 계정 (#218). 둘 중 하나라도 비어 있으면 이전처럼 공개한다
    @Value("${SWAGGER_USERNAME:}")
    private String swaggerUsername;

    @Value("${SWAGGER_PASSWORD:}")
    private String swaggerPassword;

    /**
     * Swagger 문서 전용 체인. 계정이 설정되어 있으면 Basic Auth로 보호한다.
     * 사용자 API 체인보다 먼저 검사해야 하므로 순서를 앞에 둔다.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain swaggerFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(SWAGGER_PATHS)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (swaggerUsername.isBlank() || swaggerPassword.isBlank()) {
            log.warn("SWAGGER_USERNAME/SWAGGER_PASSWORD가 설정되지 않아 Swagger 문서를 공개합니다. 운영에서는 설정하세요.");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        // 비밀번호는 기동할 때 해시해 두고 비교한다 (평문 비교 시간 차이로 추측하지 못하도록)
        InMemoryUserDetailsManager swaggerUsers = new InMemoryUserDetailsManager(User.withUsername(swaggerUsername.strip())
                .password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(swaggerPassword.strip()))
                .roles("SWAGGER")
                .build());
        http
                .userDetailsService(swaggerUsers)
                .httpBasic(basic -> basic.realmName("FinTarget API Docs"))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("SWAGGER"));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        // SSE 연결이 끝날 때의 비동기 재디스패치. 최초 요청에서 이미 인증했고, JwtFilter는 재디스패치에서 다시 돌지 않는다
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        // 배포 헬스 체크용 (#218). 상세 정보 없이 UP/DOWN만 응답한다
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/webhooks/**").hasRole(BankWebhookKeyFilter.ROLE)
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new AuthRateLimitFilter(authRequestsPerMinute), JwtFilter.class)
                .addFilterAfter(new AdminApiKeyFilter(adminApiKey), JwtFilter.class)
                .addFilterAfter(new BankWebhookKeyFilter(bankWebhookKey), JwtFilter.class);
        return http.build();
    }
}
