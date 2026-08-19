# docs — account-service

## `openapi.yaml`

Contrato OpenAPI 3 del servicio. **Es un archivo generado, no se edita a mano:** lo produce
springdoc a partir de los controladores y los DTO, asi que la fuente de verdad siempre es el
codigo. Se versiona para poder revisar el contrato (y sus cambios en el diff) sin levantar el
servicio.

Para regenerarlo despues de tocar un endpoint o un DTO, con el servicio corriendo:

```bash
curl -s http://localhost:8082/v3/api-docs.yaml -o docs/openapi.yaml
```

La version viva, navegable, queda en `http://localhost:8082/swagger-ui.html`.

## `sequence-diagrams.md`

Diagramas de secuencia (Mermaid) de los flujos de negocio del servicio. Estos si se escriben a
mano y se actualizan cuando cambia un flujo.
