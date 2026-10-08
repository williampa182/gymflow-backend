-- 009_pase_diario_tipo_plan.sql
--
-- Pases diarios: nuevo valor PASE_DIARIO en el enum TipoPlan.
--
-- Por qué existe este script aunque "los enums no necesitan migración":
-- Hibernate 6 genera un CHECK sobre columnas de enum
-- (antecedente: suscripciones_estado_check en 008). Si la BD tiene
-- planes_tipo_check con los 4 valores viejos y el código inserta un plan
-- PASE_DIARIO con DDL_AUTO=validate, el INSERT falla aunque el código
-- esté bien. DROP + ADD idempotente; si el check no existe, no pasa nada.
--
-- CÓMO APLICAR: backup previo (BACKUP_RUNBOOK.md); dev primero.

ALTER TABLE planes DROP CONSTRAINT IF EXISTS planes_tipo_check;
ALTER TABLE planes ADD CONSTRAINT planes_tipo_check
    CHECK (tipo IN ('MENSUAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL', 'PASE_DIARIO')) NOT VALID;
ALTER TABLE planes VALIDATE CONSTRAINT planes_tipo_check;
