# Cifrado

## Alcance de este pase

El spec original mezcla tres cosas con capas de implementación completamente
distintas: TLS en tránsito, cifrado de disco en RDS/S3, y cifrado a nivel de
campo. Las primeras dos son configuración de infraestructura (load
balancer/ingress, parámetros de la instancia RDS, política del bucket S3) —
no hay Terraform/CloudFormation en este repo, así que no hay nada que
"implementar" ahí sin inventar infraestructura ficticia. Quedan documentadas
abajo como referencia de qué hay que configurar cuando exista IaC, pero el
trabajo de código de este pase es **cifrado a nivel de campo**, la única
pieza que vive en la aplicación.

## 1. Fuera de este repo (infraestructura)

- **TLS en tránsito**: terminación TLS 1.2+ en el load balancer/ingress
  delante de la app, y `sslmode=require` (o superior) en la conexión a
  Postgres. Nada que cablear en Spring — es config de despliegue.
- **RDS con KMS**: `storage_encrypted = true` + `kms_key_id` al provisionar
  la instancia.
- **S3 con KMS**: default encryption del bucket (`aws:kms`) al provisionarlo.

Cuando exista IaC para este proyecto, estos tres puntos son la checklist;
hoy no hay dónde poner ese código.

## 2. Cifrado a nivel de campo

Objetivo: que un `pg_dump` de la base, o acceso de lectura directo a las
tablas, no exponga en texto plano el CUIT de un cliente ni el contenido
crudo de una consulta de scoring. Se implementa en la capa de aplicación
(un `AttributeConverter` de JPA), no con `pgcrypto` a nivel de SQL — con
JPA/Hibernate manejando entidades por atributo (no por sentencia SQL cruda),
un converter transparente encaja mejor que envolver cada query a mano con
`pgp_sym_encrypt`/`decrypt`, y sigue funcionando con los métodos derivados
de Spring Data (`findByCuit`) sin reescribir nada.

### Por qué determinístico, no aleatorio

El cifrado "de libro" (AES-GCM con nonce aleatorio) es semánticamente más
seguro, pero **rompe la búsqueda por igualdad y la constraint UNIQUE**: el
mismo CUIT cifrado dos veces da dos textos cifrados distintos, así que
`WHERE cuit = ?` y la constraint única de `clientes`/`libradores` dejan de
significar lo que deberían. La alternativa es cifrado determinístico: mismo
texto plano → mismo texto cifrado, siempre.

Construcción usada (`FieldEncryptor`):

1. De una clave maestra de 256 bits se derivan dos subclaves por HMAC-SHA256
   (una para cifrar, otra sólo para derivar el nonce) — separación de claves
   para no reusar la misma clave en dos roles distintos.
2. El nonce de AES-256-GCM (12 bytes) se deriva determinísticamente:
   `HMAC-SHA256(nonceKey, textoPlano)[0:12]`. Mismo texto plano → mismo
   nonce → mismo texto cifrado. Esto es lo que preserva `UNIQUE` y
   `findByCuit`.
3. El texto cifrado sigue siendo AES-GCM real (autenticado): un intento de
   modificar el valor cifrado en la base (o restaurar un dump viejo sobre
   una fila nueva) hace que el `AttributeConverter` falle al leer, en vez de
   devolver silenciosamente basura.

Costo aceptado: como es determinístico, dos filas con el mismo valor de
texto plano son detectables como iguales sin descifrar (se ve que dos
`cuit` cifrados coinciden). Para `cuit` esto no filtra nada nuevo — ya es
`UNIQUE`, dos filas nunca deberían coincidir — así que el costo es cero en
la práctica.

### Campos cifrados en este pase

| Campo | Tabla | Motivo | Sigue siendo buscable/único |
|---|---|---|---|
| `cuit` | `clientes` | PII fiscal | sí (`findByCuit`, `UNIQUE`) |
| `cuit` | `libradores` | PII fiscal | sí (`UNIQUE`) |
| `respuesta_raw` | `resultados_scoring` | puede traer datos de buró/ingresos del proveedor | no hace falta — nunca se busca por contenido, sólo se lee entero |

La constraint `CHECK (cuit ~ '^\d{11}$')` no puede vivir más en la base
(el valor almacenado es el cifrado, no 11 dígitos) — se mueve a
`@Pattern` en el entity, que valida el texto plano *antes* de que el
converter lo cifre. `respuesta_raw` pasa de `jsonb` a `text`: sigue siendo
un `Map<String,Object>` del lado Java (un converter dedicado
serializa/cifra al guardar y descifra/deserializa al leer), pero la base ya
no puede indexar ni consultar su contenido — tampoco lo hacía antes de
forma relevante (era JSONB opaco de un proveedor externo).

### Manejo de claves

`app.encryption.field-key` sigue el mismo patrón que `app.jwt.secret`: una
clave base64 de 256 bits con default de desarrollo en
`application.properties`, overrideable por `FIELD_ENCRYPTION_KEY`. Se
valida el largo al arrancar (falla rápido si alguien pone una clave débil).

`spring-cloud-aws-starter-secrets-manager` ya está en el classpath (se
agregó con el resto de las dependencias de AWS) pero nada lo usa todavía —
mover esta clave a Secrets Manager en vez de una property es el paso
natural siguiente, no hecho en este pase porque no hay credenciales/infra
de AWS reales en este entorno para probarlo contra algo real.

### Lo que este pase no resuelve

- **Backfill de datos existentes**: la migración de schema (ensanchar
  `cuit`/`respuesta_raw` a `text`, sacar el CHECK) no re-cifra filas que ya
  existían en texto plano — eso requiere un job de backfill aparte
  (leer con el converter viejo, re-guardar con el nuevo) que no se escribe
  acá. En este entorno de desarrollo, con datos de prueba descartables, la
  alternativa más simple es vaciar y recargar vía `DevDataSeeder` después
  de aplicar la migración.
- **Credenciales de terceros**: el spec original las menciona, pero no
  existe todavía ninguna tabla ni columna en el schema para guardar
  credenciales de Nosis/AFIP/BNA (hoy no hay ninguna credencial real
  configurada, ver `feature-specs/2-implementar-sagas.md`). Cuando exista
  ese storage, cifra con el mismo `FieldEncryptor` — no hace falta un
  mecanismo nuevo.
- **Rotación de clave**: cambiar `app.encryption.field-key` hoy deja
  ilegibles todas las filas cifradas con la clave anterior. Un esquema de
  rotación (versionar la clave por fila, re-encriptar en el próximo write)
  es trabajo futuro si hace falta.
