-- =====================================================================
-- V4__auditoria.sql
-- Auditoría append-only de acciones sensibles (ver feature-specs/4-auditoria.md).
-- usuario_email es un snapshot, no hay FK a usuarios a propósito: la fila
-- no debe cambiar de significado si el usuario cambia de email después.
-- =====================================================================

CREATE TABLE auditoria_acciones
(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    usuario_id      UUID         NOT NULL,
    usuario_email   VARCHAR(200) NOT NULL,
    accion          VARCHAR(60)  NOT NULL,
    entidad_tipo    VARCHAR(50)  NOT NULL,
    entidad_id      UUID         NOT NULL,
    estado_anterior VARCHAR(50),
    estado_nuevo    VARCHAR(50)  NOT NULL,
    detalle         JSONB,
    ip_origen       VARCHAR(45),
    fecha           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_auditoria_entidad ON auditoria_acciones (entidad_tipo, entidad_id);
CREATE INDEX idx_auditoria_usuario ON auditoria_acciones (usuario_id, fecha DESC);

-- Append-only real: ni un bug de la aplicación ni un UPDATE/DELETE manual
-- por psql pueden alterar una fila ya escrita.
CREATE OR REPLACE FUNCTION rechazar_modificacion_auditoria() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'auditoria_acciones es append-only: % no está permitido (fila id=%)',
        TG_OP, COALESCE(OLD.id, NULL);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auditoria_acciones_inmutable
    BEFORE UPDATE OR DELETE
    ON auditoria_acciones
    FOR EACH ROW
EXECUTE FUNCTION rechazar_modificacion_auditoria();
