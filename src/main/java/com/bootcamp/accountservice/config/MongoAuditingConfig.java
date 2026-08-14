package com.bootcamp.accountservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;

/** En clase aparte de la principal - ver leccion aprendida en CONVENTIONS.md (rompe
 * {@code @WebFluxTest} si no). */
@Configuration
@EnableReactiveMongoAuditing
public class MongoAuditingConfig {
}
