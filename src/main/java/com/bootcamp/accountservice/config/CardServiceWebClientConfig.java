package com.bootcamp.accountservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Cliente HTTP hacia card-service, para el requisito de tarjeta de credito de los perfiles
 * VIP/PYME al crear una cuenta (D5/D8).
 */
@Configuration
public class CardServiceWebClientConfig {

    @Bean
    public WebClient cardServiceWebClient(
            @Value("${card-service.base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
