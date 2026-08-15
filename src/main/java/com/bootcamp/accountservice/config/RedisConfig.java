package com.bootcamp.accountservice.config;

import com.bootcamp.accountservice.dto.CustomerInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Cache de datos de cliente (D8, Fase III: "datos catalogados o maestros"). Un solo
 * {@link ReactiveRedisTemplate} tipado a {@link CustomerInfo}, con serializacion JSON (Jackson
 * soporta records nativamente) - lo usa {@code CustomerClient} para el cache-aside.
 */
@Configuration
public class RedisConfig {

    /** Template reactivo clave String / valor CustomerInfo (JSON), sobre la conexion de Redis
     * autoconfigurada por Boot a partir de spring.data.redis.*. */
    @Bean
    public ReactiveRedisTemplate<String, CustomerInfo> customerRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        Jackson2JsonRedisSerializer<CustomerInfo> valueSerializer =
                new Jackson2JsonRedisSerializer<>(CustomerInfo.class);
        RedisSerializationContext<String, CustomerInfo> context =
                RedisSerializationContext.<String, CustomerInfo>newSerializationContext(
                                new StringRedisSerializer())
                        .value(valueSerializer)
                        .build();
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
