# Auditoría inmutable

## Objetivo

Toda decisión sensible que un usuario (analista/admin) toma sobre un
cliente — aprobar/rechazar/derivar a revisión manual un crédito, cambiar el
estado de un préstamo, anular o rechazar un cheque — queda registrada en una
tabla separada, append-only, con quién la tomó y cuándo. Esto es lo que
pide BCRA/UIF y lo que resuelve una disputa ("¿quién aprobó esto y en base
a qué estado previo?").

## 1. Qué cuenta como "acción sensible" acá

No es un mecanismo genérico para auditar cualquier cosa — son los cambios
de estado que ya existen como endpoints en el código, disparados por un
usuario autenticado a través de la API:

| Acción | Endpoint | entidad_tipo |
|---|---|---|
| Decisión manual sobre una solicitud (aprobar/rechazar/derivar) | `PUT /api/solicitudes/{id}/estado` | `SOLICITUD_CREDITO` |
| Cambio de estado de un préstamo (mora, cancelación, refinanciación) | `PUT /api/prestamos/{id}/estado` | `PRESTAMO` |
| Cambio de estado de un cheque (anulación, rechazo, endoso) | `PUT /api/cheques/{id}/estado` | `CHEQUE` |

**No incluye transiciones automáticas.** Cuando `ScoringSagaService`
resuelve una solicitud (`EN_EVALUACION` → `APROBADA`/`RECHAZADA` por
scoring de Nosis), no hay un usuario detrás de esa llamada — esa decisión
ya tiene su propio rastro completo en `resultados_scoring` +
`resultados_decision` + `integraciones_log`
(`feature-specs/2-implementar-sagas.md`). Escribir ahí *también* una fila
de `auditoria_acciones` sin un usuario real sería inventar un actor
"sistema" que no aporta nada sobre lo que ya queda registrado. Esta tabla
es específicamente para "un humano autenticado tomó esta decisión desde la
API".

**"Modificar tasa" queda fuera de este pase.** El spec original lo
menciona, pero hoy no existe ningún endpoint que permita cambiar
`tasa_anual` después de originado un préstamo — no hay nada que auditar
todavía. Cuando se agregue ese endpoint, se audita con el mismo mecanismo
que los tres de arriba.

## 2. Qué se guarda

Por cada acción: quién (`usuario_id` + `usuario_email`, tomados del JWT —
el email se **snapshotea** en la fila en vez de sólo guardar el id, para
que la fila de auditoría no cambie de significado si ese usuario cambia de
email más adelante), qué acción, sobre qué entidad, estado anterior →
estado nuevo, IP de origen, y cuándo. `detalle` (JSONB, opcional) queda
para cuando un endpoint sensible tenga un campo de contexto real que
capturar (ej. un motivo de rechazo) — no se llena todavía porque no existe
ese campo en ningún request actual.

## 3. Inmutable de verdad, no por convención

"Append-only" no es sólo "el código no llama a update ni a delete":

- La entidad Java (`AuditoriaAccion`) no tiene setters — se construye
  completa en el constructor, no hay forma de mutarla en memoria después.
- El repositorio (`AuditoriaAccionRepository`) extiende `Repository<T,ID>`
  directamente en vez de `JpaRepository`, y sólo declara los métodos que
  necesita (`save`, `findById`, `findAll`, un par de queries) — `delete`/
  `deleteById` ni siquiera existen en la interfaz, así que no hay forma de
  llamarlos por error desde el código de la aplicación.
- La tabla tiene un trigger de Postgres (`BEFORE UPDATE OR DELETE`) que
  rechaza la operación con una excepción. Esto es lo que realmente importa:
  ni un bug en la aplicación, ni alguien con acceso directo a `psql`, puede
  alterar una fila ya escrita sin que la base lo rechace explícitamente.

## 4. Quién puede leerla

`GET /api/auditoria` (y las variantes filtradas por entidad/usuario) están
restringidas a `ROLE_ADMIN` (`@PreAuthorize`, primera vez que se usa
autorización a nivel de método en la app — hace falta `@EnableMethodSecurity`).
Un registro de auditoría que cualquier analista puede leer libremente
(incluyendo qué hicieron sus compañeros) es en sí mismo un problema de
confidencialidad. Esto no cambia el acceso de **escritura** a los tres
endpoints de estado — cualquier analista autenticado los sigue pudiendo
llamar igual que hoy; sólo se restringe quién puede *ver* el historial de
auditoría.
