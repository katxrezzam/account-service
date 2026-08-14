package com.bootcamp.accountservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Cliente HTTP hacia credit-service, para el bloqueo de alta de productos nuevos por deuda
 * vencida (D8, Fase III).
 */
@Configuration
public class CreditServiceWebClientConfig {

    @Bean
    public WebClient creditServiceWebClient(
            @Value("${credit-service.base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
