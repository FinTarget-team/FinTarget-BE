package kr.fintarget.api.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // 기본 한도(256KB)로는 온통청년 100건 페이지(2026-10 실측 최대 약 330KB)를 받지 못한다
    static final int MAX_IN_MEMORY_SIZE = 2 * 1024 * 1024;

    @Value("${youthcenter.api.base-url}")
    private String youthCenterBaseUrl;

    @Value("${kinfa.api.base-url}")
    private String kinfaBaseUrl;

    @Bean
    public WebClient youthCenterWebClient() {
        return WebClient.builder()
                .baseUrl(youthCenterBaseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
    }

    @Bean
    public WebClient kinfaWebClient() {
        return WebClient.builder()
                .baseUrl(kinfaBaseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
    }
}
