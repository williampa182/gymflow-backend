<#
.SYNOPSIS
    Restaura un backup local en la base de datos GymFlow (desarrollo).

.DESCRIPTION
    Toma un archivo .sql o .sql.gz generado por backup-local.ps1 y lo
    restaura DENTRO del contenedor Docker "gymflow_postgres". El dump incluye
    --clean --if-exists, así que es seguro restaurar sobre una BD que ya
    tiene datos (los reemplaza).

    ⚠️ Esto SOBREESCRIBE la base de datos de desarrollo actual. Nunca correr
    contra producción sin confirmar antes.

.EXAMPLE
    .\scripts\restore-local.ps1 -BackupFile ".\backups\gymflow_backup_20260707_213000.sql.gz"
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$BackupFile,
    [string]$ContainerName = "gymflow_postgres",
    [string]$DbName = "gymflow_db",
    [string]$DbUser = "gymflow_user"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
    Write-Error "No se encontró el archivo: $BackupFile"
    exit 1
}

$running = docker ps --filter "name=$ContainerName" --filter "status=running" -q
if (-not $running) {
    Write-Error "El contenedor '$ContainerName' no está corriendo. Levantalo con 'docker-compose up -d' antes de restaurar."
    exit 1
}

Write-Host "⚠️  Esto va a SOBREESCRIBIR la base de datos '$DbName' en el contenedor '$ContainerName'." -ForegroundColor Yellow
$confirm = Read-Host "Escribí 'si' para confirmar"
if ($confirm -ne "si") {
    Write-Host "Cancelado." -ForegroundColor DarkGray
    exit 0
}

# Descomprimir a un archivo temporal si hace falta
$sqlFile = $BackupFile
$tempFile = $null
if ($BackupFile -like "*.gz") {
    $gzipAvailable = Get-Command gzip -ErrorAction SilentlyContinue
    if (-not $gzipAvailable) {
        Write-Error "El archivo está comprimido (.gz) pero 'gzip' no está disponible en el PATH. Instalá Git Bash o WSL, o descomprimí manualmente."
        exit 1
    }
    $tempFile = [System.IO.Path]::GetTempFileName() + ".sql"
    gzip -dc $BackupFile > $tempFile
    $sqlFile = $tempFile
}

Write-Host "Restaurando desde $sqlFile ..." -ForegroundColor Cyan

Get-Content $sqlFile | docker exec -i $ContainerName psql -U $DbUser -d $DbName

if ($LASTEXITCODE -ne 0) {
    Write-Error "La restauración falló (exit code $LASTEXITCODE)."
    if ($tempFile) { Remove-Item $tempFile -Force -ErrorAction SilentlyContinue }
    exit 1
}

if ($tempFile) { Remove-Item $tempFile -Force -ErrorAction SilentlyContinue }

Write-Host "Restauración completa." -ForegroundColor Green
