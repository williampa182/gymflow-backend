# GymFlow — Arquitectura técnica completa

> Documento vivo. Última actualización: 2026-07-07. Pensado para que cualquier
> sesión futura (con Claude, otra IA, u otro desarrollador) tenga el contexto
> técnico completo de una sola lectura, sin depender de memoria de conversación.

## 1. Visión general

GymFlow es un sistema de gestión de gimnasios (planes, suscripciones,
usuarios con roles) construido como proyecto de portafolio. Stack: Spring
Boot (backend) + Next.js (frontend) + PostgreSQL + Redis, con CI/CD en
GitHub Actions y despliegue planeado en Railway.

**Repos** (ambos privados):
- `github.com/williampa182/gymflow-backend`
- Frontend en el mismo dueño, repo separado (`gymflow-frontend`)

**Rutas locales:**
- Backend: `C:\proyectos\gymflow\gymflow-backend`
- Frontend: `C:\proyectos\gymflow\gymflow-frontend`

---

## 2. Backend — Spring Boot

### 2.1 Stack y versiones

| Componente | Versión |
|---|---|
| Spring Boot | 3.5.16 (migración a 4.1.0 programada, ver §7) |
| Java | 21 (Temurin) |
| Maven | vía wrapper `mvnw` |
| PostgreSQL | 16 (Docker: `postgres:16`) |
| Redis | 7-alpine |
| JJWT | 0.13.0 |
| springdoc-openapi | 2.8.9 |

### 2.2 Estructura de paquetes (`com.gymflow.backend`)

```
config/           SecurityConfig, RedisCacheConfig, SwaggerConfig
controller/       AuthController, PlanController, SuscripcionController, UsuarioController
dto/              request/, response/, PlanRequestDTO, PlanResponseDTO, etc.
model/            Usuario, Plan, Suscripcion + enums/ (Rol, TipoPlan, EstadoSuscripcion)
observability/    CorrelationIdFilter
repository/       UsuarioRepository, PlanRepository, SuscripcionRepository (Spring Data JPA)
security/
  filter/         LoginRateLimitFilter
  jwt/            JwtAuthFilter, JwtUtil
  service/        UserDetailsServiceImpl
service/          AuthService, PlanService, SuscripcionService, UsuarioService
```

### 2.3 Modelo de datos

**`Usuario`** (`usuarios`) — implementa `UserDetails` directamente:
- `id`, `nombre`, `email` (unique), `password` (BCrypt), `rol` (enum: `ADMIN`, `ENTRENADOR`, `CLIENTE`), `activo` (boolean), `creadoEn`
- `getAuthorities()` devuelve `ROLE_<rol>` (ej. `ROLE_ADMIN`) — clave para que `hasRole("ADMIN")` funcione en Spring Security
- `isAccountNonLocked()` e `isEnabled()` devuelven `activo` — desactivar un usuario lo bloquea automáticamente sin borrar datos

**`Plan`** (`planes`):
- `nombre` (unique), `descripcion`, `precio` (**`BigDecimal`**, nunca `Double` — precisión monetaria), `duracionDias`, `tipo` (enum `TipoPlan`), `limiteClases`, `incluyeClases`, `incluyeEntrenadorPersonal`, `activo`, `creadoEn`

**`Suscripcion`** (`suscripciones`):
- `usuario` (`@ManyToOne` lazy), `plan` (`@ManyToOne` lazy), `fechaInicio`, `fechaFin`, `estado` (enum `EstadoSuscripcion`), `creadoEn`
- `fechaFin` se calcula automáticamente en `@PrePersist`: `fechaInicio.plusDays(plan.getDuracionDias())` — nunca se setea manualmente

### 2.4 Endpoints completos

**`/api/auth`** (`AuthController`) — públicos:
- `POST /api/auth/register`
- `POST /api/auth/login`

**`/api/planes`** (`PlanController`):
- `POST /api/planes` — `ADMIN`
- `GET /api/planes?activo=` — cualquier autenticado
- `GET /api/planes/{id}` — cualquier autenticado
- `PUT /api/planes/{id}` — `ADMIN`
- `PATCH /api/planes/{id}/estado?activo=` — `ADMIN`

**`/api/suscripciones`** (`SuscripcionController`) — **todo `ADMIN`**:
- `POST /api/suscripciones`
- `GET /api/suscripciones/usuario/{usuarioId}`
- `GET /api/suscripciones?estado=`
- `PATCH /api/suscripciones/{id}/cancelar`

**`/api/usuarios`** (`UsuarioController`) — **todo `ADMIN`**:
- `GET /api/usuarios?rol=`
- `PATCH /api/usuarios/{id}/estado?activo=`

**Actuator** (agregado 2026-07-07, ver §6):
- `GET /actuator/health`, `/actuator/info` — públicos
- `GET /actuator/prometheus`, resto de `/actuator/**` — `ADMIN`

**Swagger**: `/swagger-ui/**`, `/v3/api-docs/**` — públicos

La autorización a nivel de método usa `@PreAuthorize("hasRole('ADMIN')")`
(habilitado vía `@EnableMethodSecurity` en `SecurityConfig`), no solo reglas
a nivel de `SecurityFilterChain` — doble capa de defensa.

### 2.5 Seguridad — cadena de filtros

Orden real (confirmado en logs de arranque):
```
DisableEncodeUrlFilter → WebAsyncManagerIntegrationFilter →
SecurityContextHolderFilter → HeaderWriterFilter → CorsFilter →
LogoutFilter → LoginRateLimitFilter → JwtAuthFilter →
RequestCacheAwareFilter → SecurityContextHolderAwareRequestFilter →
AnonymousAuthenticationFilter → SessionManagementFilter →
ExceptionTranslationFilter → AuthorizationFilter
```

**`LoginRateLimitFilter`**: solo actúa sobre `/api/auth/login` y
`/api/auth/register`. Cuenta intentos por IP en Redis (`ratelimit:auth:<ip>`),
ventana fija de 1 minuto, máximo 10 intentos → `429 Too Many Requests`. La IP
se obtiene de `X-Forwarded-For` si existe (necesario detrás del proxy de
Railway), si no de `getRemoteAddr()`.

**`JwtAuthFilter`**: lee el header `Authorization: Bearer <token>` (NO lee
cookies directamente — eso lo hace el proxy de Next.js, ver §3.3). Si el
token es válido, arma un `UsernamePasswordAuthenticationToken` con las
authorities del usuario y lo pone en el `SecurityContextHolder`.

**`JwtUtil`**: HMAC-SHA con la clave de `app.jwt.secret` (variable de
entorno `JWT_SECRET`). Firma con `jjwt` 0.13.0, expiración configurable vía
`app.jwt.expiration` (`JWT_EXPIRATION`, default 86400000 ms = 24h).

**`CorsConfigurationSource`**: origen permitido hardcodeado a
`http://localhost:3000` — **hay que actualizar esto al desplegar a Railway**
con el dominio real del frontend (ver §7, pendientes de deploy).

**`CorrelationIdFilter`** (`observability/`, agregado 2026-07-07): no es
parte de la cadena de Spring Security, es un `@Component` con
`@Order(Ordered.HIGHEST_PRECEDENCE)` — se registra directo en el contenedor
de servlets de Tomcat. Genera/reutiliza `X-Request-ID`, lo pone en MDC
(`correlationId`), se ve en todos los logs de ese request. Se limpia en el
`finally` para no filtrar entre requests (los hilos de Tomcat se reusan).

### 2.6 Cache — Redis

`RedisCacheConfig`: usa `GenericJackson2JsonRedisSerializer` (JSON, no
serialización Java nativa — más rápido y legible desde `redis-cli`). TTL
por defecto: 10 minutos. Actualmente cacheado: `PlanService.listar()` con
clave `#activo != null ? #activo : 'todos'`, invalidado con `@CacheEvict`
en `crear`, `actualizar`, `cambiarEstado`.

### 2.7 Observabilidad (agregado 2026-07-07)

- **Actuator + Micrometer + Prometheus**: `/actuator/health` (con grupos
  `liveness`/`readiness`, listo para health checks de Railway),
  `/actuator/info` (con build-info real vía plugin Maven), `/actuator/prometheus`.
- **Logging**: `logback-spring.xml` con dos perfiles — `dev`/`default`
  (texto plano coloreado en consola) y `prod`/`railway` (JSON estructurado
  vía `logstash-logback-encoder`, listo para Grafana Loki). **El perfil se
  activa con la variable `SPRING_PROFILES_ACTIVE`** — sin setearla en
  Railway, se queda en texto plano.
- Validado en local: correlationId presente en 100% de las líneas de un
  mismo request; `/actuator/prometheus` devuelve 403 sin JWT de ADMIN
  (confirmado en pruebas reales).

### 2.8 Backups (agregado 2026-07-07)

Dos niveles, 100% gratuitos (ver `docs/BACKUP_RUNBOOK.md` para el detalle
operativo completo):

1. **Local/dev**: `scripts/backup-local.ps1` y `restore-local.ps1` — usan
   el `pg_dump`/`psql` que ya trae el contenedor Docker, sin instalar nada
   en Windows. Retención de los últimos 10 backups locales.
2. **Producción**: `.github/workflows/backup.yml` — cron diario 07:00 UTC,
   `pg_dump` contra Railway vía secret `PROD_DATABASE_URL` (aún no
   configurado — pendiente hasta que exista el deploy), verificación de
   integridad, artifact de GitHub con 35 días de retención.

**Limitación conocida y aceptada**: no es Point-in-Time Recovery real (eso
existe en Railway pero solo en plan Pro). Es snapshot diario — ventana de
pérdida de hasta ~24h entre backups.

### 2.9 CI/CD

`.github/workflows/backend-ci.yml`: se dispara en push/PR a `main`. Compila
con Java 21 (Temurin), corre los 16 tests unitarios (Mockito puro, sin
Spring context, no requieren Docker). `GymflowBackendApplicationTests` está
`@Disabled` por depender de Docker.

`.github/workflows/backup.yml`: ver §2.8.

`dependabot.yml`: actualizaciones semanales. **PRs abiertos y atados entre
sí, NO mergear por separado**: PR #4 (`spring-boot-starter-parent`
3.5.16→4.1.0) y PR #7 (`springdoc-openapi` 2.8.9→3.0.3, requiere Boot 4.x
como parent). Ver §7 para el plan de migración.

---

## 3. Frontend — Next.js

### 3.1 Estructura (`src/`)

```
app/
  api/
    auth/login/route.ts       Route Handler: recibe credenciales, llama al
                               backend, setea cookies httpOnly (token) y
                               legible (session)
    auth/logout/route.ts      Limpia cookies
    backend/[...path]/route.ts Proxy genérico: reenvía cualquier request a
                               /api/backend/* hacia el backend real, adjuntando
                               el JWT como Authorization: Bearer
  dashboard/                  layout.tsx + page.tsx + planes/, suscripciones/, usuarios/
  login/page.tsx
  layout.tsx, page.tsx, globals.css
lib/
  api.ts                      Cliente HTTP (probablemente axios) hacia /api/backend/*
  auth.ts                     Helpers de sesión
  ui.ts                       Utilidades de UI
proxy.ts                      (raíz de src/)
types/index.ts                Tipos TypeScript compartidos
```

### 3.2 Sistema de diseño

Concepto "sala de máquinas" (industrial/gimnasio): paleta concreto/industrial
con acento ámbar, tipografías Oswald + Inter + JetBrains Mono, badges
circulares tipo placa de peso para stats. Mobile responsive, con overflow de
tablas corregido.

### 3.3 Mecanismo de autenticación — el detalle que más importa

**El JWT NUNCA llega al JavaScript del navegador.** Flujo exacto:

1. El navegador hace `POST /api/auth/login` — esto pega a la ruta LOCAL de
   Next.js (`app/api/auth/login/route.ts`), NO directo al backend Spring.
2. Ese Route Handler (código que corre en el servidor de Next.js, no en el
   navegador) llama al backend real (`BACKEND_URL/api/auth/login`), recibe
   `{ token, id, nombre, email, rol }`.
3. Setea DOS cookies en la respuesta:
   - `token`: **httpOnly**, `secure` en producción, `sameSite: lax`, 24h —
     el JWT real, invisible para JS.
   - `session`: **no httpOnly**, mismos datos MENOS el token (`id`, `nombre`,
     `email`, `rol`) — para que la UI muestre el nombre/rol sin decodificar
     el JWT.
4. Cuando el cliente necesita pegarle al backend (ej. listar planes), llama
   a una ruta LOCAL tipo `/api/backend/planes` — nunca directo al backend.
5. `app/api/backend/[...path]/route.ts` (corre server-side) lee la cookie
   `token` (que SÍ puede leer porque está en el servidor), arma el header
   `Authorization: Bearer <token>`, y reenvía la request real al backend
   Spring en `BACKEND_URL/api/<path>`.
6. La respuesta del backend se devuelve tal cual al cliente.

**Por qué importa**: esto es lo que hace que un ataque XSS no pueda robarse
el token — ni siquiera con `document.cookie` se puede leer, porque `httpOnly`
lo bloquea a nivel de navegador. El precio es que TODO request autenticado
pasa por un hop extra (navegador → Next.js server → Spring backend), pero es
el patrón correcto para SPAs con JWT.

**Variable de entorno clave**: `BACKEND_URL` (default `http://localhost:8080`
en dev) — en Railway hay que apuntarla a la URL interna del backend.

---

## 4. Infraestructura local (desarrollo)

`docker-compose.yml` (en `gymflow-backend/`):
- `postgres:16` → puerto 5432, credenciales `gymflow_user`/`gymflow_pass`/`gymflow_db`
- `redis:7-alpine` → puerto 6379

**Orden de arranque obligatorio**: `docker-compose up -d` SIEMPRE antes de
`.\mvnw spring-boot:run` — si el backend intenta conectar antes de que
Postgres/Redis estén arriba, falla el arranque.

**Usuarios de prueba** (sembrados en dev):
- `william@gymflow.com` / `admin1234` (ADMIN, id 1)
- `cliente@gymflow.com` / `cliente1234` (CLIENTE)

---

## 5. Variables de entorno

### Backend (`application.yaml`, todas con default de dev)

| Variable | Default dev | Notas |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | localhost:5432/gymflow_db/gymflow_user/gymflow_pass | |
| `REDIS_HOST`, `REDIS_PORT` | localhost:6379 | |
| `SERVER_PORT` | 8080 | |
| `DDL_AUTO` | `update` | **En prod DEBE ser `validate`** — nunca dejar que Hibernate toque el esquema solo en producción |
| `SHOW_SQL` | `true` | Apagar en prod |
| `JWT_SECRET` | valor dev-only en el yaml, comentado como NO usar en real | **Generar nuevo con `openssl rand -base64 64` para Railway** — el original quedó expuesto en git history (purgado 2026-07-07, pero por las dudas nunca reusar) |
| `JWT_EXPIRATION` | 86400000 (24h) | |
| `SECURITY_LOG_LEVEL` | TRACE | Bajar a WARN en prod |
| `SPRING_PROFILES_ACTIVE` | (ninguno → `default`) | **Setear a `prod` en Railway** para logs JSON |
| `HEALTH_SHOW_DETAILS` | `when-authorized` | |

### Frontend

| Variable | Notas |
|---|---|
| `BACKEND_URL` | URL del backend, server-side only |
| `NEXT_PUBLIC_API_URL` | Mencionado en roadmap de deploy, verificar uso exacto en `lib/api.ts` |

### GitHub Actions (secrets)

| Secret | Usado en | Estado |
|---|---|---|
| `PROD_DATABASE_URL` | `.github/workflows/backup.yml` | **Pendiente** — configurar cuando exista Railway (usar `DATABASE_PUBLIC_URL`, no la interna) |

---

## 6. Historial de seguridad relevante

**2026-07-07 — Purga de JWT secret del historial de Git**: el commit
`c884c3f` externalizó el JWT secret a variable de entorno, pero commits
anteriores tenían el valor hardcodeado
(`gymflow-super-secret-key-2024-must-be-at-least-256-bits-long!!`) en texto
plano. Se purgó con `git-filter-repo --replace-text` de los 20 commits del
historial, local y remoto (force-push). **Cualquier clone previo a esa fecha
queda desincronizado y necesita re-clonar.** El secret purgado NUNCA debe
reusarse en ningún entorno, ni siquiera dev — se considera comprometido
permanentemente por haber estado público en GitHub en algún momento.

---

## 7. Pendientes conocidos (con contexto de por qué)

1. **Migración a Spring Boot 4.x** (PRs Dependabot #4 y #7) — programada
   como sesión dedicada aparte, NO mezclar con otro trabajo. Riesgos
   catalogados: starters modulares, Spring Security 7 (config explícita),
   `@MockBean`/`@SpyBean` removidos (afecta los 16 tests), Jackson 3
   (revisar serialización de `BigDecimal`).

2. **Concurrencia y manejo de fallos** (Fase 5, parte pendiente) — aún no
   iniciado. Áreas candidatas sin explorar todavía: manejo de condiciones
   de carrera en `Suscripcion` (¿qué pasa si dos requests cancelan la misma
   suscripción simultáneamente?), timeouts/circuit breakers hacia
   Postgres/Redis, manejo de fallos de conexión a Redis (¿la app debe caer
   si Redis no responde, o degradar sin cache?).

3. **Deploy a Railway** — checklist conocido:
   - Actualizar `CorsConfigurationSource` en `SecurityConfig.java` con el
     dominio real del frontend (hoy hardcodeado a `localhost:3000`)
   - Generar `JWT_SECRET` real con `openssl rand -base64 64`
   - Setear `NEXT_PUBLIC_API_URL` y `BACKEND_URL`
   - Setear `SPRING_PROFILES_ACTIVE=prod` (logging JSON)
   - Setear `DDL_AUTO=validate` (nunca `update` en prod)
   - Configurar secret `PROD_DATABASE_URL` en GitHub para activar el backup
     automático (usar la connection string pública de Railway)
   - Considerar activar PITR nativo de Railway si en algún momento se paga
     el plan Pro (mejora directa sobre el backup diario actual)

4. **`gzip` no disponible por defecto en PowerShell de Windows** — el script
   `backup-local.ps1` cae a dejar el `.sql` sin comprimir si no detecta
   `gzip` en el PATH. No es un bug, es el comportamiento de respaldo
   diseñado a propósito, pero vale la pena instalar Git Bash/WSL si se
   quiere compresión real en los backups locales.

5. **`py`/`python` no quedaron en el PATH del sistema** tras la instalación
   vía winget en la máquina de desarrollo — se invocan con ruta completa
   (`%LOCALAPPDATA%\Microsoft\WindowsApps\py.exe`) o `py -m <módulo>`.
   Pendiente menor: arreglar el PATH vía GUI de variables de entorno si se
   quiere volver a necesitar Python/pip cómodamente.

---

## 8. Decisiones de diseño (el "por qué", no solo el "qué")

- **`BigDecimal` para dinero**: nunca `Double`/`float` — errores de
  redondeo en precios son inaceptables incluso en un proyecto de portafolio.
- **JWT en cookie httpOnly + proxy Next.js**, no `localStorage`: mitiga XSS
  robando el token; el costo es un hop de red extra por request autenticado.
- **Rate limiting con Redis, no en memoria**: si en el futuro se escala a
  más de una instancia del backend, un contador en memoria por instancia no
  serviría — Redis centraliza el conteo.
- **`fechaFin` calculada en `@PrePersist`, nunca recibida del cliente**: evita
  que alguien mande una fecha de fin arbitraria por la API.
- **`activo` en `Usuario` controla `isEnabled()` e `isAccountNonLocked()`**:
  desactivar un usuario lo bloquea de login inmediatamente sin necesidad de
  borrar filas ni revocar tokens uno por uno (aunque un JWT ya emitido sigue
  siendo válido hasta que expire — no hay revocación de tokens activos
  todavía, es una limitación conocida no resuelta).
- **Kubernetes explícitamente fuera de scope** — decisión tomada para no
  sobre-ingenierizar un proyecto de portafolio de este tamaño.
- **`@PreAuthorize` a nivel de método ADEMÁS de reglas en `SecurityConfig`**:
  defensa en profundidad — si alguien agrega un endpoint nuevo y se olvida
  de la regla en `SecurityConfig`, el `@PreAuthorize` en el controller
  igual protege.
