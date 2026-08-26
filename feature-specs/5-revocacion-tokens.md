# Revocación de tokens (logout)

## Objetivo

Hoy un JWT emitido por `POST /api/auth/login` es válido durante
`app.jwt.expiration-ms` (24hs por default) pase lo que pase — no existe
`POST /api/auth/logout`, ni forma de invalidar un token antes de que
expire solo. Si a un analista le roban el token, o se lo desvincula de la
empresa, ese token sigue sirviendo hasta que expire por su cuenta. Para un
backoffice financiero eso es un tiempo de exposición inaceptable.

## 1. Por qué esto no es trivial con JWT stateless

`SecurityConfig` usa `SessionCreationPolicy.STATELESS` a propósito — nada
de sesión en el servidor, el propio JWT es la prueba de identidad. Eso es
lo que hace simple escalar horizontalmente, pero significa que "invalidar
un token" necesita *algo* de estado compartido en algún lado, porque la
firma criptográfica del token va a seguir siendo válida igual.

La alternativa de "tokens de vida muy corta + refresh" evita necesitar una
denylist, pero acá `app.jwt.expiration-ms` ya está en 24hs — cambiar eso
es una decisión de producto aparte (afecta a cada cliente de la API), así
que este spec asume que el access token sigue viviendo 24hs y resuelve la
revocación con una denylist explícita.

## 2. Diseño: denylist por `jti`, no allowlist

Guardar cada token EMITIDO (allowlist) requeriría una fila por login y
crecería sin límite. Guardar sólo los REVOCADOS (denylist) es lo que se
implementa acá — la inmensa mayoría de los tokens nunca se revocan, así
que la tabla se mantiene chica.

Cambios necesarios:

1. **`JwtIssuer` tiene que emitir un claim `jti`** (UUID aleatorio) — hoy
   no lo hace, no hay forma de identificar un token individual para
   revocarlo. Se agrega junto a `sub`/`roles`/`usuarioId`.
2. **Tabla `tokens_revocados`** (`jti UUID PK`, `usuario_id UUID`,
   `expira_en TIMESTAMPTZ`, `revocado_en TIMESTAMPTZ DEFAULT now()`).
   `expira_en` es la expiración *original* del token — no hace falta
   guardar la fila para siempre, una vez pasada esa fecha el token ya no
   sería válido igual por expiración natural, así que se puede purgar.
3. **`POST /api/auth/logout`**: lee `jti`/`exp` del propio token del
   caller (`@AuthenticationPrincipal Jwt jwt`) e inserta la fila. No
   requiere body.
4. **`jwtDecoder()` en `SecurityConfig` gana un validator adicional**
   (`OAuth2TokenValidator<Jwt>` custom, agregado a la cadena junto con
   `JwtValidators.createDefault()`) que chequea si el `jti` del token
   está en `tokens_revocados`. Si está, el token se rechaza igual que uno
   vencido — mismo camino de error, mismo `RestAuthenticationEntryPoint`
   que ya existe.
5. **Ese chequeo corre en every request**, así que no puede ser un SELECT
   directo a la base en cada llamada sin pagar el costo. `spring.cache.type=caffeine`
   ya está configurado en `application.properties` desde el principio del
   proyecto pero no lo usa nada todavía — encaja perfecto acá: cachear
   "¿está revocado este jti?" con un TTL corto (ej. 60s). En el caso común
   (no revocado) el costo es un miss de cache + un SELECT ocasional, no
   uno por request. Al revocar, se invalida la entrada de cache de ese
   `jti` puntual para que el logout sea efectivo de inmediato y no recién
   después del TTL.
6. **Job de limpieza** (`@Scheduled`, mismo patrón que
   `OutboxEventPublisher`/`ScoringReconciliationJob`) que borra de
   `tokens_revocados` las filas con `expira_en` en el pasado — evita que
   la tabla crezca indefinidamente aunque nadie llame logout nunca.

## 3. Alcance de este pase

Se implementa: claim `jti`, tabla + validator + cache, endpoint de
logout, job de limpieza.

**No se implementa:**
- **Rotación de refresh tokens.** `app.jwt.refresh-expiration-ms` sigue
  sin usarse — login sólo emite access token, igual que hoy. Revocación
  y refresh son problemas relacionados pero distintos; mezclarlos en el
  mismo pase infla el scope sin necesidad.
- **"Revocar todos los tokens de un usuario"** (ej. al cambiar la
  contraseña, o que un admin fuerce el logout de otro usuario). El
  mecanismo de acá es por `jti` individual. Extenderlo a "todos los
  tokens emitidos antes de tal fecha para tal usuario" es una consulta
  distinta (por `usuario_id` + rango de fecha en vez de por `jti` exacto)
  y una decisión de producto sobre quién puede forzar el logout de quién
  — no hay todavía un endpoint de cambio de contraseña con el que
  enganchar esto.
