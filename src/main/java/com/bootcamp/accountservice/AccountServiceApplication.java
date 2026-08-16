package com.bootcamp.accountservice;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Punto de entrada de account-service: cuentas bancarias (ahorro, corriente, plazo fijo).
 * Contrato OpenAPI generado en /v3/api-docs, explorable en /swagger-ui.html. */
@OpenAPIDefinition(info = @Info(
        title = "account-service",
        version = "v1",
        description = "Cuentas bancarias: CRUD, depositos/retiros/transferencias idempotentes, "
                + "limites y comisiones por perfil (VIP/PYME/exceso de movimientos)."))
@SpringBootApplication
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
