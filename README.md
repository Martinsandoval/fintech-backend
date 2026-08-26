# fintech-backend

Backend de una financiera: originación de préstamos y descuento de
cheques, cuenta corriente, contabilidad de doble entrada, scoring
crediticio contra un proveedor externo (simulado), y auditoría inmutable
de acciones sensibles.

Java 21 · Spring Boot 4.0.7 · PostgreSQL · Flyway · Gradle.

## Empezar rápido

Requisitos: JDK 21 y una instancia de Postgres corriendo (local o
remota).

```bash
export PGUSER=tu_usuario_postgres
export PGPASSWORD=tu_password        # vacío si tu instancia no pide auth
./gradlew bootRun
```

No hace falta crear la base a mano ni correr las migraciones por
separado:

- Si `fintech_db` no existe, se crea sola al arrancar
  (`DatabaseInitializer`).
- Flyway corre las migraciones de `src/main/resources/db/migration/`
  automáticamente.
- Si la tabla `clientes` queda vacía después de migrar, `DevDataSeeder`
  carga datos de ejemplo (clientes, préstamos con cronograma de cuotas,
  cheques, cuentas corrientes) — vía los services reales, no SQL directo,
  así que quedan sujetos a las mismas validaciones y generan los mismos
  asientos contables que datos cargados por un usuario.

La app queda arriba en `http://localhost:8080`.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health check (sin auth): `http://localhost:8080/actuator/health`

## Configuración

Todo vive en `src/main/resources/application.properties`, con default de
desarrollo y override por variable de entorno en cada property. Las que
importan para correr localmente:

| Variable | Default dev | Qué es |
|---|---|---|
| `PGHOST` / `PGPORT` / `PGDATABASE` | `localhost` / `5432` / `fintech_db` | conexión a Postgres |
| `PGUSER` / `PGPASSWORD` | tu usuario del SO / vacío | credenciales de Postgres |
| `JWT_SECRET` | clave de dev incluida | firma HS256 de los JWT — **cambiar antes de cualquier deploy real** |
| `FIELD_ENCRYPTION_KEY` | clave de dev incluida | cifrado a nivel de campo (CUIT, respuestas de scoring) — **cambiar antes de cualquier deploy real** |
| `AWS_REGION` | `us-east-1` | requerida por los starters de AWS aunque no se use ninguna credencial real todavía |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | orígenes permitidos, separados por coma |
| `NOSIS_SIMULAR_FALLA` | `true` | el cliente de scoring es simulado (no hay credenciales reales de Nosis); en `true`, el primer intento por CUIT falla a propósito para poder ver el camino de reconciliación |

`JWT_SECRET` y `FIELD_ENCRYPTION_KEY` tienen un valor por default que
sólo sirve para desarrollo local — no usarlo en ningún ambiente
compartido.

## Autenticación

No hay usuarios sembrados por default — el primer usuario se crea vía
`/api/auth/register`.

```bash
# registrarse (rol ANALISTA siempre; no hay forma de auto-otorgarse ADMIN)
curl -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"vos@ejemplo.com","nombre":"Nombre","apellido":"Apellido","password":"unaClaveDe8+"}'

# login
curl -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"vos@ejemplo.com","password":"unaClaveDe8+"}'
```

Ambas devuelven `{ accessToken, tokenType, expiresInMs, usuario }`. Usar
el token como `Authorization: Bearer <accessToken>` en el resto de la
API — todo excepto `register`, `login`, `/actuator/health` y la
documentación de Swagger requiere el header. `POST /api/auth/logout`
revoca el token actual antes de que expire solo.

Roles: `ANALISTA` (default) y `ADMIN` (sólo puede leer `/api/auditoria`
por ahora — no hay endpoint para promover a alguien a ADMIN todavía).

## Qué hay implementado

Dominio principal, bajo `src/main/java/com/example/fintech/`:

| Paquete | Qué resuelve |
|---|---|
| `cliente`, `librador` | altas y datos de sujetos de crédito |
| `solicitud`, `scoring`, `decision` | solicitud de crédito → saga de scoring (Nosis simulado) → aprobación/rechazo/revisión manual |
| `prestamo` | originación, cronograma de cuotas (Francés/Alemán/Americano), cobros |
| `cheque` | cartera de cheques descontados |
| `cuentacorriente`, `contabilidad` | saldo por cliente y libro mayor de doble entrada — única fuente de verdad de saldos, nunca se pisa un campo `saldo` a mano |
| `outbox` | eventos de dominio para side-effects async, en la misma transacción que el cambio que los origina |
| `usuario`, `security` | registro/login/logout, JWT, revocación, rate limiting en login |
| `auditoria` | rastro append-only (enforced por trigger de Postgres, no sólo por convención) de decisiones sensibles sobre solicitudes/préstamos/cheques |
| `encryption` | cifrado determinístico a nivel de campo para CUIT y datos de scoring |
| `idempotencia`, `integracion` | dedup de operaciones y log de auditoría de llamadas a proveedores externos |

El razonamiento detrás de cada una de estas piezas — por qué, qué
alternativas se descartaron, qué queda deliberadamente afuera — está en
`feature-specs/`. Vale la pena leerlas antes de tocar consistencia
transaccional, sagas, cifrado, auditoría, revocación de tokens,
amortización o rate limiting: ya está pensado, no reinventar.

## Tests

```bash
./gradlew test
```

Cobertura hoy: lógica pura (cifrado, cálculo de amortización) y un test
de concurrencia real sobre el locking optimista de cuenta corriente. Los
flujos de negocio de punta a punta (controllers/services) todavía no
tienen tests automatizados — se verificaron a mano contra la API en cada
sesión de desarrollo.

## Antes de un deploy real

Este backend es un MVP funcional, no producción-ready tal cual está. Lo
que falta, en orden de importancia:

1. Sacar los defaults de `JWT_SECRET`/`FIELD_ENCRYPTION_KEY` del
   repo — hoy son claves reales y funcionales.
2. Paginar los endpoints de listado (`findAll` hoy trae la tabla
   entera; varias tablas como `auditoria_acciones` crecen sin límite
   por diseño).
3. Tests automatizados de los flujos de negocio, no sólo de lógica
   pura.
4. CI (build + test en cada push) y un `Dockerfile` — hoy la única
   forma de correr esto es `./gradlew bootRun` a mano.

Detalle completo de esto y otros gaps (idempotencia a nivel HTTP,
screening AML/UIF, observabilidad) disponible bajo pedido.
