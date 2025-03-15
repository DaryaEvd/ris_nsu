package ru.nsu.fit.evdokimova.worker.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(new PoolingHttpClientConnectionManager())
                .evictExpiredConnections()
                .evictIdleConnections(Timeout.ofSeconds(30))
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(factory);
    }
}
