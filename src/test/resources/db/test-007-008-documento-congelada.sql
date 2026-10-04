-- test-007-008-documento-congelada.sql (SOLO TESTS)
--
-- Espejo de scripts/migrations/007_documento_usuario.sql y
-- 008_congelamiento_suscripciones.sql para que @Sql lo cargue desde el
-- classpath en tests. Si los canónicos cambian, actualizar acá también.
--
-- Por qué existe: Hibernate (ddl-auto=update) crea las columnas pero NO
-- índices únicos parciales, y el CI levanta Postgres fresco sin correr
-- scripts/migrations. Sin esto, los stress tests de importación y
-- congelamiento corren sin constraints y una carrera que en prod da 409
-- en CI crearía duplicados.
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS tipo_documento VARCHAR(10);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS numero_documento VARCHAR(30);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS telefono VARCHAR(30);
ALTER TABLE usuarios ALTER COLUMN email DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_usuario_documento
    ON usuarios (tipo_documento, numero_documento)
    WHERE numero_documento IS NOT NULL;

ALTER TABLE suscripciones ADD COLUMN IF NOT EXISTS congelada_desde DATE;

ALTER TABLE suscripciones DROP CONSTRAINT IF EXISTS suscripciones_estado_check;
ALTER TABLE suscripciones ADD CONSTRAINT suscripciones_estado_check
    CHECK (estado IN ('ACTIVA', 'VENCIDA', 'CANCELADA', 'CONGELADA')) NOT VALID;
ALTER TABLE suscripciones VALIDATE CONSTRAINT suscripciones_estado_check;
