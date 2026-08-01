# GymFlow — Arquitectura técnica completa

> Documento vivo. Última actualización: 2026-08-01 (migración a Spring Boot
> 4.1.0, deploy a Railway, chatbot, notificaciones Resend — ver §2.1, §2.10,
> §2.11 y §7). Pensado para que cualquier sesión futura (con Claude, otra IA,
> u otro desarrollador) tenga el contexto técnico completo de una sola
> lectura, sin depender de memoria de conversación. Para el estado de
> seguridad vigente, el source of truth es [`THREAT_MODEL.md`](THREAT_MODEL.md)
> + [`SECURITY_CHANGELOG.md`](SECURITY_CHANGELOG.md) + [`SECURITY_AUDIT_LOG.md`](SECURITY_AUDIT_LOG.md).

## 1. Visión general

GymFlow es un sistema de gestión de gimnasios (planes, suscripciones,
usuarios con roles) construido como proyecto de portafolio. Stack: Spring
Boot (backend) + Next.js (frontend) + PostgreSQL + Redis, con CI/CD en
GitHub Actions y **ambos servicios desplegados en Railway** (demostraciones
en vivo en el README de cada repo).

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
| Spring Boot | 4.1.0 (migrado desde 3.5.16, sesión 2026-07-26) |
| Java | 21 (Temurin) |
| Maven | vía wrapper `mvnw` |
| PostgreSQL | 16 en dev (Docker: `postgres:16`); **18 en Railway prod** (divergencia documentada, ver §7) |
| Redis | 7-alpine (`requirepass` + bind a `127.0.0.1` en dev) |
| JJWT | 0.13.0 |
| springdoc-openapi | 3.0.3 (requiere Boot 4.x) |

### 2.2 Estructura de paquetes (`com.gymflow.backend`)

```
client/           ChatCompletionClient (interfaz), GeminiChatCompletionClient,
                  AnthropicChatCompletionClient, ChatCompletionException,
                  EmailClient, ResendEmailClient, EmailEnvioException, EmailPayload
config/           SecurityConfig, SwaggerConfig
controller/       AuthController, PlanController, SuscripcionController, UsuarioController,
                  DashboardAdminController, ChatController, DiagnosticHeaderController
dto/              request/, response/, dashboard/, PlanRequestDTO, PlanResponseDTO, etc.
exception/        GlobalExceptionHandler (@RestControllerAdvice)
model/            Usuario, Plan, Suscripcion + enums/ (Rol, TipoPlan, EstadoSuscripcion)
observability/    CorrelationIdFilter
repository/       UsuarioRepository, PlanRepository, SuscripcionRepository (Spring Data JPA) + projection/
security/
  filter/         LoginRateLimitFilter, ChatRateLimitFilter
  jwt/            JwtAuthFilter, JwtUtil
  auth/           TimingSafeAuthenticationProvider
  service/        UserDetailsServiceImpl
service/          AuthService, PlanService, SuscripcionService, UsuarioService,
                  DashboardAdminService, ChatService, NotificacionVencimientoService,
                  NotificacionVencimientoScheduler, PlantillaEmailVencimiento
validation/       NotCommonPassword, NotCommonPasswordValidator
```

### 2.3 Modelo de datos

**`Usuario`** (`usuarios`) — implementa `UserDetails` directamente:
- `id`, `nombre`, `email` (unique), `password` (BCrypt), `rol` (enum: `ADMIN`, `ENTRENADOR`, `CLIENTE`), `activo` (boolean), `creadoEn`
- `getAuthorities()` devuelve `ROLE_<rol>` (ej. `ROLE_ADMIN`) — clave para que `hasRole("ADMIN")` funcione en Spring Security
- `isAccountNonLocked()` e `isEnabled()` devuelven `activo` — desactivar un usuario lo bloquea automáticamente sin borrar datos

**`Plan`** (`planes`):
- `nombre` (unique), `descripcion`, `precio` (**`BigDecimal`**, nunca `Double` — precisión monetaria), `duracionDias`, `tipo` (enum `TipoPlan`), `limiteClases`, `incluyeClases`, `incluyeEntrenadorPersonal`, `activo`, `creadoEn`

**`Suscripcion`** (`suscripciones`):
- `usuario` (`@ManyToOne` lazy), `plan` (`@ManyToOne` lazy), `fechaInicio`, `fechaFin`, `estado` (enum `EstadoSuscripcion`), `creadoEn`, `notificadoEn` (tracking del aviso de vencimiento, agregado 2026-07-31, migración `002`)
- `fechaFin` se calcula automáticamente en `@PrePersist`: `fechaInicio.plusDays(plan.getDuracionDias())` — nunca se setea manualmente
- `@Version` (optimistic locking, agregado 2026-07-11) — protege contra dos requests modificando la misma fila a la vez (ej. doble cancelación). Complementado con índice único parcial en Postgres `uq_suscripcion_activa_por_usuario` (`scripts/migrations/001_unique_suscripcion_activa.sql`) que cierra el otro caso de la carrera: dos creaciones simultáneas de suscripción activa para el mismo usuario.

### 2.4 Endpoints completos

**`/api/auth`** (`AuthController`) — públicos:
- `POST /api/auth/register` — rol SIEMPRE forzado a `CLIENTE` server-side (ver §6.1, escalada de privilegios corregida)
- `POST /api/auth/login`

**`/api/planes`** (`PlanController`):
- `POST /api/planes` — `ADMIN`
- `GET /api/planes?activo=&page=&size=` — cualquier autenticado, **paginado** (agregado 2026-07-11)
- `GET /api/planes/{id}` — cualquier autenticado
- `PUT /api/planes/{id}` — `ADMIN`
- `PATCH /api/planes/{id}/estado?activo=` — `ADMIN`

**`/api/suscripciones`** (`SuscripcionController`) — **todo `ADMIN`**:
- `POST /api/suscripciones`
- `GET /api/suscripciones/usuario/{usuarioId}?page=&size=` — paginado
- `GET /api/suscripciones?estado=&page=&size=` — paginado
- `PATCH /api/suscripciones/{id}/cancelar`

**`/api/usuarios`** (`UsuarioController`) — **todo `ADMIN`**:
- `GET /api/usuarios?rol=&page=&size=` — paginado
- `PATCH /api/usuarios/{id}/estado?activo=`

**`/api/dashboard/admin`** (`DashboardAdminController`, agregado 2026-07-12) — `ADMIN`:
- `GET /api/dashboard/admin/estadisticas` — agregaciones server-side (usuarios activos por rol, ingresos estimados por tipo de plan sobre suscripciones `ACTIVA`, suscripciones por estado). Ver §9 para el detalle completo.

**`/api/chat`** (`ChatController`, agregado 2026-07-26) — **cualquier usuario autenticado**:
- `POST /api/chat` — chatbot de soporte con RAG simple sobre los planes + guía del dashboard (ver §2.10). Rate limited con `ChatRateLimitFilter` (Redis), kill-switch `app.chat.enabled` → 503.

**`/api/v1/debug`** (`DiagnosticHeaderController`, agregado 2026-07-24) — **temporal**:
- `GET /api/v1/debug/headers` — devuelve `remoteAddr` y todos los headers (verificación del fix 2.2, X-Forwarded-For). Solo se registra si `app.debug-headers.enabled=true` (default `false`, vía `@ConditionalOnProperty`); candidato a eliminación cuando cumpla su función.

**Actuator** (agregado 2026-07-07, ver §6):
- `GET /actuator/health`, `/actuator/info` — públicos
- `GET /actuator/prometheus`, resto de `/actuator/**` — `ADMIN`

**Swagger**: `/swagger-ui/**`, `/v3/api-docs/**` — **gateado**: habilitado solo con
`SWAGGER_ENABLED=true` (default `false`, fix security-deep-dive §2). Cuando está
deshabilitado, springdoc no registra los handlers y los `permitAll()` de
`SecurityConfig` no matchean nada — la defensa real está en el yaml.

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
se resuelve con `RemoteIpValve` nativo de Tomcat (`forward-headers-strategy:
native` + `internal-proxies` en `application.yaml`), que lee
`X-Forwarded-For` de derecha a izquierda descartando saltos internos —
fix del hallazgo 2.2 (ver `THREAT_MODEL.md`). Si Redis no responde, falla
cerrado con 503 explícito (no 429), para que el estado sea distinguible.

**`ChatRateLimitFilter`**: mismo patrón, solo sobre `/api/chat`
(`ratelimit:chat:<ip>`), mismo fail-closed con Redis.

**`JwtAuthFilter`**: lee el header `Authorization: Bearer <token>` (NO lee
cookies directamente — eso lo hace el proxy de Next.js, ver §3.3). Si el
token es válido, arma un `UsernamePasswordAuthenticationToken` con las
authorities del usuario y lo pone en el `SecurityContextHolder`.

**`JwtUtil`**: HMAC-SHA con la clave de `app.jwt.secret` (variable de
entorno `JWT_SECRET`). Firma con `jjwt` 0.13.0, expiración configurable vía
`app.jwt.expiration` (`JWT_EXPIRATION`, default 86400000 ms = 24h).

**`TimingSafeAuthenticationProvider`** (security-deep-dive §1, Fix B):
envuelve el `DaoAuthenticationProvider` (constructor explícito de Spring
Security 7 — breaking change de la migración a Boot 4) y ejecuta un BCrypt
dummy cuando el usuario no existe, eliminando el delta de timing que
permitía enumerar usuarios por login. `AuthService.login()` rehashea
automáticamente hashes con costo desactualizado (los sembrados a cost 10
reabrían el canal).

**`CorsConfigurationSource`**: orígenes permitidos vía variable de entorno
`ALLOWED_ORIGINS` (separados por coma; default dev `http://localhost:3000`).
En Railway se setea con la(s) URL(s) real(es) del frontend — ya no hay nada
hardcodeado (ver `SecurityConfig.java`).

**`CorrelationIdFilter`** (`observability/`, agregado 2026-07-07): no es
parte de la cadena de Spring Security, es un `@Component` con
`@Order(Ordered.HIGHEST_PRECEDENCE)` — se registra directo en el contenedor
de servlets de Tomcat. Genera/reutiliza `X-Request-ID`, lo pone en MDC
(`correlationId`), se ve en todos los logs de ese request. Se limpia en el
`finally` para no filtrar entre requests (los hilos de Tomcat se reusan).

### 2.6 Cache — Redis (removido, 2026-07-11)

**`RedisCacheConfig` fue eliminado del proyecto** (movido a
`docs/codigo-removido/RedisCacheConfig.java.txt` como referencia, no como
código activo). Usaba `GenericJackson2JsonRedisSerializer` con metadata de
tipo (`@class`) en el JSON — hallazgo de seguridad 1.2 en
`docs/THREAT_MODEL.md`: no se demostró RCE explotable con la configuración
actual, pero cuando se sacó el único `@Cacheable` existente
(`PlanService.listar()`, al agregar paginación) quedó como superficie
muerta — riesgo de que alguien la reactive sin revisar el modelo de
seguridad del serializer. `@EnableCaching` también removido de
`GymflowBackendApplication`. **Redis se sigue usando** para
`LoginRateLimitFilter` (rate limiting de auth), ahora con `requirepass`
configurado (ver §6.1).

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
   `pg_dump` (cliente 18, debe coincidir con el Postgres de Railway) contra
   el secret `PROD_DATABASE_URL` — **configurado y corriendo desde
   2026-07-26**, verificación de integridad, artifact de GitHub con 35 días
   de retención.

**Limitación conocida y aceptada**: no es Point-in-Time Recovery real (eso
existe en Railway pero solo en plan Pro). Es snapshot diario — ventana de
pérdida de hasta ~24h entre backups.

### 2.9 CI/CD

`.github/workflows/backend-ci.yml`: se dispara en push/PR a `main`. Compila
con Java 21 (Temurin) y corre la suite completa de tests — 67 tests
(1 `@Disabled`, `GymflowBackendApplicationTests`, por depender de Docker).
Desde el fix del 2026-07-26, **el CI levanta Postgres y Redis reales como
`services`** (antes solo corría tests Mockito puros sin Spring context) —
esto destapó 3 bugs reales: Backend CI nunca había probado contra la BD,
el backup de producción nunca corrió, y Railway resultó estar en Postgres 18.

`.github/workflows/backup.yml`: ver §2.8.

`dependabot.yml`: actualizaciones semanales. **La migración a Boot 4.1.0 +
springdoc 3.0.3 (PRs #4 y #7) se completó en la sesión del 2026-07-26.**
Regla vigente del incidente de ese PR: revisar el changelog real antes de
mergear cualquier bump de Dependabot (ver `THREAT_MODEL.md` §4.3).

### 2.10 Chatbot de soporte (agregado 2026-07-26, blindado 2026-08-01)

`POST /api/chat` — cualquier usuario autenticado, con rate limit en Redis
(`ChatRateLimitFilter`) y kill-switch `app.chat.enabled`/`APP_CHAT_ENABLED`
(default `true`; `false` → 503 sin redeploy).

- **RAG simple**: contexto = los planes activos del sistema + guía estática
  del dashboard (secciones, rutas, roles, acciones — `GUIA_DASHBOARD` en
  `ChatService`). El bot no ejecuta acciones ni ve datos de otros usuarios.
- **Proveedor**: `app.chat.provider` — `gemini` (default, tier gratis,
  modelo `gemini-3.1-flash-lite`) o `anthropic` (`claude-haiku-4-5`). Ambos
  adaptadores en `client/` con la interfaz `ChatCompletionClient`; cambiar
  de proveedor es solo una property. `max-tokens` 1024 como techo (respuestas
  breves por instrucción de prompt, para cuidar la cuota del tier gratis).
- **Blindaje (2026-08-01)**: prompt endurecido (no revelar instrucciones
  internas/stack, no inventar datos, no pedir PII); filtro de emails en
  `ChatService` (regex → respuesta fija sin llamar al proveedor ni quemar
  cuota); aviso de proveedor externo en el UI del chat; log de uso sin
  contenido (proveedor/modelo/tokens/duración en ambos clientes).
- **Frontend**: `ChatWidget.tsx` flotante en el dashboard, con persistencia
  de la conversación en `sessionStorage` (muere al cerrar la pestaña).

### 2.11 Notificaciones de vencimiento vía Resend (agregado 2026-07-31)

`NotificacionVencimientoService` + scheduler diario (`@Scheduled`, cron
configurable `app.email.aviso-cron`, default 09:00):

- Ventana abierta `hoy..hoy+7` (`app.email.aviso-ventana-dias`) con tracking
  por `notificado_en` (migración `002`) — cada suscripción se notifica una
  sola vez.
- `ResendEmailClient` (HTTP directo con `RestClient`, patrón `client/`, cero
  dependencias nuevas) + `PlantillaEmailVencimiento` (HTML con `<table>` +
  texto plano, escapado con `HtmlUtils`). CTA "Renovar ahora" →
  `app.email.cta-base-url` + `/dashboard/suscripciones`.
- Gateado por `app.email.aviso-enabled` (los tests lo apagan). Log sin PII
  (solo `usuario id`). Timeouts connect 3s / read 30s (fix M1, 2026-08-01).

---

## 3. Frontend — Next.js

### 3.1 Estructura (`src/`)

```
proxy.ts                      Gate de rutas /dashboard/* (requiere sesión)
app/
  page.tsx                    Landing (incluye "Decisiones técnicas" + video del dashboard)
  login/, register/           Páginas de auth (AuthShell compartido)
  dashboard/
    layout.tsx + page.tsx     + planes/, suscripciones/, usuarios/
    _components/              AdminDashboardCharts (Recharts), ChatWidget
  api/
    auth/login/route.ts       Route Handler: recibe credenciales, llama al
                               backend, setea cookies httpOnly (token) y
                               legible (session)
    auth/logout/route.ts      Limpia cookies
    auth/register/route.ts    Ídem login, para registro
    auth/session/route.ts     Expone la sesión legible (id, nombre, rol)
    backend/[...path]/route.ts Proxy genérico: reenvía /api/backend/* al
                               backend real, adjuntando el JWT como
                               Authorization: Bearer + valida Origin en
                               métodos de mutación
components/                   AuthShell, ButtonSpinner, EmptyState, PageHeader,
                               Select (custom, a11y), Skeleton, ToastHost
lib/
  api.ts                      Cliente axios hacia /api/backend/* (timeout 15s, 30s en /chat)
  auth.ts                     Helpers de sesión
  chatStorage.ts              Persistencia del chat en sessionStorage
  format.ts                   formatFecha (es-CO) / formatMoneda (COP)
  toast.tsx                   Sistema de toasts custom (cero dependencias)
  ui.ts                       Tokens de UI del sistema "sala de máquinas"
  useFocusTrap.ts, usePageTitle.ts, useRequireRole.ts
types/index.ts                Tipos TypeScript compartidos
```

### 3.2 Sistema de diseño

Concepto "sala de máquinas" (industrial/gimnasio): paleta concreto/industrial
con acento ámbar, tipografías Oswald + Inter + JetBrains Mono, badges
circulares tipo placa de peso para stats, remaches y sombras duras. Mobile
responsive, con overflow de tablas corregido. Componentes propios: `Select`
custom (roving focus, accesible, con `<select>` nativo oculto), toasts sin
dependencias (`lib/toast.tsx`), `PageHeader`, `EmptyState`, skeletons,
`AuthShell`/`ButtonSpinner` compartidos. Tokens en `globals.css` + `lib/ui.ts`.

### 3.3 Mecanismo de autenticación — el detalle que más importa

**El JWT NUNCA llega al JavaScript del navegador.** Flujo exacto
(login y register usan el mismo patrón; `auth/session` expone solo la
cookie legible):

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
   Spring en `BACKEND_URL/api/<path>`. En métodos de mutación valida el
   header `Origin` contra `NEXT_PUBLIC_APP_ORIGIN` (defensa CSRF en
   profundidad, ver `THREAT_MODEL.md` §2.5).
6. La respuesta del backend se devuelve tal cual al cliente.

**Por qué importa**: esto es lo que hace que un ataque XSS no pueda robarse
el token — ni siquiera con `document.cookie` se puede leer, porque `httpOnly`
lo bloquea a nivel de navegador. El precio es que TODO request autenticado
pasa por un hop extra (navegador → Next.js server → Spring backend), pero es
el patrón correcto para SPAs con JWT.

**Variables de entorno clave**: `BACKEND_URL` (server-side, default
`http://localhost:8080`) y `NEXT_PUBLIC_APP_ORIGIN` (origen esperado para la
validación de Origin) — ambas seteadas en Railway.

---

## 4. Infraestructura local (desarrollo)

`docker-compose.yml` (en `gymflow-backend/`):
- `postgres:16` → puerto 5432, credenciales `gymflow_user`/`gymflow_pass`/`gymflow_db` (comentario "DEV ONLY" explícito, hallazgo 4.5)
- `redis:7-alpine` → `127.0.0.1:6379:6379` con `requirepass` (`REDIS_PASSWORD`, default dev `gymflow_redis_dev_only_change_me`) — fix del hallazgo 1.1

**Orden de arranque obligatorio**: `docker-compose up -d` SIEMPRE antes de
`.\mvnw spring-boot:run` — si el backend intenta conectar antes de que
Postgres/Redis estén arriba, falla el arranque.

**`.env` local**: el backend carga `.env` automáticamente vía
`spring.config.import: "optional:file:.env[.properties]"` (agregado
2026-07-30) — para sobrescribir defaults de dev, copiar `.env.example` a
`.env`. El frontend también usa su `.env`.

**Nota de versiones divergentes**: dev corre Postgres 16, Railway prod corre
18 — el backup de CI usa el cliente 18 (ver §2.8). Pendiente alinear el
`docker-compose.yml` a 18 (ver §7).

**Usuarios de prueba** (sembrados en dev):
- `william@gymflow.com` / `admin1234` (ADMIN, id 1)
- `cliente@gymflow.com` / `cliente1234` (CLIENTE)

---

## 5. Variables de entorno

### Backend (`application.yaml`, todas con default de dev)

| Variable | Default dev | Notas |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | localhost:5432/gymflow_db/gymflow_user/gymflow_pass | |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | localhost:6379 / `gymflow_redis_dev_only_change_me` | Password debe coincidir con `docker-compose.yml` (hallazgo 1.1) |
| `SERVER_PORT` | 8080 | |
| `DDL_AUTO` | `update` | **En prod DEBE ser `validate`** — nunca dejar que Hibernate toque el esquema solo en producción |
| `SHOW_SQL` | `false` | `true` solo en dev para depurar queries (fix deep-dive §5: default cambiado) |
| `JWT_SECRET` | valor dev-only en el yaml, comentado como NO usar en real | En prod es un secret generado con `openssl rand -base64 64` — el original quedó expuesto en git history (purgado 2026-07-07, pero por las dudas nunca reusar) |
| `JWT_EXPIRATION` | 86400000 (24h) | |
| `JWT_INCLUDE_TOKEN_IN_RESPONSE` | `true` | Opción C del fix deep-dive §4; `false` cuando se use cookie httpOnly exclusivamente |
| `REVEAL_EMAIL_EXISTS` | `false` | `true` solo si el contexto justifica priorizar UX sobre el threat model (decisión 07-16) |
| `ALLOWED_ORIGINS` | `http://localhost:3000` | En Railway: URL(s) real(es) del frontend separadas por coma |
| `SWAGGER_ENABLED` | `false` | `true` solo en dev/staging para la UI de Swagger (gateado, fix §2) |
| `APP_DEBUG_HEADERS_ENABLED` | `false` | Endpoint de diagnóstico temporal `/api/v1/debug/headers` (fix 2.2) |
| `APP_CHAT_ENABLED` | `true` | Kill-switch del chatbot; `false` → 503 |
| `APP_CHAT_PROVIDER` | `gemini` | `gemini` \| `anthropic` — cambiar solo esta property |
| `ANTHROPIC_API_KEY` / `GEMINI_API_KEY` | placeholder dev-only | En prod: API key real del proveedor activo |
| `RESEND_API_KEY` | placeholder dev-only | En prod: key real de Resend |
| `EMAIL_FROM`, `EMAIL_AVISO_VENTANA_DIAS`, `EMAIL_CTA_BASE_URL`, `EMAIL_AVISO_ENABLED`, `EMAIL_AVISO_CRON` | `no-reply@gymflow.com` / 7 / `http://localhost:3000` / `true` / `0 0 9 * * *` | Notificaciones de vencimiento (ver §2.11). `EMAIL_CTA_BASE_URL` debe coincidir con `NEXT_PUBLIC_APP_ORIGIN` |
| `MAX_HTTP_REQUEST_HEADER_SIZE`, `MAX_HTTP_FORM_POST_SIZE`, `MAX_SWALLOW_SIZE` | 8KB / 2MB / 2MB | Límites del hallazgo 3.4 |
| `ERROR_INCLUDE_STACKTRACE` / `_MESSAGE` / `_BINDING_ERRORS` | `never` | Sin stack traces ni mensajes internos en errores (hallazgo 3.6) |
| `SECURITY_LOG_LEVEL` | TRACE | Bajar a WARN en prod |
| `SPRING_PROFILES_ACTIVE` | (ninguno → `default`) | **`prod` en Railway** para logs JSON (logstash-logback-encoder) |
| `HEALTH_SHOW_DETAILS` | `when-authorized` | |

### Frontend

| Variable | Notas |
|---|---|
| `BACKEND_URL` | URL del backend, server-side only (route handlers + proxy) |
| `NEXT_PUBLIC_APP_ORIGIN` | Origen esperado para validar `Origin` en el proxy (defensa CSRF, THREAT_MODEL §2.5). En Railway: URL pública del frontend |

### GitHub Actions (secrets)

| Secret | Usado en | Estado |
|---|---|---|
| `PROD_DATABASE_URL` | `.github/workflows/backup.yml` | **Configurado y en producción** — backups diarios corriendo desde 2026-07-26 (usar `DATABASE_PUBLIC_URL` de Railway) |

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

## 6.1 Auditoría de seguridad multi-IA (2026-07-09 a 2026-07-13)

Sesión extensa de auditoría de seguridad con colaboración entre Claude,
Codex, y GLM-5.2 (vía Z Code), coordinada en `collab/` (ver
`collab/MAPA.md` para el índice completo, y `docs/THREAT_MODEL.md` para el
detalle exhaustivo de cada hallazgo). Resumen de lo más relevante:

**Hallazgo más grave, no anticipado**: `RegisterRequest` aceptá un campo
`rol` del cliente sin restricción — cualquiera podía registrarse como
`ADMIN`. Corregido: el rol se fuerza a `CLIENTE` siempre, server-side.

**Otros hallazgos críticos/altos confirmados y corregidos**:
- Excepción sin manejar en `JwtAuthFilter` ante un JWT malformado (DoS
  trivial) — ahora capturada, con límite de tamaño previo al parseo.
- Redis sin autenticación y puerto expuesto a todas las interfaces —
  ahora con `requirepass` y bind a `127.0.0.1`.
- `LoginRateLimitFilter` sin manejo de fallo de Redis (crash accidental, no
  política deliberada) — ahora fail-closed explícito (503, no 429) cuando
  Redis no responde.
- SSRF/path traversal en el proxy de Next.js (`new URL()` normalizaba
  `..`) — corregido con validación de segmentos de path.
- CSRF: backend tiene `.csrf(csrf -> csrf.disable())` por diseño (API
  stateless), pero el proxy de Next.js ahora valida `Origin` como defensa
  en profundidad.
- Condición de carrera en `Suscripcion` (creación concurrente de más de
  una suscripción activa) — cerrada con `@Version` (updates concurrentes)
  + índice único parcial en Postgres (creaciones concurrentes).
- Sin `@ControllerAdvice` global — agregado `GlobalExceptionHandler`,
  cubre validación, credenciales inválidas (mensaje genérico anti-
  enumeración), conflictos de integridad/optimistic locking, Redis caído.
- Política de password débil (`min=8`, sin chequeo de comunes) — ahora
  `min=12` sin regla de composición obligatoria (siguiendo NIST 800-63B,
  tras evaluar tres opiniones: Claude, Codex, y una consulta externa a
  ChatGPT) + validador `@NotCommonPassword` contra lista offline.

**Corrección importante de un hallazgo propio**: la severidad Crítica
original del hallazgo 1.2 (deserialización insegura vía Redis → RCE) se
degradó tras validación en sandbox real por el plugin "Codex Security" —
un harness Java confirmó que el `@class` no se interpreta como type hint
ejecutable con la config actual, y que además el único sink (`@Cacheable`
de `PlanService`) ya no existía tras el trabajo de paginación. Detalle
completo en `docs/THREAT_MODEL.md` §7.5.

**Regresiones de proceso detectadas y corregidas** (documentadas para no
repetirlas): un cambio de firma de método en la paginación rompió la
compilación de tests existentes porque nadie los corrió antes de dar el
cambio por bueno (ver `collab/aplicado/2026-07-12-fix-tests-paginacion.md`).
Desde entonces, toda tarea que cambie una firma pública debe verificar
tests, no solo que el código de producción compile.

## 7. Pendientes conocidos (con contexto de por qué)

Estado al 2026-08-01. Los pendientes históricos de abajo (migración a Boot 4
y deploy a Railway) están **cerrados**; quedan pocos ítems de verificación y
mejoras opcionales.

1. **Verificar `requirepass` de Redis en Railway prod** — único pendiente de
   seguridad activo; requiere consola de la plataforma (ver `THREAT_MODEL.md`
   §1.1). No es trabajo de código.

2. **Alinear Postgres dev/CI/prod** — Railway corre Postgres 18, el
   `docker-compose.yml` local sigue en 16. El backup de CI ya usa el cliente
   18; subir dev a 18 elimina la divergencia latente de comportamiento
   (ver `.github/workflows/backup.yml`).

3. **CSP completa con nonce (M2)** — mejora opcional de portafolio, diferida
   2026-08-01: ampliar `script-src`/`object-src`/`base-uri` con nonce.
   Requiere testeo real con los scripts inline de Next.

4. **Housekeeping de dependencias frontend** — bumps pendientes sin impacto
   en seguridad: `eslint-config-next 16.2.10→16.2.12`, `@types/react 19.2.18`,
   `@types/react-dom 19.2.4`, `@vitejs/plugin-react 6.0.5`.

5. **Checklist pre-repo-público** — revisar triggers de los workflows y qué
   secrets ve cada uno (`THREAT_MODEL.md` §4.4), y verificar el estado de
   commits colgantes tras el force-push del 07-07.

6. **Rotar credenciales filtradas en un chat de sesión** — API key de Gemini
   y un token `gho_` de GitHub expuestos en conversación (no en el repo). No
   urgente, pero vale la pena rotarlas.

7. **`DiagnosticHeaderController`** (`/api/v1/debug/headers`) — temporal del
   fix 2.2, gateado por `APP_DEBUG_HEADERS_ENABLED` (default false);
   candidato a eliminación cuando cumpla su función.

8. **User journeys sin verificar en producción** — viajes 3 y 4 de
   `docs/USER_JOURNEYS.md`; decidir si hace falta el endpoint de cambio de
   rol (hoy solo SQL directo).

9. **`gzip` no disponible por defecto en PowerShell de Windows** — el script
   `backup-local.ps1` cae a dejar el `.sql` sin comprimir si no detecta
   `gzip` en el PATH. No es un bug, es el comportamiento de respaldo
   diseñado a propósito, pero vale la pena instalar Git Bash/WSL si se
   quiere compresión real en los backups locales.

10. **`py`/`python` no quedaron en el PATH del sistema** tras la instalación
    vía winget en la máquina de desarrollo — se invocan con ruta completa
    (`%LOCALAPPDATA%\Microsoft\WindowsApps\py.exe`) o `py -m <módulo>`.
    Pendiente menor: arreglar el PATH vía GUI de variables de entorno si se
    quiere volver a necesitar Python/pip cómodamente.

### Cerrados desde la última actualización de este documento

- **Migración a Spring Boot 4.1.0** + springdoc 3.0.3 — completada
  2026-07-26 (PRs Dependabot #4/#7). Riesgos que se materializaron y se
  resolvieron: Spring Security 7 (`DaoAuthenticationProvider` con
  constructor explícito), `@MockBean`→`@MockitoBean`, `TestRestTemplate`
  movido a artefacto modular (`spring-boot-resttestclient`).
- **Deploy a Railway** — backend y frontend en producción con demos en vivo;
  CORS vía `ALLOWED_ORIGINS`, `JWT_SECRET` real, `BACKEND_URL` +
  `NEXT_PUBLIC_APP_ORIGIN`, `SPRING_PROFILES_ACTIVE=prod`, `DDL_AUTO=validate`
  (supuesto operativo: verificar en consola), backup `PROD_DATABASE_URL`
  activo.
- **Timeouts a clientes externos** — pendiente de la Fase 5 original:
  aplicados 2026-08-01 (fix M1, connect 3s/read 30s en Gemini/Anthropic/
  Resend). Circuit breakers hacia Postgres/Redis siguen fuera de scope para
  un proyecto de portafolio con una instancia.

---

## 9. Dashboard ADMIN (agregado 2026-07-12)

Trabajo cruzado entre GLM-5.2 (spec + componente frontend con Recharts,
`collab/propuestas/glm/dashboard-admin-charts.md`) y Codex (backend,
`GET /api/dashboard/admin/estadisticas`). Ambos coincidieron exacto en la
forma del JSON sin fricción de integración — confirmado por Claude leyendo
ambos lados.

**Decisiones de negocio tomadas**: "usuarios activos" filtra `activo=true`;
"ingresos estimados" suma `precio` de suscripciones en estado `ACTIVA`
únicamente (no histórico completo). Las tres categorías (roles, tipos de
plan, estados) siempre aparecen en la respuesta aunque el conteo sea cero,
para que el frontend no tenga que manejar campos faltantes.

**Estado**: integrado con el endpoint real desde 2026-07-13
(`AdminDashboardCharts.tsx` con datos reales, `recharts` agregado como
dependencia) — la nota "pendiente" de la propuesta original de GLM quedó
vieja.

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
- **Chat LLM con proveedor externo del tier gratis, blindado en vez de
  removido** (2026-08-01): el riesgo real (el usuario pega PII y el proveedor
  entrena con el tráfico) se cierra con filtro de emails previo al proveedor
  + aviso en UI + prompt endurecido, sin sacrificar la feature de portafolio.
  Kill-switch `APP_CHAT_ENABLED` para apagarlo en prod sin redeploy.
- **Email vía HTTP directo con `RestClient` (patrón `client/`), sin SDK**:
  cero dependencias nuevas para Resend — mismo criterio que el chat. El
  scheduler diario va gateado por config para que los tests no disparen
  correos reales.
- **`sessionStorage` para el chat, no `localStorage`**: la conversación
  sobrevive a un reload pero muere al cerrar la pestaña — coherente con una
  sesión de soporte puntual y más conservador en privacidad.
