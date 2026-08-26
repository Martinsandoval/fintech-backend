# Motor de amortización

## Objetivo

`Prestamo` ya tiene `sistemaAmortizacion` (`FRANCES`/`ALEMAN`/`AMERICANO`),
pero ese campo hoy es decorativo: no hay código que lo lea para calcular
nada. El único camino para crear cuotas es `POST /api/cuotas`, una por
una, a mano — así es como las carga `DevDataSeeder`. Para una plataforma
de préstamos, calcular el cronograma de pagos a partir de
monto/tasa/plazo/sistema es el cálculo de dominio más central que hay, y
no existe.

## 1. Las tres fórmulas

Con `n` = `plazoMeses`, `i` = tasa mensual = `tasaAnual / 12 / 100`,
`monto` = capital original:

- **FRANCES** (cuota fija): todas las cuotas tienen el mismo monto total;
  lo que cambia mes a mes es cuánto de esa cuota es interés vs. capital
  (al principio es casi todo interés, al final casi todo capital).
  ```
  cuota = monto * (i * (1+i)^n) / ((1+i)^n - 1)
  ```
- **ALEMAN** (capital fijo): el capital amortizado es el mismo todos los
  meses (`monto / n`); el interés se calcula sobre el saldo pendiente, así
  que la cuota total *decrece* mes a mes.
  ```
  capital_cuota = monto / n
  interes_cuota_k = saldo_pendiente_k * i
  cuota_k = capital_cuota + interes_cuota_k
  ```
- **AMERICANO** (bullet): todas las cuotas excepto la última son sólo
  interés; el capital completo se devuelve entero en la última cuota
  junto con su interés.
  ```
  cuota_k (k < n) = monto * i
  cuota_n          = monto * i + monto
  ```

## 2. El problema de redondeo — y por qué importa acá específicamente

`monto`/`tasaAnual`/`monto` de cada cuota son `NUMERIC(16,2)` — dos
decimales. Calcular cada cuota con la fórmula y redondear
independientemente puede dejar el total de capital amortizado a lo largo
del cronograma unos centavos por encima o por debajo de `monto`. Eso no es
un detalle cosmético: `CuotaPrestamoService.registrarPago` ya asume que la
suma de lo cobrado tiene que cuadrar exactamente contra el préstamo, y
`AsientoContableService.crearConLineas` **rechaza cualquier asiento donde
debe ≠ haber** (`feature-specs/1-consistencia-datos.md`). Un cronograma
que no cierra a la última cuota produce, tarde o temprano, un intento de
asiento desbalanceado real.

Solución estándar: generar las primeras `n-1` cuotas con la fórmula y
redondeo normal (`HALF_UP`, 2 decimales), y calcular la **última cuota
como el remanente exacto** (capital pendiente + interés del último
período) en vez de por fórmula — así absorbe cualquier diferencia de
redondeo acumulada y el cronograma cierra siempre.

## 3. Dónde vive esto

`AmortizacionService` (paquete `prestamo`), un método:

```java
List<CuotaPrestamo> generar(Prestamo prestamo)
```

que devuelve las cuotas ya armadas (sin persistir) con `numero`, `monto`,
`fechaVencimiento` (`fechaOriginacion.plusMonths(numero)`). Se llama desde
`PrestamoService.create`, **en la misma transacción** que ya banca el
asiento de originación — si algo falla armando el cronograma, no debería
quedar un préstamo originado sin sus cuotas, mismo principio de
transaccionalidad única de `feature-specs/1-consistencia-datos.md`.

`POST /api/cuotas` (creación manual) **no se elimina** — sigue
disponible para correcciones puntuales o casos donde el cronograma
automático no aplica.

## 4. Lo que este pase deja afuera (a propósito)

- **Split capital/interés por cuota.** `CuotaPrestamo` sólo tiene `monto`
  — no hay columnas para guardar cuánto de cada cuota es capital y cuánto
  interés. El motor necesita calcular esa distinción *internamente* para
  que las fórmulas (sobre todo ALEMAN) den bien, pero no la persiste.
  Como consecuencia, `plan_cuentas` ya tiene sembrada `4.1.01 Intereses
  ganados` desde `V1__init_schema.sql` — y nunca se usó: hoy
  `CuotaPrestamoService.registrarPago` acredita el cobro entero a
  `1.1.05 Cuentas a cobrar clientes`, sin separar interés ganado de
  recupero de capital. Corregir eso (agregar
  `capital`/`interes` a `CuotaPrestamo` y partir el asiento de cobro en
  dos líneas contra las cuentas correctas) es un cambio real y
  relacionado, pero es contable, no de cálculo de cronograma — mejor un
  pase aparte que toque `CuotaPrestamoService`/`ScoringResultProcessor`-style
  en vez de mezclarlo con esto.
- **Precancelación / refinanciación.** Recalcular un cronograma existente
  cuando el cliente paga antes de tiempo, o cuando un préstamo se
  refinancia (`EstadoPrestamo.REFINANCIADO` ya existe como valor de enum,
  sin lógica atrás) no está cubierto.
- **Tasa variable.** `tasaAnual` es fija para todo el préstamo; no hay
  concepto de tasa que cambie a mitad de camino.
