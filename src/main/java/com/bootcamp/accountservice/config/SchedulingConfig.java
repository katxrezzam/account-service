package com.bootcamp.accountservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} en una clase de config aparte, no en la clase principal - mismo
 * criterio que {@link MongoAuditingConfig} con {@code @EnableReactiveMongoAuditing}: evita que
 * los slice tests ({@code @WebFluxTest}) arrastren infraestructura de scheduling que no
 * necesitan (leccion de Fase 1, ver CONVENTIONS.md).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
