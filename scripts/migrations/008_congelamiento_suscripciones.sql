-- 008_congelamiento_suscripciones.sql
--
-- Recepción: congelar/descongelar membresías (el dueño congela seguido).
--
-- congelada_desde (DATE, NULLABLE): día en que se congeló; se limpia al
-- descongelar. Al descongelar, fecha_fin se extiende por los días
-- congelados (hoy - congelada_desde). Sin tracking de historial de
-- congelamientos (fuera de alcance del piloto).
--
-- El índice único parcial 001 (solo estado ACTIVA) no se toca: una fila
-- CONGELADA no viola "una ACTIVA por usuario".
--
-- CÓMO APLICAR: aditivo e idempotente; backup previo (BACKUP_RUNBOOK.md);
-- dev primero. En local el esquema lo crea Hibernate (DDL_AUTO=update).

ALTER TABLE suscripciones ADD COLUMN IF NOT EXISTS congelada_desde DATE;

-- Hibernate 6 genera un CHECK sobre el enum estado (suscripciones_estado_check)
-- con los valores conocidos al crear el esquema. Al agregar CONGELADA hay que
-- evolucionarlo o todo UPDATE a CONGELADA falla en BD aunque el código esté
-- bien (detectado por el stress de datos, constraint suscripciones_estado_check).
-- DROP + ADD con IF NOT EXISTS es idempotente; si Hibernate ya hubiera creado
-- el check con 4 valores (esquema fresco post-cambio), el ADD se salta.
ALTER TABLE suscripciones DROP CONSTRAINT IF EXISTS suscripciones_estado_check;
ALTER TABLE suscripciones ADD CONSTRAINT suscripciones_estado_check
    CHECK (estado IN ('ACTIVA', 'VENCIDA', 'CANCELADA', 'CONGELADA')) NOT VALID;
ALTER TABLE suscripciones VALIDATE CONSTRAINT suscripciones_estado_check;
