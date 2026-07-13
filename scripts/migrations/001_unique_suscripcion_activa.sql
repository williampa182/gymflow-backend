-- 001_unique_suscripcion_activa.sql
--
-- Por qué: SuscripcionService.crear() hace un chequeo "¿el usuario ya tiene
-- una suscripción activa?" en código Java, seguido de un INSERT separado.
-- Bajo concurrencia real, dos requests pueden pasar el chequeo antes de que
-- cualquiera de las dos haga commit (clásico check-then-act / TOCTOU),
-- resultando en dos (o más) suscripciones ACTIVA para el mismo usuario.
--
-- El @Version agregado a la entidad Suscripcion NO resuelve esto — @Version
-- protege contra updates concurrentes sobre la MISMA fila (ej. dos
-- cancelaciones de la misma suscripción), no contra la creación de dos
-- filas distintas para el mismo usuario_id.
--
-- La única forma correcta de cerrar esta carrera es una constraint a nivel
-- de base de datos. Como la regla de negocio es "como mucho UNA suscripción
-- ACTIVA por usuario, pero puede tener muchas CANCELADA/VENCIDA a lo largo
-- del tiempo", no sirve una unique constraint simple en usuario_id (eso
-- bloquearía tener historial). Se necesita un ÍNDICE ÚNICO PARCIAL,
-- soportado por Postgres pero no expresable de forma portable con
-- anotaciones JPA estándar — por eso este script es manual.
--
-- CÓMO APLICAR (no se corre solo, hacerlo a mano y con cuidado):
--   1. Confirmar el nombre real de la tabla/columnas si difieren de lo
--      asumido acá (tabla "suscripciones", columnas "usuario_id", "estado").
--   2. Backup antes de correr esto en cualquier entorno con datos reales
--      (ver docs/BACKUP_RUNBOOK.md).
--   3. Si YA existen datos con más de una suscripción ACTIVA para el mismo
--      usuario (posible si esta carrera ya se explotó antes de este fix),
--      este CREATE INDEX va a fallar. Hay que resolver esos duplicados a
--      mano primero (decidir cuál de las suscripciones activas queda como
--      la "real" y cancelar las demás) antes de poder aplicar el índice.
--   4. Correr contra dev primero, confirmar que SuscripcionService.crear()
--      sigue funcionando normal y que el catch de DataIntegrityViolationException
--      agregado en el servicio efectivamente devuelve un 409 limpio al
--      intentar crear una segunda suscripción activa en paralelo.

CREATE UNIQUE INDEX IF NOT EXISTS uq_suscripcion_activa_por_usuario
    ON suscripciones (usuario_id)
    WHERE estado = 'ACTIVA';

-- Verificación rápida post-aplicación (debería devolver 0 filas):
-- SELECT usuario_id, COUNT(*)
-- FROM suscripciones
-- WHERE estado = 'ACTIVA'
-- GROUP BY usuario_id
-- HAVING COUNT(*) > 1;
