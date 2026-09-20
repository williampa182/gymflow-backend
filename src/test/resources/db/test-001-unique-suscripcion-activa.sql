-- test-001-unique-suscripcion-activa.sql (SOLO TESTS)
--
-- Copia del índice canónico scripts/migrations/001_unique_suscripcion_activa.sql
-- para que @Sql lo cargue desde el classpath en tests. Si el canónico cambia,
-- actualizar acá también (esa es la deuda explícita de duplicarlo; la
-- alternativa —leer por ruta relativa— depende del working dir y falla en CI).
--
-- Por qué existe: Hibernate (ddl-auto=update) NO crea índices únicos
-- parciales, y el CI levanta Postgres fresco sin correr scripts/migrations.
-- Sin este paso, los tests corren sin la constraint y un 201 en CI puede ser
-- 409/500 en prod. Ver collab D1 v2 §5.
CREATE UNIQUE INDEX IF NOT EXISTS uq_suscripcion_activa_por_usuario
    ON suscripciones (usuario_id)
    WHERE estado = 'ACTIVA';
