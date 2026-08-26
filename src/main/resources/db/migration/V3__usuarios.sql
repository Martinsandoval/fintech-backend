-- =====================================================================
-- V3__usuarios.sql
-- Usuarios internos de la app (analistas/admins que operan el backend),
-- no confundir con `clientes` (los sujetos de crédito).
-- =====================================================================

CREATE TYPE rol_usuario AS ENUM ('ADMIN', 'ANALISTA');

CREATE TABLE usuarios
(
    id             UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    email          VARCHAR(200) NOT NULL,
    nombre         VARCHAR(100) NOT NULL,
    apellido       VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    rol            rol_usuario  NOT NULL DEFAULT 'ANALISTA',
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_usuarios_email UNIQUE (email)
);
