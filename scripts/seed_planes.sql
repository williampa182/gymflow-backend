-- seed_planes.sql
--
-- Seed de catálogo inicial de planes para producción (Railway).
-- Prod quedó con la tabla planes vacía (verificado en smoke test 2026-08-04);
-- la app solo expone planes creados por ADMIN, así que el catálogo base se
-- carga con INSERT directo. Idempotente: ON CONFLICT (nombre) DO NOTHING —
-- reejecutable sin riesgo.
--
-- Uso (mismo patrón que las migraciones 001-006):
--   psql "$DATABASE_PUBLIC_URL" -f scripts/seed_planes.sql
--
-- Nombres alineados con los fixtures de los tests frontend
-- (planes-role.test.tsx: "Plan Mensual Básico", "Plan Anual Premium").

INSERT INTO planes
    (nombre, descripcion, precio, duracion_dias, tipo, limite_clases,
     incluye_clases, incluye_entrenador_personal, activo, creado_en)
VALUES
    ('Plan Mensual Básico', 'Acceso general al gimnasio',
     25000, 30, 'MENSUAL', NULL, false, false, true, NOW()),
    ('Plan Full Access', 'Acceso general + clases dirigidas',
     45000, 30, 'MENSUAL', 20, true, false, true, NOW()),
    ('Plan Trimestral', '3 meses con clases dirigidas sin límite',
     120000, 90, 'TRIMESTRAL', NULL, true, false, true, NOW()),
    ('Plan Anual Premium', 'Todo incluido con entrenador personal',
     280000, 365, 'ANUAL', NULL, true, true, true, NOW())
ON CONFLICT (nombre) DO NOTHING;
