-- =====================================================================
-- V1__init_schema.sql
-- Esquema inicial MVP: Préstamos + Cheques/Factoring + Cta Cte +
-- Contabilidad + Motor de Decisión (single-tenant)
-- Motor: PostgreSQL 15+
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
-- para gen_random_uuid()

-- ---------------------------------------------------------------------
-- Tipos enumerados
-- ---------------------------------------------------------------------
CREATE TYPE tipo_persona AS ENUM ('FISICA', 'JURIDICA');

CREATE TYPE estado_solicitud AS ENUM (
    'INICIADA', 'EN_EVALUACION', 'APROBADA', 'RECHAZADA', 'REVISION_MANUAL', 'CANCELADA'
    );

CREATE TYPE tipo_solicitud AS ENUM ('PRESTAMO', 'DESCUENTO_CHEQUE');

CREATE TYPE resultado_decision AS ENUM ('APROBADO', 'RECHAZADO', 'DERIVADO_MANUAL');

CREATE TYPE estado_prestamo AS ENUM (
    'ORIGINADO', 'VIGENTE', 'CANCELADO', 'REFINANCIADO', 'EN_MORA'
    );

CREATE TYPE sistema_amortizacion AS ENUM ('FRANCES', 'ALEMAN', 'AMERICANO');

CREATE TYPE estado_cuota AS ENUM ('PENDIENTE', 'PAGADA', 'VENCIDA', 'PARCIAL');

CREATE TYPE estado_cheque AS ENUM (
    'EN_CARTERA', 'ENDOSADO', 'DEPOSITADO', 'ACREDITADO', 'RECHAZADO', 'ANULADO'
    );

CREATE TYPE tipo_movimiento_cc AS ENUM ('DEBITO', 'CREDITO');

CREATE TYPE tipo_cuenta_contable AS ENUM ('ACTIVO', 'PASIVO', 'PATRIMONIO', 'INGRESO', 'EGRESO');

CREATE TYPE proveedor_scoring AS ENUM ('NOSIS', 'AFIP', 'BNA');

-- ---------------------------------------------------------------------
-- Clientes
-- ---------------------------------------------------------------------
CREATE TABLE clientes
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    cuit               VARCHAR(11)  NOT NULL,
    razon_social       VARCHAR(200) NOT NULL,
    tipo_persona       tipo_persona NOT NULL,
    email              VARCHAR(200),
    telefono           VARCHAR(30),
    direccion          VARCHAR(300),
    score_nosis        INTEGER,
    fecha_ultimo_score TIMESTAMPTZ,
    activo             BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_clientes_cuit UNIQUE (cuit),
    CONSTRAINT ck_clientes_cuit_formato CHECK (cuit ~ '^\d{11}$')
);

CREATE INDEX idx_clientes_razon_social ON clientes (razon_social);
-- Si más adelante se necesita búsqueda difusa/parcial sobre razón social,
-- habilitar `CREATE EXTENSION pg_trgm;` y reemplazar por un índice GIN
-- con gin_trgm_ops.

-- ---------------------------------------------------------------------
-- Libradores (de cheques, no son necesariamente clientes)
-- ---------------------------------------------------------------------
CREATE TABLE libradores
(
    id           UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    cuit         VARCHAR(11)  NOT NULL,
    razon_social VARCHAR(200) NOT NULL,
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_libradores_cuit UNIQUE (cuit),
    CONSTRAINT ck_libradores_cuit_formato CHECK (cuit ~ '^\d{11}$')
);

-- ---------------------------------------------------------------------
-- Solicitudes de crédito (entrada al motor de decisión)
-- ---------------------------------------------------------------------
CREATE TABLE solicitudes_credito
(
    id               UUID PRIMARY KEY          DEFAULT gen_random_uuid(),
    cliente_id       UUID             NOT NULL REFERENCES clientes (id),
    tipo             tipo_solicitud   NOT NULL,
    monto_solicitado NUMERIC(16, 2)   NOT NULL CHECK (monto_solicitado > 0),
    estado           estado_solicitud NOT NULL DEFAULT 'INICIADA',
    fecha_solicitud  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    fecha_resolucion TIMESTAMPTZ,
    creado_en        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    actualizado_en   TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_solicitudes_cliente ON solicitudes_credito (cliente_id);
CREATE INDEX idx_solicitudes_estado ON solicitudes_credito (estado);

-- ---------------------------------------------------------------------
-- Resultados de scoring (Nosis / AFIP / BNA)
-- ---------------------------------------------------------------------
CREATE TABLE resultados_scoring
(
    id             UUID PRIMARY KEY           DEFAULT gen_random_uuid(),
    solicitud_id   UUID REFERENCES solicitudes_credito (id),
    cliente_id     UUID              NOT NULL REFERENCES clientes (id),
    proveedor      proveedor_scoring NOT NULL,
    score          INTEGER,
    respuesta_raw  JSONB             NOT NULL DEFAULT '{}'::jsonb,
    fecha_consulta TIMESTAMPTZ       NOT NULL DEFAULT now(),
    CONSTRAINT ck_scoring_score_rango CHECK (score IS NULL OR (score BETWEEN 0 AND 999))
);

CREATE INDEX idx_scoring_solicitud ON resultados_scoring (solicitud_id);
CREATE INDEX idx_scoring_cliente_proveedor_fecha ON resultados_scoring (cliente_id, proveedor, fecha_consulta DESC);
-- Este índice es el que soporta el "cachear por CUIT con TTL corto":
-- consultar el último resultado por cliente+proveedor antes de re-llamar a la API externa.

-- ---------------------------------------------------------------------
-- Motor de decisión: reglas y resultados
-- ---------------------------------------------------------------------
CREATE TABLE reglas_decision
(
    id        UUID PRIMARY KEY            DEFAULT gen_random_uuid(),
    nombre    VARCHAR(150)       NOT NULL,
    condicion JSONB              NOT NULL, -- árbol de condición serializado (evaluado por el motor)
    accion    resultado_decision NOT NULL,
    orden     INTEGER            NOT NULL,
    activo    BOOLEAN            NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ        NOT NULL DEFAULT now(),
    CONSTRAINT uq_reglas_orden UNIQUE (orden)
);

CREATE TABLE resultados_decision
(
    id           UUID PRIMARY KEY            DEFAULT gen_random_uuid(),
    solicitud_id UUID               NOT NULL REFERENCES solicitudes_credito (id),
    regla_id     UUID REFERENCES reglas_decision (id),
    resultado    resultado_decision NOT NULL,
    detalle      JSONB, -- valores evaluados, útil para trazabilidad/compliance
    fecha        TIMESTAMPTZ        NOT NULL DEFAULT now()
);

CREATE INDEX idx_resultados_decision_solicitud ON resultados_decision (solicitud_id);

-- ---------------------------------------------------------------------
-- Préstamos y cuotas
-- ---------------------------------------------------------------------
CREATE TABLE prestamos
(
    id                   UUID PRIMARY KEY              DEFAULT gen_random_uuid(),
    cliente_id           UUID                 NOT NULL REFERENCES clientes (id),
    solicitud_id         UUID REFERENCES solicitudes_credito (id),
    monto                NUMERIC(16, 2)       NOT NULL CHECK (monto > 0),
    tasa_anual           NUMERIC(7, 4)        NOT NULL CHECK (tasa_anual >= 0),
    sistema_amortizacion sistema_amortizacion NOT NULL,
    plazo_meses          INTEGER              NOT NULL CHECK (plazo_meses > 0),
    estado               estado_prestamo      NOT NULL DEFAULT 'ORIGINADO',
    fecha_originacion    DATE                 NOT NULL DEFAULT CURRENT_DATE,
    creado_en            TIMESTAMPTZ          NOT NULL DEFAULT now(),
    actualizado_en       TIMESTAMPTZ          NOT NULL DEFAULT now(),
    version              BIGINT               NOT NULL DEFAULT 0 -- optimistic locking
);

CREATE INDEX idx_prestamos_cliente ON prestamos (cliente_id);
CREATE INDEX idx_prestamos_estado ON prestamos (estado);

CREATE TABLE cuotas_prestamo
(
    id                UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    prestamo_id       UUID           NOT NULL REFERENCES prestamos (id) ON DELETE CASCADE,
    numero            INTEGER        NOT NULL CHECK (numero > 0),
    monto             NUMERIC(16, 2) NOT NULL CHECK (monto > 0),
    monto_pagado      NUMERIC(16, 2) NOT NULL DEFAULT 0 CHECK (monto_pagado >= 0),
    fecha_vencimiento DATE           NOT NULL,
    fecha_pago        DATE,
    estado            estado_cuota   NOT NULL DEFAULT 'PENDIENTE',
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_cuota_prestamo_numero UNIQUE (prestamo_id, numero)
);

CREATE INDEX idx_cuotas_prestamo ON cuotas_prestamo (prestamo_id);
CREATE INDEX idx_cuotas_vencimiento_estado ON cuotas_prestamo (fecha_vencimiento, estado);
-- Soporta el job de mora / motor de cobranza (barrido diario de vencidas).

-- ---------------------------------------------------------------------
-- Cheques
-- ---------------------------------------------------------------------
CREATE TABLE cheques
(
    id                UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    cliente_id        UUID           NOT NULL REFERENCES clientes (id),
    librador_id       UUID           NOT NULL REFERENCES libradores (id),
    numero            VARCHAR(30)    NOT NULL,
    banco             VARCHAR(100)   NOT NULL,
    monto             NUMERIC(16, 2) NOT NULL CHECK (monto > 0),
    fecha_emision     DATE           NOT NULL,
    fecha_vencimiento DATE           NOT NULL,
    estado            estado_cheque  NOT NULL DEFAULT 'EN_CARTERA',
    creado_en         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    actualizado_en    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_cheque_banco_numero UNIQUE (banco, numero, librador_id),
    CONSTRAINT ck_cheque_fechas CHECK (fecha_vencimiento >= fecha_emision)
);

CREATE INDEX idx_cheques_cliente ON cheques (cliente_id);
CREATE INDEX idx_cheques_librador ON cheques (librador_id);
CREATE INDEX idx_cheques_estado_vencimiento ON cheques (estado, fecha_vencimiento);

CREATE TABLE endosos_cheque
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    cheque_id     UUID        NOT NULL REFERENCES cheques (id) ON DELETE CASCADE,
    de_cliente_id UUID        NOT NULL REFERENCES clientes (id),
    a_cliente_id  UUID        NOT NULL REFERENCES clientes (id),
    fecha         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_endoso_distinto_cliente CHECK (de_cliente_id <> a_cliente_id)
);

CREATE INDEX idx_endosos_cheque ON endosos_cheque (cheque_id);

-- ---------------------------------------------------------------------
-- Cuenta corriente (única fuente de verdad de saldo por cliente)
-- ---------------------------------------------------------------------
CREATE TABLE cuentas_corrientes
(
    id             UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    cliente_id     UUID           NOT NULL REFERENCES clientes (id),
    moneda         VARCHAR(3)     NOT NULL DEFAULT 'ARS',
    saldo          NUMERIC(16, 2) NOT NULL DEFAULT 0,
    version        BIGINT         NOT NULL DEFAULT 0, -- optimistic locking, crítico acá
    creado_en      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_cta_cte_cliente_moneda UNIQUE (cliente_id, moneda)
);

CREATE TABLE movimientos_cta_cte
(
    id                  UUID PRIMARY KEY            DEFAULT gen_random_uuid(),
    cuenta_corriente_id UUID               NOT NULL REFERENCES cuentas_corrientes (id),
    tipo                tipo_movimiento_cc NOT NULL,
    monto               NUMERIC(16, 2)     NOT NULL CHECK (monto > 0),
    saldo_posterior     NUMERIC(16, 2)     NOT NULL, -- snapshot: saldo luego de aplicar el movimiento
    referencia_tipo     VARCHAR(50)        NOT NULL, -- ej. 'PRESTAMO', 'CHEQUE', 'PAGO_CUOTA'
    referencia_id       UUID               NOT NULL,
    idempotency_key     VARCHAR(100)       NOT NULL,
    fecha               TIMESTAMPTZ        NOT NULL DEFAULT now(),
    CONSTRAINT uq_movimiento_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_movimientos_cta_cte ON movimientos_cta_cte (cuenta_corriente_id, fecha DESC);
CREATE INDEX idx_movimientos_referencia ON movimientos_cta_cte (referencia_tipo, referencia_id);

-- ---------------------------------------------------------------------
-- Contabilidad (plan de cuentas + asientos de doble entrada)
-- ---------------------------------------------------------------------
CREATE TABLE plan_cuentas
(
    id     UUID PRIMARY KEY              DEFAULT gen_random_uuid(),
    codigo VARCHAR(20)          NOT NULL,
    nombre VARCHAR(150)         NOT NULL,
    tipo   tipo_cuenta_contable NOT NULL,
    activa BOOLEAN              NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_plan_cuentas_codigo UNIQUE (codigo)
);

CREATE TABLE asientos_contables
(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    fecha           DATE         NOT NULL DEFAULT CURRENT_DATE,
    descripcion     VARCHAR(300) NOT NULL,
    origen_tipo     VARCHAR(50)  NOT NULL, -- ej. 'PRESTAMO', 'CHEQUE', 'PAGO_CUOTA'
    origen_id       UUID         NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_asiento_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_asientos_origen ON asientos_contables (origen_tipo, origen_id);
CREATE INDEX idx_asientos_fecha ON asientos_contables (fecha);

CREATE TABLE movimientos_contables
(
    id                 UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    asiento_id         UUID           NOT NULL REFERENCES asientos_contables (id) ON DELETE CASCADE,
    cuenta_contable_id UUID           NOT NULL REFERENCES plan_cuentas (id),
    debe               NUMERIC(16, 2) NOT NULL DEFAULT 0 CHECK (debe >= 0),
    haber              NUMERIC(16, 2) NOT NULL DEFAULT 0 CHECK (haber >= 0),
    CONSTRAINT ck_movimiento_contable_exclusivo CHECK (
        (debe > 0 AND haber = 0) OR (haber > 0 AND debe = 0)
        )
);

CREATE INDEX idx_mov_contables_asiento ON movimientos_contables (asiento_id);
CREATE INDEX idx_mov_contables_cuenta ON movimientos_contables (cuenta_contable_id);

-- ---------------------------------------------------------------------
-- Soporte técnico: outbox, idempotencia e integraciones
-- (no son "negocio" pero se crean desde el V1 siguiendo el patrón
-- descripto en el documento de arquitectura)
-- ---------------------------------------------------------------------
CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    aggregate_tipo VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    tipo_evento    VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    publicado      BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publicado_en   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pendientes ON outbox_events (creado_en) WHERE publicado = FALSE;

CREATE TABLE idempotency_keys
(
    id        UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    key       VARCHAR(100) NOT NULL,
    operacion VARCHAR(100) NOT NULL,
    resultado JSONB,
    creado_en TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_key_operacion UNIQUE (key, operacion)
);

CREATE TABLE integraciones_log
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    servicio    VARCHAR(30)  NOT NULL, -- 'NOSIS' | 'AFIP' | 'BNA'
    endpoint    VARCHAR(200) NOT NULL,
    request     JSONB,
    response    JSONB,
    estado_http INTEGER,
    exitoso     BOOLEAN      NOT NULL,
    duracion_ms INTEGER,
    fecha       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_integraciones_servicio_fecha ON integraciones_log (servicio, fecha DESC);

-- ---------------------------------------------------------------------
-- Seed mínimo: plan de cuentas base
-- ---------------------------------------------------------------------
INSERT INTO plan_cuentas (codigo, nombre, tipo)
VALUES ('1.1.01', 'Caja', 'ACTIVO'),
       ('1.1.02', 'Banco', 'ACTIVO'),
       ('1.1.03', 'Préstamos otorgados', 'ACTIVO'),
       ('1.1.04', 'Cheques en cartera', 'ACTIVO'),
       ('1.1.05', 'Cuentas a cobrar clientes', 'ACTIVO'),
       ('2.1.01', 'Cuentas a pagar', 'PASIVO'),
       ('4.1.01', 'Intereses ganados', 'INGRESO'),
       ('5.1.01', 'Gastos operativos', 'EGRESO');