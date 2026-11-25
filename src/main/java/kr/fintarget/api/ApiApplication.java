package kr.fintarget.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 기본 사용자 계정 자동 생성을 끈다 (#246). 인증은 JWT·API 키 필터로 하고, Swagger 계정은 SecurityConfig가 직접 만든다.
// 켜 두면 쓰이지 않는 계정의 비밀번호가 기동 로그에 출력된다
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class ApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}
