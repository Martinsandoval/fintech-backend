# Observabilidad

## Objetivo

`build.gradle` ya trae `micrometer-registry-prometheus`,
`micrometer-tracing-bridge-otel` y `opentelemetry-exporter-otlp` — pero
`application.properties` no configura nada de `management.tracing.*` ni
`management.otlp.*`, y `management.endpoints.web.exposure.include` nunca
se seteó, así que Boot expone únicamente `/actuator/health` por default.
Es el mismo patrón de "la dependencia está, nada la usa" que ya apareció
con springdoc, Flyway, testcontainers-bom, y el cliente OAuth2 — acá
aplica a observabilidad. Para una app que mueve plata de forma
asincrónica (sagas, outbox, jobs de reconciliación), no tener métricas ni
trazas es un punto ciego real: hoy, si el publisher del outbox se atrasa o
una saga de scoring queda colgada, la única forma de enterarse es
consultar `idempotency_keys`/`outbox_events` a mano por SQL.

## 1. Métricas HTTP — gratis, sólo falta exponerlas

`spring-boot-starter-actuator` + Micrometer ya instrumentan cada request
HTTP automáticamente (latencia, status code, por endpoint) sin código
adicional — sólo hace falta exponer el endpoint:

```properties
management.endpoints.web.exposure.include=health,prometheus,metrics,info
```

`/actuator/prometheus` **no** se agrega a la lista de `permitAll` de
`SecurityConfig` — a diferencia de `/actuator/health` (pensado para un
load balancer que no tiene credenciales), un scraper de Prometheus corre
en la misma red interna que el resto de la infraestructura y puede llevar
autenticación igual que cualquier otro cliente de la API. Mantenerlo
detrás de `anyRequest().authenticated()` es la opción consistente con el
resto de la app.

## 2. Métricas de negocio — lo que realmente hace falta mirar acá

Las métricas HTTP genéricas no dicen si una saga quedó colgada. Lo que sí
importa, expuesto como gauges/counters de Micrometer (`MeterRegistry`
inyectado donde ya existe la lógica, sin tocar el flujo de negocio):

| Métrica | Tipo | De dónde sale |
|---|---|---|
| `outbox.events.pendientes` | gauge | `OutboxEventRepository.findByPublicado(false)`, tamaño |
| `scoring.saga.pendientes` | gauge | `idempotency_keys` con `operacion='scoring_nosis'` y `resultado IS NULL` |
| `scoring.saga.reconciliaciones` | counter | incrementado en cada pasada de `ScoringReconciliationJob` que reintenta algo |
| `prestamos.originados` | counter | incrementado en `PrestamoService.create` |
| `cuentas_corrientes.conflictos_version` | counter | incrementado donde `GlobalExceptionHandler` atrapa `OptimisticLockingFailureException` |

Estas dos últimas son baratas de agregar porque son un `meterRegistry.counter(...).increment()`
en un punto que ya existe; las de "pendientes" son gauges que consultan
un repositorio ya existente — no hace falta ninguna tabla nueva.

## 3. Trazas distribuidas — configurado pero inerte sin colector

```properties
management.tracing.sampling.probability=${TRACING_SAMPLING_PROBABILITY:1.0}
management.otlp.tracing.endpoint=${OTLP_TRACING_ENDPOINT:http://localhost:4318/v1/traces}
```

Con esto, Micrometer Tracing + el bridge de OTel empiezan a generar
trace-id/span-id por request y a correlacionar los saltos entre threads
que ya tiene la app (el listener `@Async` de `ScoringSagaService`, el
`@Scheduled` de los jobs) — que es exactamente donde más cuesta seguirle
el rastro a un request a mano por logs hoy. Los logs ganan
`traceId`/`spanId` en el MDC automáticamente, así que conviene sumarlos al
patrón de logging (`logging.pattern.console`) para poder buscarlos.

Honestidad sobre el alcance: **esto configura el exportador, no levanta
un colector.** No hay Jaeger/Tempo/Grafana corriendo en este entorno —
sin uno escuchando en `OTLP_TRACING_ENDPOINT`, el exportador simplemente
falla en silencio en el background y no genera ningún error visible (así
está diseñado OTel: no romper la app porque el collector no está). El
valor de este pase es que el día que exista un collector real (en
cualquier ambiente), sólo hace falta apuntar la variable de entorno — no
hay que tocar código.

## 4. Alcance de este pase

Se implementa: exposición de `/actuator/prometheus` (autenticado), las
cinco métricas de negocio de la tabla de arriba, configuración del
exportador OTLP con endpoint por variable de entorno.

**No se implementa:**
- **Levantar Prometheus/Grafana/Jaeger.** Es infraestructura, no código
  de la app — mismo criterio que TLS/RDS-KMS en
  `feature-specs/3-cifrado.md`: se documenta qué hay que correr, no se
  inventa infraestructura ficticia en este repo.
- **Reglas de alerta** (ej. "avisar si `scoring.saga.pendientes` > 10 por
  más de 5 minutos") — necesitan Alertmanager u otra herramienta real
  configurada contra las métricas de arriba, no tiene sentido definirlas
  sin un sistema de alertas corriendo para probarlas.
- **Sampling adaptativo o distinto por ambiente.** El default de
  `probability=1.0` (tracear el 100% de los requests) es razonable en
  dev/staging pero caro en producción con volumen real — ajustar eso es
  una decisión operativa por ambiente, no algo para hardcodear acá.
