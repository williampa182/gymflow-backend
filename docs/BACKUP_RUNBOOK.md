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

## 2. Producción (Neon)

Workflow de GitHub Actions: `.github/workflows/backup.yml`

- Corre automáticamente todos los días a las 07:00 UTC
- También se puede disparar manualmente desde la pestaña **Actions** → `Backup de PostgreSQL (producción)` → **Run workflow**
- Hace `pg_dump` completo, comprimido, con verificación de integridad (el workflow falla ruidosamente si el dump está corrupto, vacío, o sospechosamente pequeño — nunca falla en silencio)
- Guarda el resultado como **artifact de GitHub Actions**, con 35 días de retención

### Setup aplicado y verificado (2026-08-14)

✅ **COMPLETADO**: tras la caída de Railway (2026-08-14), el secret
`PROD_DATABASE_URL` de GitHub se actualizó al connection string directo de
Neon y el backup fue **verificado con un run manual el mismo día** (run
`31844449997`, artifact `gymflow_prod_20260814_215507.sql.gz`, 2858 bytes,
contenido inspeccionado con `william@gymflow.com` presente). Pasos de
referencia por si hay que re-ejecutar el setup:

1. En Neon: **Dashboard → Connection Details** → copiar el connection
   string **directo** (no el pooled) de la base `neondb`. Debe incluir
   `?sslmode=require` (Neon exige TLS; sin eso `pg_dump` falla con
   "server does not support SSL").
   Formato: `postgresql://neondb_owner:<PASSWORD>@ep-dark-night-ay80lxgq.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require`
   (password: [CENSURADO] — vive solo en el gestor de William).
2. En GitHub: `Settings` → `Secrets and variables` → `Actions` → **Update
   secret** (o New si no existe).
   - Nombre: `PROD_DATABASE_URL`
   - Valor: la connection string del paso 1 (con el password real).
3. Probar con un run manual (**Actions** → `Backup de PostgreSQL
   (producción)` → **Run workflow**) y confirmar que el artifact se genera
   y pasa la verificación.

Sin este secret configurado (o apuntando a un host muerto), el workflow
falla intencionalmente en el primer paso, con un mensaje claro (`Falta el
secret PROD_DATABASE_URL`) en vez de generar un backup vacío sin que nadie
se entere.

⚠️ **Vencimiento del cron (repos públicos)**: GitHub desactiva los
workflows programados tras ~60 días sin actividad en repos públicos. Si el
cron deja de correr, reactivar desde **Settings → Actions** o usar
`workflow_dispatch` manual.

### Restaurar un backup de producción

1. Ir a la pestaña **Actions** del repo → el run del día que te interesa → descargar el artifact (`.zip` que contiene el `.sql.gz`).
2. Descomprimir y aplicar contra la base de datos de destino:
   ```powershell
   # Contra un Postgres local (ej. para investigar un problema sin tocar producción)
   gzip -dc gymflow_prod_20260707_070000.sql.gz | docker exec -i gymflow_postgres psql -U gymflow_user -d gymflow_db

   # Contra Neon/producción directamente (⚠️ sobreescribe producción — confirmar dos veces)
   gzip -dc gymflow_prod_20260707_070000.sql.gz | psql "$env:PROD_DATABASE_URL"
   ```

### Limitaciones conocidas (honestas, para no generar falsa sensación de seguridad)

- **No es Point-in-Time Recovery.** Es un snapshot diario — si algo se rompe a las 3 PM y el último backup es de las 07:00 AM, se pierden esas horas. Neon ofrece branching/time-travel, pero el restore point-in-time con retención larga es de pago. Si algún día se paga Neon (o se migra al Plan A de Oracle con Postgres propio), activar PITR real ahí es la mejora más directa sobre esta estrategia.
- **Retención de 35 días** por el límite gratuito de artifacts de GitHub Actions. Backups más viejos que eso se pierden a menos que se descarguen manualmente antes. Nota: los backups históricos de Railway (previos al 14/08) siguen en los artifacts; los de Neon existen desde el 14/08 (el dump Railway 13/08 también está archivado en `C:\respaldo\gymflow-artifacts\`).
- **Restauración probada en real, no automatizada como test de CI**: drill de restore local (06/08) + **restauración real contra Neon prod el 14/08** (drop schema + dump Railway 13/08, exit 0, verificado por API: 4 planes + admin). Falta solo el test de CI si algún día se quiere un nivel "enterprise" de confianza.
