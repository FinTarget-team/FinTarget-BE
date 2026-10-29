package kr.fintarget.api.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfig {

    // RestClient.Builder는 spring-boot-starter-restclient 자동 설정 빈 (#105). 다른 클라이언트에 영향이 없도록 복제해 쓴다
    @Bean
    public RestClient openAiRestClient(RestClient.Builder restClientBuilder, OpenAiProperties properties) {
        return restClientBuilder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    static JdkClientHttpRequestFactory requestFactory(OpenAiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }
}
