-- =====================================================================
-- V5__tokens_revocados.sql
-- Denylist de JWT revocados (ver feature-specs/5-revocacion-tokens.md).
-- jti es la clave primaria (id único que ya trae cada JWT emitido por
-- JwtIssuer); expira_en es la expiración original del token, para poder
-- purgar filas cuyo token ya venció por su cuenta igual.
-- =====================================================================

CREATE TABLE tokens_revocados
(
    jti         UUID PRIMARY KEY,
    usuario_id  UUID        NOT NULL,
    expira_en   TIMESTAMPTZ NOT NULL,
    revocado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tokens_revocados_expira ON tokens_revocados (expira_en);
