package kr.fintarget.api.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${youthcenter.api.base-url}")
    private String youthCenterBaseUrl;

    @Value("${kinfa.api.base-url}")
    private String kinfaBaseUrl;

    @Bean
    public WebClient youthCenterWebClient() {
        return WebClient.builder()
                .baseUrl(youthCenterBaseUrl)
                .build();
    }

    @Bean
    public WebClient kinfaWebClient() {
        return WebClient.builder()
                .baseUrl(kinfaBaseUrl)
                .build();
    }
}
