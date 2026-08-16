# Diagramas de secuencia — account-service

Requerimiento no funcional (Parte I): *"Elaborar diagramas de secuencia de cada microservicio."*

## Depósito con Idempotency-Key (incluida la comisión por exceso de movimientos)

Todo movimiento de dinero pasa por la misma pata (`executeLeg`): valida el límite mensual
gratuito, aplica el update atómico de saldo, registra el movimiento y cobra la comisión si
corresponde — nunca lectura+escritura separada sobre el saldo.

```mermaid
sequenceDiagram
    actor Cliente
    participant GW as api-gateway
    participant AS as account-service
    participant Mongo as accountdb

    Cliente->>GW: POST /accounts/{id}/deposits (Idempotency-Key, JWT)
    GW->>AS: forward (JWT validado)
    AS->>Mongo: findByIdempotencyKey(key)
    alt clave ya usada
        Mongo-->>AS: Movement existente
        AS-->>GW: 201 (mismo resultado, no reaplica)
    else clave nueva
        Mongo-->>AS: vacío
        AS->>Mongo: countByAccountIdAndTimestampBetween(mes actual)
        Mongo-->>AS: cantidad de movimientos del mes
        AS->>Mongo: findAndModify (balance += amount, atómico)
        Mongo-->>AS: cuenta actualizada
        AS->>Mongo: save(Movement DEPOSIT)
        opt cantidad >= límite gratuito mensual
            AS->>Mongo: chargeFee (Movement FEE, idempotencyKey propia)
        end
        AS-->>GW: 201 Created
    end
    GW-->>Cliente: respuesta
```

## Alta de cuenta VIP (validación cross-service síncrona)

Crear una cuenta VIP exige que el cliente ya tenga tarjeta de crédito (D5) — resuelto con
llamadas REST protegidas por circuit breaker + timeout de 2s, porque `account-service` no es un
microservicio nuevo (la restricción "sin REST" de Fase III no le aplica).

```mermaid
sequenceDiagram
    actor Cliente
    participant GW as api-gateway
    participant AS as account-service
    participant CustS as customer-service
    participant CardS as card-service
    participant Mongo as accountdb

    Cliente->>GW: POST /accounts {profile: VIP}
    GW->>AS: forward
    AS->>CustS: GET /customers/{holderId} (CustomerClient, circuit breaker)
    CustS-->>AS: CustomerInfo (PERSONAL)
    AS->>CardS: GET /cards/exists?customerId= (CardClient, circuit breaker)
    alt sin tarjeta de crédito
        CardS-->>AS: false
        AS-->>GW: 400 "requiere tarjeta de crédito"
    else con tarjeta de crédito
        CardS-->>AS: true
        AS->>Mongo: existsByHoldersAndAccountType (D8: 1 SAVINGS por personal)
        Mongo-->>AS: false
        AS->>Mongo: save(Account profile=VIP)
        Mongo-->>AS: cuenta creada
        AS-->>GW: 201 Created
    end
    GW-->>Cliente: respuesta
```

## Transferencia con compensación (Saga local, D6/D7)

Si el depósito en destino falla después del retiro en origen, se revierte con un depósito
compensatorio — incluida la devolución de cualquier comisión que el retiro original haya
disparado (hallazgo 8.7, `reverseWithdrawal`: la reversión no cuenta para el límite/comisión ni
para el bloqueo de día de una cuenta a plazo fijo).

```mermaid
sequenceDiagram
    actor Cliente
    participant GW as api-gateway
    participant AS as account-service
    participant Mongo as accountdb

    Cliente->>GW: POST /accounts/{origen}/transfers
    GW->>AS: forward
    AS->>Mongo: findAndModify (retiro atómico en origen)
    Mongo-->>AS: cuenta origen actualizada
    AS->>Mongo: save(Movement WITHDRAWAL, origen)
    AS->>Mongo: findAndModify (depósito en destino)
    alt depósito en destino falla
        Mongo-->>AS: error (ej. regla de negocio del destino)
        AS->>Mongo: applyReversal - findAndModify (revierte el retiro en origen)
        Mongo-->>AS: origen restaurado
        AS->>Mongo: save(Movement DEPOSIT compensación)
        AS->>Mongo: findByIdempotencyKey(retiro original + ":excess-fee")
        opt el retiro original había cobrado comisión
            AS->>Mongo: refundFee (Movement FEE_REVERSAL)
        end
        AS-->>GW: error original (la transferencia no se completó)
    else depósito exitoso
        Mongo-->>AS: cuenta destino actualizada
        AS->>Mongo: save(Movement DEPOSIT, destino)
        AS-->>GW: 201 Created (TransferResponse)
    end
    GW-->>Cliente: respuesta
```

## Pata de transferencia pedida por yanki-service (coreografía Kafka)

`account-service` no es un microservicio nuevo, pero expone esta pata para que `yanki-service`
(que sí lo es) mueva plata real sin llamarlo por REST — reutiliza los mismos métodos públicos
`deposit()`/`withdraw()`/`reverseWithdrawal()` de arriba, invocados desde un consumer de Kafka en
vez de un controller HTTP.

```mermaid
sequenceDiagram
    participant KafkaReq as Kafka (yanki.account-operation-requests)
    participant AS as account-service
    participant Mongo as accountdb
    participant KafkaRes as Kafka (yanki.account-operation-results)

    KafkaReq->>AS: AccountOperationRequest (leg, operationType, idempotencyKey)
    alt idempotencyKey termina en ":compensation"
        AS->>AS: reverseWithdrawal() - sin contar límite/comisión, devuelve la comisión original
    else solicitud normal
        AS->>AS: deposit() / withdraw() según operationType
    end
    AS->>Mongo: (mismo camino que las peticiones REST de arriba)
    AS->>KafkaRes: publish AccountOperationResult (success/failure, siempre se publica)
```
