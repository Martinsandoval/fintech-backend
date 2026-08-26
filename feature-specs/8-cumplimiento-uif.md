# Screening AML/UIF (PEP y listas de sanciones)

## Objetivo

`feature-specs/4-auditoria.md` ya nombra a la UIF (Unidad de Información
Financiera) como uno de los motivos de la auditoría inmutable. Pero UIF no
es sólo "guardar quién aprobó qué" — para una financiera en Argentina
exige, antes de operar con un cliente, verificar que no sea una Persona
Expuesta Políticamente (PEP) ni figure en listas de sanciones (OFAC, ONU,
etc.). Hoy la única verificación que existe es el scoring crediticio vía
Nosis (`feature-specs/2-implementar-sagas.md`) — eso mide capacidad de
pago, no es screening AML. Son obligaciones legalmente distintas y hoy
sólo está cubierta la primera.

## 1. Mismo patrón que el scoring, proveedor distinto

La forma del problema es idéntica a la saga de Nosis: llamar a un
proveedor externo, lento, no transaccional — así que se resuelve con el
mismo mecanismo de `feature-specs/2-implementar-sagas.md` en vez de
inventar uno nuevo: evento post-commit, listener `@Async`, idempotencia
vía `idempotency_keys`, auditoría de cada intento en `integraciones_log`,
reconciliación si no vuelve la respuesta.

Lo que **no** se reusa es la tabla/enum de scoring crediticio
(`ProveedorScoring`, `resultados_scoring`) — mezclar "resultado de
scoring Nosis" con "resultado de screening AML" en la misma tabla
confundiría dos obligaciones regulatorias distintas con dueños y
respuestas distintas. Se crea un módulo paralelo:

- `ProveedorAml` (enum): `LISTAS_SANCIONES`, `PEP` — quién sea el
  proveedor real (hoy no hay ninguno contratado).
- `resultados_screening_aml` (tabla): `cliente_id`, `proveedor`,
  `resultado` (`LIMPIO` / `COINCIDENCIA_PEP` / `COINCIDENCIA_SANCIONES` /
  `REVISION_MANUAL`), `detalle` JSONB, `fecha`.
- `AmlScreeningClient` (interfaz) + `SimulatedAmlScreeningClient` —
  mismo motivo que `SimulatedNosisScoringClient`: no hay credenciales
  reales de ningún proveedor de listas en este entorno. Determinístico
  por CUIT para que sea reproducible.

## 2. A nivel de negocio: se screenea al cliente, no a la solicitud

El scoring de Nosis se dispara por solicitud de crédito (cada solicitud
puede necesitar un score fresco). El screening AML es distinto: es una
verificación de identidad del cliente en sí, no del monto que está
pidiendo — se hace una vez al alta (y periódicamente después, ver
sección 4), no en cada solicitud.

- `Cliente` gana un campo `estadoKyc`
  (`PENDIENTE`/`APROBADO`/`RECHAZADO`/`REVISION_MANUAL`), default
  `PENDIENTE`.
- `ClienteService.create` dispara la saga de screening al finalizar la
  transacción (mismo mecanismo post-commit que
  `ScoringSagaService.iniciarEvaluacion`), en vez de necesitar que
  alguien lo dispare a mano.
- **`SolicitudCreditoService.create` valida `cliente.estadoKyc ==
  APROBADO` antes de crear la solicitud** — si el cliente todavía está
  `PENDIENTE` (screening en curso) o `RECHAZADO`/`REVISION_MANUAL`,
  rechaza con 400. Esto es lo que hace el control real: sin este chequeo,
  el screening existiría pero no bloquearía nada.

## 3. Qué significa cada resultado

- `LIMPIO` → `estadoKyc = APROBADO`, el cliente puede operar.
- `COINCIDENCIA_SANCIONES` → `estadoKyc = RECHAZADO`. A diferencia del
  scoring crediticio (donde un score bajo es una decisión de negocio
  reversible), una coincidencia en listas de sanciones no es algo que un
  analista pueda simplemente anular desde la API — necesita intervención
  de compliance fuera de este sistema. No se expone un endpoint para
  revertir este estado.
- `COINCIDENCIA_PEP` → `estadoKyc = REVISION_MANUAL`. A diferencia de
  sanciones, ser PEP no es un impedimento automático — sí exige debida
  diligencia reforzada por parte de un humano antes de operar. Esto usa
  el mismo patrón de "cola de revisión manual" que ya existe para
  solicitudes de crédito (`EstadoSolicitud.REVISION_MANUAL`).

## 4. Alcance de este pase

Se implementa: módulo AML paralelo (cliente/tabla/enum/service/job de
reconciliación, mismo mecanismo que scoring), campo `estadoKyc` en
`Cliente`, gating en `SolicitudCreditoService.create`.

**No se implementa:**
- **ROS (Reporte de Operación Sospechosa).** Filar un ROS ante la UIF es
  un proceso regulatorio con revisión humana obligatoria — no es algo que
  tenga sentido automatizar de punta a punta sin un flujo de compliance
  que hoy no existe en la app (no hay rol de "oficial de cumplimiento",
  no hay bandeja de casos sospechosos). Este pase deja al cliente en
  `REVISION_MANUAL` cuando corresponde; qué hace un humano con eso
  después queda fuera.
- **Re-screening periódico.** UIF espera revisar clientes existentes
  periódicamente, no sólo una vez al alta — no hay job para eso todavía
  (se podría modelar con el mismo `@Scheduled` que ya usan
  `OutboxEventPublisher`/`ScoringReconciliationJob`, pero define su
  propia cadencia/criterio de selección, que es una decisión de producto
  aparte).
- **Proveedor real.** Igual que Nosis, esto corre contra un cliente
  simulado hasta que haya credenciales reales de un proveedor de listas.
