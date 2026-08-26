# Rate limiting en login/registro

## Objetivo

`POST /api/auth/login` y `POST /api/auth/register` son, por diseño, los
únicos dos endpoints públicos de toda la API (`SecurityConfig` los deja
en `permitAll`). Hoy no hay ningún límite de intentos — un atacante puede
probar contraseñas contra un email conocido, o crear usuarios en loop,
sin fricción. Confirmado por grep: no hay `bucket4j` ni ningún
rate-limiter en `build.gradle` todavía.

## 1. Por qué no el resilience4j que ya está

`spring-cloud-starter-circuitbreaker-resilience4j` (usado para las
llamadas salientes a Nosis, `feature-specs/2-implementar-sagas.md`) trae
transitivamente `resilience4j-ratelimiter` — ya está en el classpath, se
puede confirmar con `./gradlew dependencies`. Pero el `RateLimiter` de
resilience4j está pensado para limitar *llamadas salientes* con un nombre
fijo (como el círculo `"nosis"` que ya existe) — no para limitar
*llamadas entrantes* por IP/cliente dinámicamente. Forzarlo a ese uso
significaría crear una instancia de rate limiter por IP en runtime, que no
es el caso de uso para el que está diseñada esa librería.

## 2. Diseño: filtro propio con Caffeine

`spring.cache.type=caffeine` está configurado desde el arranque del
proyecto y, igual que en `feature-specs/5-revocacion-tokens.md`, no lo usa
nada todavía. Un contador de intentos por IP con ventana corta es
exactamente el caso de uso para el que Caffeine (con expiración
por-entrada) está bueno, y evita sumar una dependencia nueva sólo para
esto.

- `LoginRateLimitFilter` (`OncePerRequestFilter`), registrado en la
  cadena de filtros **antes** de `BearerTokenAuthenticationFilter` —
  tiene que rechazar antes de que el request llegue a Spring Security,
  no depende de autenticación (obvio: el punto es proteger el login).
- Aplica sólo a `POST /api/auth/login` y `POST /api/auth/register`. El
  resto de la API ya requiere un JWT válido — ahí el costo de un ataque
  de fuerza bruta es mucho más alto (hay que tener un token primero).
- Clave del contador: IP de origen (`request.getRemoteAddr()`, mismo dato
  que ya se usa para `ip_origen` en `feature-specs/4-auditoria.md`).
- Ventana: ej. 10 intentos por IP cada 5 minutos (`Caffeine.newBuilder().expireAfterWrite(5, MINUTES)`,
  incrementando un contador por IP). Al superar el límite, `429 Too Many
  Requests` con header `Retry-After`, mismo formato de error JSON que ya
  usa `GlobalExceptionHandler`/`RestAuthenticationEntryPoint` para
  consistencia.
- Los límites (intentos, ventana) van en `application.properties`
  (`app.security.rate-limit.login.*`), no hardcodeados — mismo patrón que
  `app.scoring.reconciliacion.*`.

## 3. El trade-off de bloquear por IP vs. por cuenta

Un límite por IP no protege bien contra un atacante distribuido (muchas
IPs, pocos intentos cada una) — pero un límite por **cuenta** (bloquear el
email después de N fallos) tiene el problema inverso: cualquiera puede
bloquear la cuenta de otra persona a propósito con sólo fallar el login
repetidas veces con su email. Este spec implementa sólo el límite por IP,
que es el que no tiene ese efecto secundario. Bloqueo por cuenta queda
como posible capa adicional, no como reemplazo.

## 4. Alcance de este pase

Se implementa: filtro de rate limiting por IP sobre `/api/auth/login` y
`/api/auth/register` únicamente, backed by Caffeine, límites
configurables.

**No se implementa:**
- Bloqueo de cuenta por intentos fallidos.
- CAPTCHA o cualquier verificación humana.
- Rate limiting general para el resto de la API (el mecanismo del filtro
  es reusable — extenderlo a todos los endpoints autenticados es un
  cambio chico si hace falta más adelante, pero no está pedido hoy y
  cambia el perfil de uso esperado de la API para clientes legítimos, que
  merece su propia conversación sobre límites razonables).
