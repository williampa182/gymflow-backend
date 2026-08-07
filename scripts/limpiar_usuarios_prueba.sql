-- limpiar_usuarios_prueba.sql
--
-- Borra de PRODUCCIÓN (Railway) los usuarios de prueba creados por QA y
-- smoke tests. Pendiente T2 del plan aprobado 2026-08-05.
--
-- PREREQUISITOS (obligatorios, ver plan T2):
--   1. Backup fresco de prod + drill de restore probado (BACKUP_RUNBOOK.md).
--   2. Ejecución manual por William con URL de un solo uso, mismo patrón
--      que las migraciones 001-006 / seed_planes.sql:
--        psql "$DATABASE_PUBLIC_URL" -f scripts/limpiar_usuarios_prueba.sql
--
-- Usuarios objetivo:
--   - smoketest.gymflow.20260804@example.com (smoke test 04/08; carnet NHPTUJC)
--   - qa.05082026@test.local             (QA visual 05/08; rol CLIENTE)
--   - triage.checkin.20260806@test.local (triaje H2 06/08; rol CLIENTE)
--   - journey.t5.20260806@test.local     (verificación T5 journeys 3+4, 06/08; id=9, rol CLIENTE)
--
-- Orden de borrado por FKs (todas NO ACTION, sin cascada): hijos → padre.
-- Transaccional: si algo falla, ROLLBACK completo. Reejecutable sin riesgo
-- (los DELETE sobre emails inexistentes afectan 0 filas).
--
-- NOTA: NO borrar aún el usuario fixture del pentest (T6) si ya se creó —
-- ese se borra DESPUÉS de T6, no con este script.

BEGIN;

-- Verificación previa: cuántos y cuáles se van a tocar (debe listar 4).
SELECT id, email, rol, codigo_carnet, activo
FROM usuarios
WHERE email IN (
    'smoketest.gymflow.20260804@example.com',
    'qa.05082026@test.local',
    'triage.checkin.20260806@test.local',
    'journey.t5.20260806@test.local'
);

-- Hijos: asistencias (check-ins) de esos usuarios.
DELETE FROM asistencias
WHERE usuario_id IN (
    SELECT id FROM usuarios
    WHERE email IN (
        'smoketest.gymflow.20260804@example.com',
        'qa.05082026@test.local',
        'triage.checkin.20260806@test.local',
        'journey.t5.20260806@test.local'
    )
);

-- Hijos: asignaciones de rutinas (cliente_id) y rutinas creadas por ellos
-- (entrenador_id, por si acaso) con sus ejercicios.
DELETE FROM ejercicios
WHERE rutina_id IN (
    SELECT id FROM rutinas
    WHERE entrenador_id IN (
        SELECT id FROM usuarios
        WHERE email IN (
            'smoketest.gymflow.20260804@example.com',
            'qa.05082026@test.local',
            'triage.checkin.20260806@test.local',
            'journey.t5.20260806@test.local'
        )
    )
);

DELETE FROM asignaciones_rutina
WHERE cliente_id IN (
    SELECT id FROM usuarios
    WHERE email IN (
        'smoketest.gymflow.20260804@example.com',
        'qa.05082026@test.local',
        'triage.checkin.20260806@test.local',
        'journey.t5.20260806@test.local'
    )
);

DELETE FROM rutinas
WHERE entrenador_id IN (
    SELECT id FROM usuarios
    WHERE email IN (
        'smoketest.gymflow.20260804@example.com',
        'qa.05082026@test.local',
        'triage.checkin.20260806@test.local',
        'journey.t5.20260806@test.local'
    )
);

-- Hijos: acompañamientos de entrenador (ambos lados de la relación).
DELETE FROM asignaciones_entrenador
WHERE cliente_id IN (
    SELECT id FROM usuarios
    WHERE email IN (
        'smoketest.gymflow.20260804@example.com',
        'qa.05082026@test.local',
        'triage.checkin.20260806@test.local',
        'journey.t5.20260806@test.local'
    )
)
OR entrenador_id IN (
    SELECT id FROM usuarios
    WHERE email IN (
        'smoketest.gymflow.20260804@example.com',
        'qa.05082026@test.local',
        'triage.checkin.20260806@test.local',
        'journey.t5.20260806@test.local'
    )
);

-- Hijos: suscripciones.
DELETE FROM suscripciones
WHERE usuario_id IN (
    SELECT id FROM usuarios
    WHERE email IN (
        'smoketest.gymflow.20260804@example.com',
        'qa.05082026@test.local',
        'triage.checkin.20260806@test.local',
        'journey.t5.20260806@test.local'
    )
);

-- Padre: los usuarios.
DELETE FROM usuarios
WHERE email IN (
    'smoketest.gymflow.20260804@example.com',
    'qa.05082026@test.local',
    'triage.checkin.20260806@test.local',
    'journey.t5.20260806@test.local'
);

-- Verificación posterior: debe devolver 0 filas.
SELECT id, email FROM usuarios
WHERE email IN (
    'smoketest.gymflow.20260804@example.com',
    'qa.05082026@test.local',
    'triage.checkin.20260806@test.local',
    'journey.t5.20260806@test.local'
);

COMMIT;
