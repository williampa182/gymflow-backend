<#
.SYNOPSIS
    D0: modo demo local de GymFlow (100% ficticio, Postgres local).
.DESCRIPTION
    Levanta el backend (JAR local), aplica migraciones 001-006 + seed de
    planes, registra 1 ADMIN + 40 socios ficticios via API, siembra
    suscripciones (28 ACTIVA + 12 VENCIDA) y corre los 3 flujos DOS veces
    seguidas con asserts. Todo contra localhost:8080 + gymflow_postgres.
    PROHIBIDO: Neon/Render/prod (este script no conoce esas URLs).
    Uso: powershell -NoProfile -File scripts\demo-d0\run-D0.ps1
    (correr desde gymflow-backend).
#>
param([switch]$SoloFlujos)
$ErrorActionPreference = "Stop"
$BackendDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Evidence = Join-Path $env:TEMP "opencode\evidencia-D0.txt"
$SrvLog = Join-Path $env:TEMP "opencode\backend-D0.log"
$SrvErr = Join-Path $env:TEMP "opencode\backend-D0.err.log"
Start-Transcript -Path $Evidence -Force | Out-Null

function Api($Method, $Path, $Token, $Body) {
    $h = @{}
    if ($Token) { $h["Authorization"] = "Bearer $Token" }
    $p = @{ Method = $Method; Uri = "http://localhost:8080$Path"; Headers = $h }
    if ($Body) { $p["Body"] = ($Body | ConvertTo-Json); $p["ContentType"] = "application/json" }
    # Rate limit auth: 10/min/IP/ruta (LoginRateLimitFilter.java:41). Reintento con espera ante 429.
    for ($att = 0; $att -lt 3; $att++) {
        try { return Invoke-RestMethod @p }
        catch {
            $code = $null
            try { $code = [int]$_.Exception.Response.StatusCode } catch { }
            if ($code -eq 429 -and $att -lt 2) { Write-Host "  429, esperando 70s y reintentando..."; Start-Sleep -Seconds 70 }
            else { throw }
        }
    }
}
function Assert($Cond, $Msg) {
    if (-not $Cond) { throw ("ASSERT FALLO: " + $Msg) }
    Write-Host ("OK: " + $Msg)
}
function Q1($Sql) {
    $out = ($Sql | docker exec -i gymflow_postgres psql -U gymflow_user -d gymflow_db -v ON_ERROR_STOP=1 -q -t -A)
    if ($LASTEXITCODE -ne 0) { throw ("psql fallo: " + $Sql) }
    $line = ($out | Where-Object { $_ -match '\S' } | Select-Object -Last 1)
    return $line.Trim()
}

try {
    if (-not $SoloFlujos) {
        Write-Host "== [1/7] Arrancando backend =="
        $srv = Start-Process java -ArgumentList "-jar", "target\gymflow-backend-0.0.1-SNAPSHOT.jar" `
            -WorkingDirectory $BackendDir -PassThru `
            -RedirectStandardOutput $SrvLog -RedirectStandardError $SrvErr
        $up = $false
        for ($i = 0; $i -lt 42; $i++) {
            Start-Sleep -Seconds 10
            try {
                $h = Invoke-RestMethod "http://localhost:8080/actuator/health"
                if ($h.status -eq "UP") { $up = $true; break }
            } catch { Write-Host ("  esperando backend... (" + $i + ")") }
        }
        Assert $up "backend UP"
    } else {
        $h = Invoke-RestMethod "http://localhost:8080/actuator/health"
        Assert ($h.status -eq "UP") "backend UP (externo)"
    }

    Write-Host "== [2/7] Migraciones 001-006 + seed planes =="
    $migs = @("001_unique_suscripcion_activa", "002_notificado_en_suscripciones", "003_rutinas_y_ejercicios",
              "004_asignaciones_entrenador_y_rutina", "005_codigo_carnet_y_asistencias", "006_kiosco_config")
    foreach ($n in $migs) {
        # -Encoding UTF8: sin esto, PowerShell decodifica como ANSI y las tildes (á en seed_planes)
        # llegan como mojibake a Postgres (incidente D0 2026-09-20: "B??sico").
        Get-Content (Join-Path $BackendDir ("scripts\migrations\" + $n + ".sql")) -Raw -Encoding UTF8 |
            docker exec -i gymflow_postgres psql -U gymflow_user -d gymflow_db -v ON_ERROR_STOP=1 -q
        if ($LASTEXITCODE -ne 0) { throw ("migracion " + $n + " fallo") }
    }
    Get-Content (Join-Path $BackendDir "scripts\seed_planes.sql") -Raw -Encoding UTF8 |
        docker exec -i gymflow_postgres psql -U gymflow_user -d gymflow_db -v ON_ERROR_STOP=1 -q
    $c = Q1 "SELECT count(*) FROM planes;"
    Assert ($c -eq "4") "4 planes seed"

    Write-Host "== [3/7] Registro ADMIN + 40 socios =="
    $admin = Api "POST" "/api/auth/register" $null @{ nombre = "Dueno Demo"; email = "dueno@gymflow.demo"; password = "DuenoDemo2026!##" }
    Assert ($admin.rol -eq "ADMIN") "primer registro nace ADMIN (bootstrap local)"
    $login = Api "POST" "/api/auth/login" $null @{ email = "dueno@gymflow.demo"; password = "DuenoDemo2026!##" }
    $adminToken = $login.token
    Assert ($adminToken.Length -gt 20) "login ADMIN devuelve token"

    $nombres = @("Carlos", "Maria", "Jose", "Ana", "Luis", "Carmen", "Miguel", "Rosa", "Pedro", "Lucia")
    $apellidos = @("Mendoza", "Rojas", "Contreras", "Villamizar", "Pabon", "Acevedo", "Duran", "Quintero", "Sepulveda", "Penaranda")
    $k = 0
    for ($i = 0; $i -lt 10; $i++) {
        for ($j = 0; $j -lt 4; $j++) {
            $k++
            $num = $k.ToString("00")
            $r = Api "POST" "/api/auth/register" $null @{
                nombre = ($nombres[$i] + " " + $apellidos[$j])
                email = ("socio" + $num + "@gymflow.demo")
                password = ("SocioDemo2026!#" + $num)
            }
            Assert ($r.rol -eq "CLIENTE") ("socio" + $num + " CLIENTE")
            Start-Sleep -Seconds 7  # pacing bajo el rate limit de register (10/min)
        }
    }
    $c = Q1 "SELECT count(*) FROM usuarios WHERE rol='CLIENTE';"
    Assert ($c -eq "40") "40 socios en BD"

    Write-Host "== [4/7] Siembra suscripciones =="
    $ids = (Q1 "SELECT string_agg(id::text, ',' ORDER BY id) FROM usuarios WHERE rol='CLIENTE';") -split ","
    $hoy = Get-Date
    $sql = ""
    for ($i = 0; $i -lt 40; $i++) {
        $uid = $ids[$i].Trim()
        $plan = 1 + ($i % 4)
        $dur = @(30, 30, 90, 365)[$i % 4]
        if ($i -lt 28) {
            $ini = $hoy.AddDays(-1 * ($i % 20)).ToString("yyyy-MM-dd")
            $fin = ([datetime]$ini).AddDays($dur).ToString("yyyy-MM-dd")
            $est = "ACTIVA"
        } else {
            $ini = $hoy.AddDays(-1 * (60 + $i)).ToString("yyyy-MM-dd")
            $fin = ([datetime]$ini).AddDays($dur).ToString("yyyy-MM-dd")
            $est = "VENCIDA"
        }
        $sql += "INSERT INTO suscripciones (usuario_id, plan_id, fecha_inicio, fecha_fin, estado, creado_en, version) VALUES ($uid, $plan, '$ini', '$fin', '$est', NOW(), 0);`n"
    }
    $sql | docker exec -i gymflow_postgres psql -U gymflow_user -d gymflow_db -v ON_ERROR_STOP=1 -q
    if ($LASTEXITCODE -ne 0) { throw "siembra suscripciones fallo" }
    $c = Q1 "SELECT count(*) FROM suscripciones WHERE estado='ACTIVA';"
    Assert ($c -eq "28") "28 ACTIVA"
    $c = Q1 "SELECT count(*) FROM suscripciones WHERE estado='VENCIDA';"
    Assert ($c -eq "12") "12 VENCIDA"

    for ($ronda = 1; $ronda -le 2; $ronda++) {
        Write-Host ("== RONDA " + $ronda + ": flujo 1 registrar socio ==")
        $email = ("demoronda" + $ronda + "@gymflow.demo")
        $nuevo = Api "POST" "/api/auth/register" $null @{ nombre = ("Demo Ronda " + $ronda); email = $email; password = ("RondaDemo2026!#" + $ronda) }
        Assert (($nuevo.id -gt 0) -and ($nuevo.rol -eq "CLIENTE")) ("ronda " + $ronda + ": socio registrado id=" + $nuevo.id)

        Write-Host ("== RONDA " + $ronda + ": flujo 2 marcar pago manual ==")
        $sub = Api "POST" "/api/suscripciones" $adminToken @{ usuarioId = $nuevo.id; planId = 1; fechaInicio = $hoy.ToString("yyyy-MM-dd") }
        Assert ($sub.estado -eq "ACTIVA") ("ronda " + $ronda + ": pago marcado sub id=" + $sub.id + " ACTIVA")

        Write-Host ("== RONDA " + $ronda + ": flujo 3 quien debe hoy ==")
        $deb = Api "GET" "/api/suscripciones?estado=VENCIDA&size=50" $adminToken
        Assert ($deb.totalElements -eq 12) ("ronda " + $ronda + ": 12 deudores")
        $nombres_deuda = @($deb.content | ForEach-Object { $_.nombreUsuario })
        Assert (-not ($nombres_deuda -contains ("Demo Ronda " + $ronda))) ("ronda " + $ronda + ": recien pagado NO esta en deuda")
        Write-Host ("Deudores (12): " + ($nombres_deuda -join "; "))
    }
    Write-Host "== D0 VERDE: 3 flujos x 2 rondas sin errores =="
}
finally {
    Write-Host "== Apagando backend =="
    try { Stop-Process -Id $srv.Id -Force -ErrorAction SilentlyContinue } catch { }
    try { Stop-Transcript | Out-Null } catch { }
}
