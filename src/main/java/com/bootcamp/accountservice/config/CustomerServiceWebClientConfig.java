package com.bootcamp.accountservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Cliente HTTP hacia customer-service, para validar clientes al crear una cuenta (D5: en
 * Fase 1/2 las reglas cross-service se resuelven con REST, no con eventos).
 */
@Configuration
public class CustomerServiceWebClientConfig {

    @Bean
    public WebClient customerServiceWebClient(
            @Value("${customer-service.base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
