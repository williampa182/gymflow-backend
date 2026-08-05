-- 006_kiosco_config.sql
--
-- Fase 5, P4: configuración del kiosco de recepción.
--
-- Fila única (id = 1): una puerta, una clave de dispositivo. api_key_hash es
-- el hash BCrypt de la clave (NUNCA la clave en texto plano). La fila la
-- crea KioscoConfigService (siembra desde KIOSK_API_KEY al boot o rotación
-- desde la UI ADMIN); este script solo garantiza que la tabla exista en
-- entornos con esquema manejado a mano (prod/CI). En dev el esquema lo crea
-- Hibernate (DDL_AUTO=update).
--
-- CÓMO APLICAR: aditivo e idempotente (IF NOT EXISTS); correr contra dev
-- primero (ver docs/BACKUP_RUNBOOK.md).

CREATE TABLE IF NOT EXISTS kiosco_config (
    id          BIGINT PRIMARY KEY,
    api_key_hash VARCHAR(60)
);
