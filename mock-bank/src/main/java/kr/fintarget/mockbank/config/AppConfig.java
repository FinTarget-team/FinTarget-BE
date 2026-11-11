package kr.fintarget.mockbank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Random;
import java.util.random.RandomGenerator;

@Configuration
public class AppConfig {

    // "오늘"과 "이번 달"은 서버 시간대와 상관없이 한국 시간 기준으로 판단한다
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    @Bean
    public RandomGenerator randomGenerator() {
        return new Random();
    }
}
