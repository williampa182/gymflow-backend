-- limpiar_datos_qa_t1t8.sql
--
-- Borra de la BD LOCAL de desarrollo (gymflow_db, docker-compose) los datos
-- creados por el QA visual del gate pre-commit T1-T8 (2026-08-13).
--
-- Objetivo (evidencia: collab/evidencia/qa-visual/2026-08-13-gate-pre-commit-t1t8/reporte.md):
--   - qa-t1t8+1786647733040@gymflow.com        (ENTRENADOR)
--   - qa-t1t8+1786647733040-cliente@gymflow.com (CLIENTE)
--   - rutina "QA T1T8 1786647733040" (con su ejercicio)
--   - asignación de esa rutina a "Smoke Test" (relación creada por el QA)
--   - acompañamientos de entrenador (asignaciones_entrenador, ambos lados)
--
-- Mismo patrón que limpiar_usuarios_prueba.sql: orden hijos -> padre por FKs
-- (todas NO ACTION), transaccional, reejecutable (DELETE por LIKE inexistente
-- afecta 0 filas).
--
-- Ejecución local:
--   docker exec -i gymflow_postgres psql -U gymflow_user -d gymflow_db -f - < scripts/limpiar_datos_qa_t1t8.sql
--   (o: PGPASSWORD=gymflow_pass psql -h localhost -U gymflow_user -d gymflow_db -f scripts/limpiar_datos_qa_t1t8.sql)

BEGIN;

-- Verificación previa: cuántos y cuáles se van a tocar.
SELECT id, email, rol, activo
FROM usuarios
WHERE email LIKE 'qa-t1t8+%';

-- Hijos: asistencias (check-ins) de los usuarios QA.
DELETE FROM asistencias
WHERE usuario_id IN (
    SELECT id FROM usuarios WHERE email LIKE 'qa-t1t8+%'
);

-- Hijos: ejercicios de rutinas creadas por el entrenador QA.
DELETE FROM ejercicios
WHERE rutina_id IN (
    SELECT id FROM rutinas
    WHERE entrenador_id IN (
        SELECT id FROM usuarios WHERE email LIKE 'qa-t1t8+%'
    )
);

-- Hijos: asignaciones de rutina del entrenador QA (incluye la relación
-- creada por el QA con el usuario "Smoke Test", que no se borra).
DELETE FROM asignaciones_rutina
WHERE rutina_id IN (
    SELECT id FROM rutinas
    WHERE entrenador_id IN (
        SELECT id FROM usuarios WHERE email LIKE 'qa-t1t8+%'
    )
)
OR cliente_id IN (
    SELECT id FROM usuarios WHERE email LIKE 'qa-t1t8+%'
);

-- Hijos: rutinas del entrenador QA.
DELETE FROM rutinas
WHERE entrenador_id IN (
    SELECT id FROM usuarios WHERE email LIKE 'qa-t1t8+%'
);

-- Hijos: acompañamientos de entrenador (ambos lados de la relación).
DELETE FROM asignaciones_entrenador
WHERE cliente_id IN (
    SELECT id FROM usuarios WHERE email LIKE 'qa-t1t8+%'
)
OR entrenador_id IN (
    SELECT id FROM usuarios WHERE email LIKE 'qa-t1t8+%'
);

-- Hijos: suscripciones (por si el QA compró plan).
DELETE FROM suscripciones
WHERE usuario_id IN (
    SELECT id FROM usuarios WHERE email LIKE 'qa-t1t8+%'
);

-- Padre: los usuarios QA.
DELETE FROM usuarios
WHERE email LIKE 'qa-t1t8+%';

-- Verificación posterior: debe devolver 0 filas.
SELECT id, email FROM usuarios WHERE email LIKE 'qa-t1t8+%';

COMMIT;