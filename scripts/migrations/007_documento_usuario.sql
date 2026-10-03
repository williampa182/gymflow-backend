-- 007_documento_usuario.sql
--
-- Recepción: identificación del socio (tipo + número de documento) y teléfono.
--
-- Columnas NULLABLES: los usuarios existentes quedan con NULL sin romper
-- nada; el documento se completa en edición o en el próximo check-in.
-- La unicidad de (tipo, número) es un índice parcial: solo aplica cuando
-- hay documento (múltiples NULLs permitidos).
--
-- CÓMO APLICAR:
--   1. Backup antes de correrlo en entornos con datos reales
--      (ver docs/BACKUP_RUNBOOK.md).
--   2. Aditivo e idempotente (IF NOT EXISTS): se puede correr con el
--      servidor abajo o arriba sin destruir datos.
--   3. Correr contra dev primero y verificar (SELECT de las columnas).
-- En desarrollo local el esquema lo crea Hibernate (DDL_AUTO=update); este
-- script es para entornos donde se maneja el esquema a mano (prod/CI).

ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS tipo_documento VARCHAR(10);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS numero_documento VARCHAR(30);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS telefono VARCHAR(30);

-- Recepción: el email pasa a opcional (socios que no entran a la web).
-- Postgres permite múltiples NULLs en columna UNIQUE, así que varios
-- socios sin email no chocan. El login no se toca: findByEmail nunca
-- matchea una fila NULL.
ALTER TABLE usuarios ALTER COLUMN email DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_usuario_documento
    ON usuarios (tipo_documento, numero_documento)
    WHERE numero_documento IS NOT NULL;
