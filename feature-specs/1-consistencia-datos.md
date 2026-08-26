# Consistencia de datos

## Objetivo

Garantizar que un movimiento de plata nunca quede a mitad de camino: si se
cobra una cuota, se acredita el préstamo y se dispara la notificación al
cliente, las tres cosas ocurren atómicamente o ninguna ocurre. Este documento
define los tres mecanismos que lo garantizan y el contrato concreto que debe
cumplir cualquier flujo de negocio que toque plata.

## 1. Transaccionalidad fuerte dentro del monolito

Cada operación de negocio que mueve plata (originar un préstamo, cobrar una
cuota, liquidar un cheque) y su asiento contable correspondiente se escriben
en **la misma transacción de base de datos** (`@Transactional`, aislamiento
`READ_COMMITTED`, el default de Postgres/Spring).

- Las columnas de saldo (`cuentas_corrientes.saldo`) usan **locking
  optimista** vía la columna `version`. Una escritura concurrente sobre la
  misma cuenta lanza `OptimisticLockException`, que se traduce a `409
  Conflict`; el llamador debe reintentar leyendo el estado actual.
- Ninguna operación de negocio hace `UPDATE` directo sobre una columna de
  saldo. Ver punto 3.

## 2. Idempotencia explícita en toda escritura de ledger

`movimientos_cta_cte` y `asientos_contables` tienen una columna
`idempotency_key` `NOT NULL UNIQUE`. Todo método de servicio que escribe en
estas tablas:

1. Recibe (o deriva determinísticamente) una `idempotency_key`.
2. Antes de escribir, busca si ya existe una fila con esa key. Si existe,
   devuelve el resultado existente sin volver a aplicar el efecto (reintento
   seguro de una operación que ya se completó).
3. Si dos requests concurrentes con la misma key llegan a la vez, uno gana y
   el otro choca contra la constraint `UNIQUE`. Ese caso se traduce a `409
   Conflict` (no se intenta "recuperar" dentro de la misma transacción: en
   Postgres, una vez que una sentencia falla, la transacción queda abortada y
   no acepta más comandos hasta el rollback). El cliente debe releer el
   recurso, no reintentar la escritura a ciegas.

Convención de las keys:

- Operaciones que ocurren una única vez en la vida de una entidad (ej.
  originación de un préstamo) derivan la key del id de esa entidad
  server-side: `"prestamo-originacion-" + prestamoId`.
- Operaciones que pueden repetirse (ej. pagos parciales de una cuota) reciben
  la key del llamador (`PUT /api/cuotas/{id}/pago` requiere
  `idempotencyKey` en el body). Es responsabilidad del cliente generar una
  key nueva por intento de pago real y reusar la misma key si reintenta el
  mismo request.

## 3. Doble entrada real: el ledger es la única fuente de verdad de saldos

El módulo de contabilidad no es un log de auditoría, es la fuente de verdad
de saldos. Dos servicios concentran **toda** escritura de saldo:

- `CuentaCorrienteLedgerService.registrarMovimiento(...)`: única forma de
  modificar `cuentas_corrientes.saldo`. Escribe una fila en
  `movimientos_cta_cte` (con el snapshot `saldo_posterior`) y actualiza el
  saldo en la misma operación. Ningún otro service llama
  `cuentaCorrienteRepository.save()` para tocar `saldo`.
  - Convención de signo: `saldo` representa lo que el cliente le debe a la
    financiera. `DEBITO` aumenta el saldo (se le genera un cargo), `CREDITO`
    lo disminuye (paga o se le acredita).
- `AsientoContableService.crearConLineas(...)`: única forma de crear un
  asiento con sus líneas (`movimientos_contables`). Antes de persistir valida
  que `sum(debe) == sum(haber)` sobre todas las líneas y que cada línea tenga
  `debe > 0` xor `haber > 0` (mismo invariante que la constraint
  `ck_movimiento_contable_exclusivo` de la base). Si no está balanceado,
  lanza `IllegalArgumentException` (`400`) y la transacción completa —
  incluyendo el cambio de estado de negocio que la disparó— hace rollback.

`AsientoContableService.create(...)` (el endpoint REST genérico ya
existente) sigue permitiendo cargar un asiento suelto sin líneas para casos
administrativos; `crearConLineas` es el que usan los flujos de negocio y es
el que garantiza partida doble real.

## 4. Outbox transaccional

Cualquier evento que deba disparar un side-effect fuera de la transacción de
negocio (notificar al cliente, encolar cobranza, llamar a Nosis) se escribe
en `outbox_events` **dentro de la misma transacción** vía
`OutboxEventService.publicarEvento(...)`. Esto evita el problema clásico de
"grabé en Postgres pero se cayó antes de mandar el mensaje a la cola": si la
transacción de negocio falla, el evento nunca se escribe; si se confirma, el
evento ya está ahí para publicarse.

Un proceso separado (`OutboxEventPublisher`, `@Scheduled`, polling cada 5s
por default) lee los eventos con `publicado = false` y los entrega a un
`OutboxEventSink`. Cada evento se publica en su propia transacción corta, así
una falla puntual no bloquea el resto del batch.

`OutboxEventSink` es una interfaz — la implementación por default
(`LoggingOutboxEventSink`) sólo loguea. Reemplazarla por una que publique a
SQS real es un cambio de una sola clase; no está hecho en este pase porque
requiere infraestructura de AWS que no está configurada en este entorno
(ver `spring-cloud-aws-starter-sqs`, ya en el classpath pero sin cola real
todavía).

## 5. Alcance de este pase — qué flujos quedan cableados

El patrón (transacción única + ledger + outbox) se cablea completo en dos
flujos, elegidos por no tener ambigüedad de negocio en el asiento contable
resultante (usan cuentas ya seedeadas en `plan_cuentas`):

| Flujo | Trigger | Asiento | Movimiento cta-cte |
|---|---|---|---|
| Originación de préstamo | `PrestamoService.create` | Debe `1.1.03 Préstamos otorgados` / Haber `1.1.02 Banco` | ninguno |
| Cobro de cuota | `CuotaPrestamoService.registrarPago` | Debe `1.1.02 Banco` / Haber `1.1.05 Cuentas a cobrar clientes` (por el *delta* pagado, no el acumulado) | `CREDITO` por el delta, sólo si el cliente ya tiene una `cuenta_corriente` en ARS — si no tiene, se omite sin fallar el pago |

**Liquidación de cheques queda explícitamente fuera de este pase.** Requiere
una decisión de producto que todavía no está tomada: si el adelanto sobre un
cheque descontado debita la cuenta corriente del cliente en el momento del
descuento o en el momento de la liquidación, y qué pasa si el cheque rebota
(`RECHAZADO`). Cablearlo a ciegas con la convención de signo de la sección 3
sería inventar semántica de negocio. El servicio `ChequeService` sigue
existiendo sin tocar; cuando se defina la regla, se cablea con el mismo
patrón (`CuentaCorrienteLedgerService` + `AsientoContableService.crearConLineas`
+ `OutboxEventService.publicarEvento`, dentro del mismo `@Transactional` que
ya envuelve el cambio de estado del cheque).

`idempotency_keys` (la tabla genérica, distinta de las columnas
`idempotency_key` de `movimientos_cta_cte`/`asientos_contables`) tampoco se
usa en este pase: está pensada para deduplicar llamadas a nivel API/externas
en general, no específicamente para el ledger, y no hay todavía un caso de
uso concreto que la necesite.
