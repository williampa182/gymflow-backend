# Backups de PostgreSQL — GymFlow

Estrategia de backup en dos niveles, ambos gratuitos:

## 1. Desarrollo local

Scripts en `scripts/`, corren contra el contenedor Docker de Postgres.

**Crear backup:**
```powershell
.\scripts\backup-local.ps1
```
Genera `backups\gymflow_backup_<timestamp>.sql.gz` (carpeta gitignoreada — nunca se sube al repo, contiene datos reales). Se conservan automáticamente los últimos 10 backups locales.

**Restaurar backup:**
```powershell
.\scripts\restore-local.ps1 -BackupFile ".\backups\gymflow_backup_20260707_213000.sql.gz"
```
⚠️ Sobreescribe la base de datos local actual. Pide confirmación explícita antes de ejecutar.

**Cuándo usarlo:** antes de una migración riesgosa (ej. la de Spring Boot 4.x), antes de probar un cambio de esquema, o simplemente como snapshot antes de experimentar.

## 2. Producción (Railway)

Workflow de GitHub Actions: `.github/workflows/backup.yml`

- Corre automáticamente todos los días a las 07:00 UTC
- También se puede disparar manualmente desde la pestaña **Actions** → `Backup de PostgreSQL (producción)` → **Run workflow**
- Hace `pg_dump` completo, comprimido, con verificación de integridad (el workflow falla ruidosamente si el dump está corrupto, vacío, o sospechosamente pequeño — nunca falla en silencio)
- Guarda el resultado como **artifact de GitHub Actions**, con 35 días de retención

### Setup requerido (una sola vez, al desplegar a Railway)

1. En Railway, entrar al servicio de Postgres → **Variables** → copiar el valor de `DATABASE_PUBLIC_URL` (la connection string pública, no la interna `.railway.internal`, porque GitHub Actions corre fuera de la red privada de Railway).
2. En GitHub: `Settings` → `Secrets and variables` → `Actions` → **New repository secret**.
   - Nombre: `PROD_DATABASE_URL`
   - Valor: la connection string copiada en el paso 1.
3. Listo — el próximo run programado (o uno manual vía `workflow_dispatch`) ya va a poder conectarse.

Sin este secret configurado, el workflow falla intencionalmente en el primer paso, con un mensaje claro (`Falta el secret PROD_DATABASE_URL`) en vez de generar un backup vacío sin que nadie se entere.

### Restaurar un backup de producción

1. Ir a la pestaña **Actions** del repo → el run del día que te interesa → descargar el artifact (`.zip` que contiene el `.sql.gz`).
2. Descomprimir y aplicar contra la base de datos de destino:
   ```powershell
   # Contra un Postgres local (ej. para investigar un problema sin tocar producción)
   gzip -dc gymflow_prod_20260707_070000.sql.gz | docker exec -i gymflow_postgres psql -U gymflow_user -d gymflow_db

   # Contra Railway directamente (⚠️ sobreescribe producción — confirmar dos veces)
   gzip -dc gymflow_prod_20260707_070000.sql.gz | psql "$env:PROD_DATABASE_URL"
   ```

### Limitaciones conocidas (honestas, para no generar falsa sensación de seguridad)

- **No es Point-in-Time Recovery.** Es un snapshot diario — si algo se rompe a las 3 PM y el último backup es de las 07:00 AM, se pierden esas horas. Railway ofrece PITR real (ventana de ~4 semanas, granularidad de segundos) pero es **exclusivo del plan Pro**. Si en algún momento se paga Railway Pro, activar PITR ahí es la mejora más directa sobre esta estrategia.
- **Retención de 35 días** por el límite gratuito de artifacts de GitHub Actions. Backups más viejos que eso se pierden a menos que se descarguen manualmente antes.
- **No probado con restauración automática end-to-end** (solo se valida integridad del `.gz` y que el contenido tenga forma de dump válido) — la restauración real se probó manualmente en local, no está automatizada como test de CI. Se puede sumar más adelante si se quiere un nivel "enterprise" de confianza.
