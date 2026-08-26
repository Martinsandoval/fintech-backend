-- =====================================================================
-- V2__cifrado_campos_sensibles.sql
-- Cifrado a nivel de campo (ver feature-specs/3-cifrado.md sección 2).
--
-- IMPORTANTE: esta migración ensancha las columnas y saca las constraints
-- que asumían texto plano, pero NO re-cifra filas existentes — el valor
-- que ya estaba en la fila queda como texto plano en la columna
-- ensanchada hasta que la aplicación la vuelva a escribir. En este
-- entorno de desarrollo, con datos de prueba descartables, la fila vieja
-- se vacía y se recarga en vez de escribir un backfill.
-- =====================================================================

ALTER TABLE clientes
    ALTER COLUMN cuit TYPE text;
ALTER TABLE clientes
    DROP CONSTRAINT ck_clientes_cuit_formato;

ALTER TABLE libradores
    ALTER COLUMN cuit TYPE text;
ALTER TABLE libradores
    DROP CONSTRAINT ck_libradores_cuit_formato;

-- jsonb -> text: el valor cifrado ya no es JSON válido, es base64 opaco.
ALTER TABLE resultados_scoring
    ALTER COLUMN respuesta_raw TYPE text USING respuesta_raw::text;
ALTER TABLE resultados_scoring
    ALTER COLUMN respuesta_raw DROP DEFAULT;
