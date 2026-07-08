<#
.SYNOPSIS
    Backup local de la base de datos GymFlow (desarrollo).

.DESCRIPTION
    Genera un dump comprimido de la base de datos Postgres que corre en el
    contenedor Docker "gymflow_postgres", sin necesitar pg_dump instalado en
    Windows (se ejecuta DENTRO del contenedor, que ya lo trae).

    Pensado para backups manuales de desarrollo (antes de una migración
    riesgosa, antes de probar un cambio destructivo, etc.) — NO reemplaza el
    backup automatizado de producción (ver .github/workflows/backup.yml).

.EXAMPLE
    .\scripts\backup-local.ps1
    .\scripts\backup-local.ps1 -OutputDir "D:\mis-backups"
#>

param(
    [string]$OutputDir = "$PSScriptRoot\..\backups",
    [string]$ContainerName = "gymflow_postgres",
    [string]$DbName = "gymflow_db",
    [string]$DbUser = "gymflow_user"
)

$ErrorActionPreference = "Stop"

# Verificar que el contenedor está corriendo
$running = docker ps --filter "name=$ContainerName" --filter "status=running" -q
if (-not $running) {
    Write-Error "El contenedor '$ContainerName' no está corriendo. Levantalo con 'docker-compose up -d' antes de hacer el backup."
    exit 1
}

# Crear carpeta de backups si no existe (queda gitignoreada, ver .gitignore)
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$fileName = "gymflow_backup_$timestamp.sql"
$filePath = Join-Path $OutputDir $fileName

Write-Host "Generando backup de '$DbName' desde el contenedor '$ContainerName'..." -ForegroundColor Cyan

# pg_dump corre DENTRO del contenedor; el resultado se redirige a un archivo local.
# --clean --if-exists: el dump incluye DROP antes de CREATE, para que restaurar
# sobre una BD existente no falle por objetos duplicados.
docker exec $ContainerName pg_dump -U $DbUser -d $DbName --clean --if-exists --no-owner > $filePath

if ($LASTEXITCODE -ne 0) {
    Write-Error "pg_dump falló (exit code $LASTEXITCODE). Revisá que el usuario/BD sean correctos."
    exit 1
}

# Comprimir con gzip (viene con Git Bash / WSL; si no está disponible, dejamos el .sql sin comprimir)
$gzipAvailable = Get-Command gzip -ErrorAction SilentlyContinue
if ($gzipAvailable) {
    gzip -f $filePath
    $filePath = "$filePath.gz"
}

$sizeKb = [math]::Round((Get-Item $filePath).Length / 1KB, 1)
Write-Host "Backup completo: $filePath ($sizeKb KB)" -ForegroundColor Green

# Retención simple: dejar solo los últimos 10 backups locales, para no llenar el disco.
$allBackups = Get-ChildItem $OutputDir -Filter "gymflow_backup_*" | Sort-Object LastWriteTime -Descending
if ($allBackups.Count -gt 10) {
    $toDelete = $allBackups | Select-Object -Skip 10
    $toDelete | Remove-Item -Force
    Write-Host "Limpieza: eliminados $($toDelete.Count) backups viejos (se conservan los últimos 10)." -ForegroundColor DarkGray
}
