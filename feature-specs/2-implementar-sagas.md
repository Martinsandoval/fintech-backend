# Sagas con servicios externos

## Objetivo

Las llamadas a Nosis/AFIP/BNA son I/O externo, lento y no transaccional con
nuestra base. Nunca bloqueamos una transacción de DB esperando una respuesta
externa, y nunca asumimos que la respuesta va a llegar. Este documento define
cómo se modela una saga corta contra un proveedor externo y cablea la
primera (scoring crediticio vía Nosis) como referencia completa.

## 1. Forma de una saga

Una saga acá es: una operación de negocio pasa a un estado "esperando
externo", se dispara la llamada **después** de que ese cambio de estado
quede confirmado en la base (nunca antes — si se dispara dentro de la misma
transacción y esa transacción hace rollback, quedó una llamada externa
disparada sobre una operación que "nunca pasó"), y cuando la respuesta llega
(o se reintenta porque nunca llegó) se resuelve el estado final en una
transacción separada.

Mecanismo concreto en Spring:

1. El método que dispara la saga hace su cambio de estado y publica un
   `ApplicationEvent` dentro de la misma transacción (`@Transactional`).
2. Un listener separado escucha ese evento con
   `@TransactionalEventListener(phase = AFTER_COMMIT)` — Spring garantiza que
   sólo se ejecuta si la transacción que publicó el evento confirmó. Ese
   listener además es `@Async` (executor dedicado, no el pool por default),
   así la llamada externa nunca corre en el thread que atendió el request
   HTTP ni dentro de ninguna transacción de DB.
3. Cuando la llamada externa vuelve (éxito o error), se abre una transacción
   nueva que persiste el resultado y avanza el estado de negocio.

## 2. Idempotencia del disparo — tabla `idempotency_keys`

Antes de disparar una llamada externa, se reserva una fila en
`idempotency_keys` con `(operacion, key)` — por ejemplo
`("scoring_nosis", solicitudId)`. La constraint única `(key, operacion)` es
la que garantiza que un doble-click o una re-entrada no dispare la llamada
dos veces: si la fila ya existe, no se dispara de nuevo. `resultado` (JSONB,
nullable) se completa recién cuando la respuesta externa se procesó — hasta
entonces, `resultado IS NULL` es la señal de "todavía esperando", y es
exactamente lo que usa el job de reconciliación (sección 4).

Esta es la misma tabla que quedó **sin usar** en
`feature-specs/1-consistencia-datos.md` por no tener un caso de uso
concreto todavía — este es el caso de uso.

## 3. Auditoría — tabla `integraciones_log`

Todo intento de llamada externa (exitoso o no) se registra en
`integraciones_log` (servicio, endpoint, request, response, estado_http,
exitoso, duración). No es opcional: es lo que permite responder "¿le
pegamos a Nosis por este cliente?" sin tener que confiar en logs de
aplicación, y es la fuente para el job de reconciliación cuando necesita
decidir si reintentar o alertar.

## 4. Reconciliación — el webhook (o la respuesta async) puede no llegar nunca

Un job (`@Scheduled`) busca en `idempotency_keys` las filas con
`resultado IS NULL` y `creado_en` más viejo que un umbral configurable
(`app.scoring.reconciliacion.umbral-segundos`, corto en dev, debería ser
minutos/horas en producción). Por cada una, vuelve a disparar la saga (mismo
mecanismo que el disparo original — no hay código de reintento separado) y
lo deja logueado. El intervalo del job es
`app.scoring.reconciliacion.intervalo-ms`.

Esto es intencionalmente el único mecanismo de reintento: no hay un
`@Retry` de bajo nivel alrededor de la llamada HTTP en este pase. Un fallo
transitorio de red y un webhook que nunca llegó son, desde el punto de vista
del negocio, el mismo problema ("la saga no se resolvió") y se resuelven con
el mismo mecanismo.

## 5. Alcance de este pase

Se cablea completo el flujo de **scoring crediticio vía Nosis**, que es el
que dispara la decisión de una `SolicitudCredito`:

`INICIADA` → (`POST /api/solicitudes/{id}/iniciar-evaluacion`) → `EN_EVALUACION`
→ (saga async) → se escribe `resultados_scoring` + `resultados_decision` →
`APROBADA` / `RECHAZADA` / `REVISION_MANUAL` según el score, y se publica un
evento outbox `SOLICITUD_RESUELTA` (reusando la infraestructura de
`feature-specs/1-consistencia-datos.md`).

Regla de decisión (deliberadamente simple, sin motor de reglas
configurable — `reglas_decision`/`regla_id` quedan sin usar en este pase):
score ≥ 700 → `APROBADO`, score < 400 → `RECHAZADO`, resto →
`DERIVADO_MANUAL`.

**No hay credenciales reales de Nosis en este entorno.** `NosisScoringClient`
es una interfaz; la única implementación por ahora es
`SimulatedNosisScoringClient`, que devuelve un score determinístico
derivado del CUIT (mismo CUIT → mismo score, para que sea reproducible) y,
controlado por `app.integraciones.nosis.simular-falla-primer-intento`
(default `true` en dev), hace fallar el primer intento por CUIT para poder
demostrar el camino de reconciliación sin depender de un proveedor real
caído. Cambiar a un cliente HTTP real contra la API de Nosis es una
implementación nueva de la misma interfaz — el resto de la saga
(orquestación, idempotencia, reconciliación) no cambia.

**AFIP y BNA quedan fuera de este pase.** `proveedor_scoring` ya tiene los
tres valores en el enum de base porque el dominio los va a necesitar, pero
qué dato puntual se les consulta y qué decisión dispara cada uno es una
definición de producto que todavía no está tomada (¿AFIP para validar
condición fiscal del CUIT antes de aprobar? ¿BNA para verificar que un
cheque no esté denunciado antes de descontarlo?). Cuando se defina, se
cablea con el mismo mecanismo: evento transaccional post-commit, listener
async, `idempotency_keys` para el disparo, `integraciones_log` para
auditoría, reconciliación para lo que no vuelve.
