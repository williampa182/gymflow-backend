# GymFlow — Threat Model y Análisis de Vulnerabilidades

> Documento vivo, complementario a `docs/ARCHITECTURE.md`. Generado en sesión
> de análisis de seguridad, 2026-07-09/10. No reemplaza un pentest real ni una
> auditoría de terceros — es un mapa de riesgos priorizado para guiar el
> trabajo de la Fase 5 (concurrencia y manejo de fallos) y lo que la excede.
>
> **Disclaimer honesto:** ningún sistema es "impenetrable". El objetivo de
> este documento es reducir la superficie de ataque y el impacto de cada
> vector hasta un nivel razonable para un proyecto de portafolio con datos
> reales de usuarios (passwords, emails), no alcanzar un estado teórico de
> cero riesgo que no existe en software real.

> **Nota de vigencia (2026-08-01)**: las secciones 1-5 de abajo son el mapa
> de amenazas ORIGINAL del análisis (2026-07-09/10) con su estado actualizado
> inline en cada hallazgo. **No queda ningún hallazgo de código abierto.**
> El detalle de cómo se auditó y corrigió cada cosa está en
> [`SECURITY_AUDIT_LOG.md`](SECURITY_AUDIT_LOG.md); el resumen ejecutivo con
> la tabla completa de hallazgos→estado está en
> [`SECURITY_CHANGELOG.md`](SECURITY_CHANGELOG.md). Resumen honesto:
>
> - §1.1 (Redis auth): resuelto en dev (07-11) y **verificado en prod (2026-08-04)** — AUTH aceptada contra el Redis de Railway con la password real (PING ok) y la app opera 200 (fail-closed no disparado).
> - §1.2: degradado a hardening preventivo y resuelto (cache removido).
> - §1.3 (CVE-2026-40976): no aplica (Boot 4.1.0 + `SecurityConfig` propio).
> - §2.2 (X-Forwarded-For): cerrado 07-24 vía `RemoteIpValve` nativo.
> - §2.3 (índice único): cerrado en dev y Railway prod (07-24).
> - npm audit: 0 vulnerabilidades (verificado 08-01).

## Cómo leer este documento

Cada hallazgo tiene:
- **Severidad**: Crítica / Alta / Media / Baja — combina impacto potencial y facilidad de explotación.
- **Esfuerzo de explotación**: qué necesita un atacante para lograrlo (conocimiento, acceso, herramientas).
- **Estado**: Confirmado (verificado contra código/config real) / Sin confirmar (hipótesis a validar) / Aceptado (riesgo conocido y asumido conscientemente).
- **Mitigación**: acción concreta, no genérica.

La sección 8 (`ARCHITECTURE.md`) del proyecto ya documenta el porqué de varias decisiones de diseño; este documento no las repite, las evalúa desde el lado ofensivo.

---

## 1. Críticos — atacan la integridad total del sistema o permiten RCE

### 1.1 Redis sin autenticación (`requirepass` no configurado)
- **Severidad:** Crítica
- **Estado:** RESUELTO y VERIFICADO EN PROD (2026-08-04) — `requirepass` + bind a `127.0.0.1` en `docker-compose.yml` (dev, 07-11); en Railway la password se inyecta vía `REDIS_PASSWORD` y se verificó con un PING autenticado contra el endpoint público del Redis de prod. No se expone a internet de forma anónima: el AUTH es obligatorio.
- **Impacto:** Es la raíz de la que cuelgan varios otros hallazgos de este documento (1.2, 3.1, 4.2). Sin autenticación, cualquiera con acceso de red al puerto 6379 puede leer/escribir/borrar todo el contenido de Redis sin restricción.
- **Explotación:** Si el puerto queda expuesto (mala segmentación de red en Railway, docker mal configurado), conexión directa con `redis-cli` sin credenciales.
- **Mitigación:** `requirepass` obligatorio en dev y prod (no "para cuando se despliegue"). Confirmar que Redis en Railway solo escucha en red interna, nunca expuesto a internet.

### 1.2 Deserialización insegura vía `GenericJackson2JsonRedisSerializer` → RCE
- **Severidad:** ~~Crítica~~ **DEGRADADA y RESUELTA — ver corrección en el audit log (2026-07-11): needs_review/hardening preventivo, RCE NO demostrado en el checkout actual**
- **Estado:** RESUELTO — `RedisCacheConfig`/`@EnableCaching` removidos (2026-07-11); no hay ningún `@Cacheable` activo ni sink de deserialización.
- **Impacto:** El serializer de caché incluía metadata de tipo (`@class`) en el JSON almacenado. Si un atacante podía escribir en Redis (ver 1.1), podía inyectar una entrada de caché con un `@class` apuntando a una gadget class del classpath, logrando **ejecución remota de código** al deserializar.
- **Explotación:** Requería 1.1 resuelto en contra (Redis escribible) + una gadget class presente en el classpath.
- **Mitigación:** Aplicada. Si algún día se reintroduce caché con Redis, revisar el modelo de serialización con un `ObjectMapper` con `PolymorphicTypeValidator` restrictivo.

### 1.3 CVE-2026-40976 — Spring Boot 4.0.0–4.0.5, bypass de autorización con Actuator
- **Severidad:** Crítica (CVSS 9.1)
- **Estado:** **NO APLICA (verificado 2026-07-23).** Rango afectado: Boot 4.0.0–4.0.5, corregido en 4.0.6+. GymFlow está en **4.1.0** (fuera de rango) y además tiene `SecurityConfig` propio (la CVE solo aplica con el filter chain por default).
- **Impacto:** En apps servlet-based con el filter chain por defecto y Actuator, la configuración de seguridad por defecto falla en aplicar autorización — podía exponer endpoints sin autenticación.
- **Mitigación:** Ninguna requerida (versión parchada + config propia). Si se vuelve a migrar de Boot, verificar el rango del advisory antes.

### 1.4 CVE-2026-40972 — timing attack contra DevTools remote secret (Boot, misma migración)
- **Severidad:** Alta (CVSS 7.5)
- **Estado:** No aplica — `spring-boot-devtools` con `scope=runtime` + `optional=true`, excluido del JAR de producción automáticamente.
- **Impacto:** Atacante en la misma red podía explotar timing contra el remote secret de DevTools, con potencial de RCE en casos extremos.
- **Mitigación:** Confirmar que DevTools nunca está activo fuera de la máquina de desarrollo local (config estándar ya lo garantiza).

---

## 2. Altos — comprometen autenticación, autorización o disponibilidad significativa

### 2.1 Fail-closed en `LoginRateLimitFilter` depende de que Redis esté protegido (ver 1.1)
- **Severidad:** Alta
- **Estado:** Decisión de diseño tomada (fail-closed) — con 1.1 resuelto, el escenario original quedó mitigado. Un fallo de Redis responde 503 explícito (no crash accidental).
- **Impacto:** Con la política fail-closed, si un atacante logra que Redis deje de responder, tumba el login de **todo el sistema**. El 503 explícito hace el estado distinguible del 429.
- **Mitigación:** Aplicada (manejo de `RedisConnectionFailureException` en `LoginRateLimitFilter`). Mantener Redis protegido (1.1).

### 2.2 `X-Forwarded-For` sin validar contra proxy confiable
- **Severidad:** Alta
- **Estado:** **RESUELTO (2026-07-24).**
- **Impacto:** Si el filtro de rate limiting confiaba ciegamente en el header `X-Forwarded-For`, un atacante podía mandar un valor distinto en cada request y hacer que cada intento cuente como una IP diferente — bypass total del rate limit de login.
- **Mitigación:** **Aplicado** vía `RemoteIpValve` nativo de Tomcat (`server.forward-headers-strategy: native` + `server.tomcat.internal-proxies` en `application.yaml`): resuelve la IP real leyendo `X-Forwarded-For` de derecha a izquierda y descartando los saltos internos (verificada la posición correcta contra soporte de Railway). `LoginRateLimitFilter.obtenerIpCliente()` ve la IP ya resuelta.

### 2.3 Ausencia de unique constraint a nivel de base de datos para suscripción activa por usuario
- **Severidad:** Alta
- **Estado:** **RESUELTO (dev 2026-07-10, Railway prod verificado 2026-07-24).**
- **Impacto:** Si el chequeo "¿ya tiene una suscripción activa?" y el `save()` no eran atómicos (TOCTOU), requests concurrentes podían crear múltiples suscripciones activas para el mismo usuario.
- **Mitigación:** Constraint a nivel de Postgres — índice único parcial `uq_suscripcion_activa_por_usuario` (`scripts/migrations/001_unique_suscripcion_activa.sql`) + `@Version` (optimistic locking) cubriendo ambas mitades de la carrera.

### 2.4 JWT sin revocación — `activo=false` no invalida tokens ya emitidos
- **Severidad:** Alta
- **Estado:** Aceptado como limitación conocida (documentada en `ARCHITECTURE.md` §8).
- **Impacto:** Desactivar un usuario comprometido no tiene efecto hasta que su JWT expire (hasta 24h). "Logout" solo limpia cookies; el token sigue válido en el backend.
- **Mitigación (proporcional al proyecto, no "nivel banco"):** revalidar `activo` contra la base en `JwtAuthFilter` en cada request (costo: una consulta extra, mitigable con cache corto), o reducir `JWT_EXPIRATION` y aceptar la ventana como trade-off documentado.

### 2.5 CSRF vía la cookie httpOnly + proxy genérico de Next.js
- **Severidad:** Alta
- **Estado:** **RESUELTO (defensa en profundidad).**
- **Impacto:** La cookie `token` se manda automáticamente en cada request al dominio; el proxy convertía la cookie en `Authorization` header válido.
- **Mitigación:** **Aplicada.** El proxy `[...path]/route.ts` valida `Origin` contra el origen esperado (`NEXT_PUBLIC_APP_ORIGIN`) en todo método que muta estado. Backend con CSRF deshabilitado por diseño (API stateless con JWT). Setear `NEXT_PUBLIC_APP_ORIGIN` en Railway (checklist de deploy).

### 2.6 SSRF / passthrough no sanitizado en el proxy `[...path]/route.ts`
- **Severidad:** Alta
- **Estado:** **RESUELTO.**
- **Impacto:** `new URL()` normaliza `..`, permitiendo que el path escape del prefijo `/api/` (ej. `/api/backend/../actuator/prometheus`).
- **Mitigación:** **Aplicada.** `esPathSeguro()` rechaza segmentos `.`, `..`, vacíos, o con `/`/`\` antes de construir la URL destino.

### 2.7 DoS de parsing JWT sin autenticación previa (Billion Hashes / Compression DoS)
- **Severidad:** Alta
- **Estado:** **RESUELTO.**
- **Impacto:** Tokens malformados forzaban el parser de JJWT completo con excepción sin manejar en cada request.
- **Mitigación:** **Aplicada.** Try/catch alrededor del parseo (`JwtException`, `IllegalArgumentException`, `UsernameNotFoundException`) + límite de tamaño (2048 chars) antes de invocar el parser.

---

## 3. Medios — amplifican otros ataques o exponen información

### 3.1 Enumeración de usuarios vía mensajes de error o timing en login/registro
- **Severidad:** Media
- **Estado:** **RESUELTO.**
- **Impacto:** Si `AuthService` distingue "usuario no encontrado" de "password incorrecta" (por mensaje o por tiempo, ya que BCrypt agrega latencia medible solo cuando el usuario existe), un atacante puede enumerar cuentas válidas.
- **Mitigación:** **Aplicada (Fix A + Fix B, 2026-07-19).** Mensaje idéntico en login; `TimingSafeAuthenticationProvider` ejecuta BCrypt dummy cuando el usuario no existe; rehash automático de hashes con cost desactualizado en login (cerró el canal que los hashes viejos a cost 10 reabrían). Registro con `reveal-email-on-register=false` por default (decisión documentada 07-16).

### 3.2 Mass assignment vía DTOs sin whitelist explícita de campos
- **Severidad:** Media
- **Estado:** **Auditado y cubierto por test de regresión (2026-07-25).**
- **Impacto:** Si el binding de JSON a DTO no excluía explícitamente campos como `id`, `activo`, `creadoEn`, `fechaFin`, un cliente podía intentar sobrescribirlos.
- **Mitigación:** DTOs de request con whitelist explícita; test `AuthRegisterPrivilegeEscalationRegressionTest` + cobertura de mass assignment.

### 3.3 Endpoints sin paginación — resource exhaustion y scraping
- **Severidad:** Media
- **Estado:** **RESUELTO (2026-07-11).**
- **Mitigación:** `Pageable` en todos los endpoints de listado (`planes`, `usuarios`, `suscripciones`, por usuario).

### 3.4 Sin límite de tamaño de request body
- **Severidad:** Media
- **Estado:** **RESUELTO (2026-07-11).**
- **Mitigación:** `max-http-request-header-size` (8KB), `max-http-form-post-size`/`max-swallow-size` (2MB) en `application.yaml`. Nota: no hay límite duro para bodies JSON grandes antes del parseo — documentado en el yaml como límite conocido.

### 3.5 Registro sin política de password ni control anti-bot
- **Severidad:** Media
- **Estado:** **RESUELTO (2026-07-12).**
- **Mitigación:** `RegisterRequest` con `@Size(min=12,max=128)` + `@NotCommonPassword` (lista offline; decisión NIST 800-63B tras tres opiniones). `/api/auth/register` cubierto por el rate limit compartido (10 intentos/min por IP, key separada de login).

### 3.6 Fingerprinting vía headers de servidor y páginas de error
- **Severidad:** Media
- **Estado:** **RESUELTO (2026-07-11).**
- **Mitigación:** `include-stacktrace: never`, `server-header: ""`, whitelabel deshabilitado — explícito independientemente del perfil activo. Setear `SPRING_PROFILES_ACTIVE=prod` en Railway (checklist).

### 3.7 Falta de headers de seguridad HTTP (CSP, X-Frame-Options, HSTS)
- **Severidad:** Media
- **Estado:** **RESUELTO (frontend, con test de regresión 07-25). Mejora pendiente opcional: CSP completa.**
- **Impacto:** Sin `X-Frame-Options`/CSP, el frontend era embebible en iframe ajeno (clickjacking). Sin HSTS, la primera conexión de cada usuario es vulnerable a downgrade.
- **Mitigación:** **Aplicada** en `next.config.ts`: `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`, HSTS (2 años), CSP con `frame-ancestors 'none'`. **Diferido (M2, 2026-08-01):** ampliar CSP con `script-src`/`object-src`/`base-uri` vía nonce — requiere testeo real con los scripts inline de Next; candidato a mejora futura del portafolio.

---

## 4. Bajos / operacionales — no explotables directamente pero aumentan el impacto de otros hallazgos

### 4.1 `BCrypt` con cost factor por defecto sin verificar
- **Severidad:** Baja individualmente, alta en combinación con una filtración de datos
- **Estado:** **RESUELTO.** `BCRYPT_STRENGTH = 12` explícito en `SecurityConfig` (2026-07-11), con rehash automático en login para hashes viejos (evita reabrir el canal de timing del Fix B).

### 4.2 Log injection vía `X-Request-ID` si se acepta del cliente sin sanitizar
- **Severidad:** Baja
- **Estado:** **RESUELTO.** `CorrelationIdFilter` gestiona el header con sanitización (2026-07-11).

### 4.3 Supply chain — actualizaciones de Dependabot mergeadas sin revisión de changelog
- **Severidad:** Baja (mitigado parcialmente por buena práctica ya existente de pausar PRs riesgosos)
- **Estado:** Proceso vigente — revisar changelog real antes de mergear, incluso en updates menores (regla reforzada tras el incidente del PR de Boot 4).

### 4.4 GitHub Actions — workflows con acceso a `PROD_DATABASE_URL`
- **Severidad:** Baja mientras el repo sea privado; sube a Media/Alta si el repo se hace público
- **Estado:** **CHECKLIST EJECUTADO (2026-08-07)** — verificado contra `.github/workflows/` de backend y frontend:
  - `backend-ci.yml` y `frontend-ci.yml`: triggers `push` + `pull_request` (NUNCA `pull_request_target`), `permissions: contents: read`, **sin secrets**. En repo público, un fork PR correría sin secrets y con token read-only.
  - `backup.yml`: triggers `schedule` (cron 07:00 UTC) + `workflow_dispatch` — imposible de disparar desde un PR; `contents: read`; único workflow con secret (`PROD_DATABASE_URL`), solo alcanzable por cron/manual.
  - `dependabot.yml`: solo abre PRs revisables, nunca auto-mergea.
  - **Conclusión: sin vectores de secrets hacia código no confiable; sin cambios requeridos.**

### 4.5 Credenciales de Docker Compose hardcodeadas en el repo
- **Severidad:** Baja (son credenciales de dev sin valor real)
- **Estado:** **Documentado** — comentario "DEV ONLY" explícito en `docker-compose.yml` (2026-07-11) para evitar repetir el incidente del JWT secret purgado.

### 4.6 Contenedor backend potencialmente corriendo como root
- **Severidad:** ~~Baja individualmente, multiplica el daño de 1.2 si se combina~~ **RESUELTO (2026-07-13)**
- **Mitigación:** Dockerfile propio con usuario `gymflow` no-root desde el diseño inicial, verificado en runtime (`actuator/health` respondiendo dentro del contenedor).

---

## 5. Priorización sugerida (severidad × esfuerzo de explotación)

> Tabla ORIGINAL del análisis (2026-07-09/10), conservada como referencia
> histórica del orden en que se trabajó. Estado real actual: ver tabla del
> [`SECURITY_CHANGELOG.md`](SECURITY_CHANGELOG.md) — todo lo de código está
> cerrado.

| # | Hallazgo | Severidad | Esfuerzo de explotación | Orden sugerido |
|---|---|---|---|---|
| 1.1 | Redis sin autenticación | Crítica | Bajo (si expuesto) | 1 |
| 1.2 | Deserialización insegura vía Redis | Crítica | Medio (depende de 1.1) | 2 |
| 1.3 | CVE-2026-40976 (Boot 4.x, Actuator) | Crítica | Bajo (solo con la migración) | 3 — antes de mergear PR #4 |
| 2.3 | Falta de unique constraint en Suscripcion | Alta | Bajo | 4 |
| 2.2 | X-Forwarded-For sin validar | Alta | Bajo | 5 |
| 2.6 | SSRF en proxy Next.js | Alta | Medio | 6 |
| 2.4 | JWT sin revocación | Alta | Medio (requiere robo previo de token) | 7 |
| 2.1 | Fail-closed login (post-1.1) | Alta | — (depende de 1.1) | 8 |
| 2.5 | CSRF vía proxy | Alta | Medio | 9 |
| 2.7 | DoS parsing JWT | Alta | Medio | 10 |
| 3.x | Hallazgos medios | Media | Variable | 11–17 |
| 4.x | Hallazgos operacionales | Baja | Variable | 18–23 |

---

## 6. Lo que este documento NO cubre (límites honestos del análisis)

- Configuración real de red/firewall de Railway — no verificable desde las sesiones de agentes.
- Resultado de un pentest o escaneo automatizado real contra el sistema desplegado.
- Vulnerabilidades de día cero no publicadas.
- Ingeniería social, phishing dirigido, o compromiso físico de la máquina de desarrollo.

Este documento es un punto de partida priorizado, no un certificado de seguridad.

---

## 7. Historial de auditoría

La bitácora completa de proceso — incluyendo la reclasificación honesta del
hallazgo 1.2 (evidencia de sandbox de Codex Security), la verificación de
runtime, la auditoría de pendientes del 23/07 y la revisión de features
nuevas del 01/08 — vive en [`SECURITY_AUDIT_LOG.md`](SECURITY_AUDIT_LOG.md).
El resumen ejecutivo con la tabla hallazgos→estado y quién hizo qué está en
[`SECURITY_CHANGELOG.md`](SECURITY_CHANGELOG.md).

---

## 8. Fase 2 — registro con rol: decisiones de seguridad (2026-08-02)

Registro público con auto-rol CLIENTE/ENTRENADOR + bootstrap del primer
admin. Decisiones y su racional:

- **Whitelist estricta en `AuthService.registrar`**: solo `CLIENTE` y
  `ENTRENADOR` pasan del body; cualquier otro valor — en particular
  `"ADMIN"` — o la ausencia del campo se degradan a CLIENTE (`resolverRolRegistro`).
  Mantiene el fix de escalada §7.0 del security deep dive: el test de
  regresión `AuthRegisterPrivilegeEscalationRegressionTest` sigue enviando
  `"rol":"ADMIN"` y esperando `200 + CLIENTE`.
- **Sin `@Pattern` en `RegisterRequest`** (a propósito): validar con 400 el
  rol no whitelistado cambiaría el contrato `200 + CLIENTE` que el test de
  regresión verifica. La whitelist vive en el service, no en la validación
  del DTO.
- **Bootstrap del primer admin**: si `countByRolAndActivo(ADMIN, true) == 0`,
  el primer usuario registrado nace ADMIN (self-provisioning del panel en un
  despliegue desde cero). Una vez que existe al menos un ADMIN activo, la
  whitelist manda y nadie más auto-escala. Esto NO reabre el vector §7.0: la
  condición es exclusivamente "no hay administradores en el sistema", nunca
  algo controlable por el payload del atacante (un atacante no puede crear
  un "cero admins" — y si el sistema no tiene admins, el rol por default es
  justamente lo que permite administrarlo).
- **El último admin activo no es degradable/desactivable** (ya existía,
  `UsuarioService` con bloqueo pesimista FOR UPDATE). Bootstrap + guarda
  juntos garantizan: el sistema nunca se queda sin ADMIN, y el registro
  nunca crea uno fuera de la regla bootstrap.
- **`GET /api/auth/registro-estado`** (público): devuelve solo el booleano
  `primerRegistroSeraAdmin` — no revela datos de usuarios ni emails.
- **Tests**: `AuthServiceTest` (whitelist, degradación, bootstrap),
  `AuthRegisterPrivilegeEscalationRegressionTest` y
  `AccessDeniedReturns403RegressionTest` siembran un admin activo primero
  para que la regla "registro → CLIENTE" sea determinística también en una
  base recién creada (CI corre con Postgres efímero por job).

---

## 9. Fase 4 — rutinas y acompañamiento: decisiones de seguridad (2026-08-02)

Dominio entrenador↔cliente: los ENTRENADORES crean rutinas (con ejercicios),
se asignan como acompañantes de CLIENTES cuyo plan ACTIVO incluye entrenador
personal, y les asignan rutinas. Decisiones y su racional:

- **La elegibilidad es una regla derivada, no una decisión manual**:
  `EntrenadorService.listarClientesElegibles` calcula en el momento quién
  califica (suscripción ACTIVA + plan activo con `incluyeEntrenadorPersonal`).
  Un entrenador NO puede acompañar a un cliente sin ese plan: `asignarme`
  re-verifica la condición (check-then-act) y rechaza con
  "el plan del cliente no incluye entrenador personal" (400). Si el plan
  deja de incluir el beneficio, el cliente desaparece de la lista elegible
  pero conserva su acompañamiento/rutinas históricas hasta que el entrenador
  las cancele o desactive — decisión deliberada (no romper el historial).
- **Ownership estricto de rutinas**: toda operación de escritura sobre una
  rutina pasa por `findByIdAndEntrenadorId` (RutinaService). Un entrenador
  no puede editar, desactivar o asignar la rutina de otro; el error
  "solo el entrenador creador puede modificar esta rutina" se mapea a 403
  en `inferirStatus` (nunca 404 — el mensaje ya identifica la acción, no
  revela más). La identidad siempre sale de `authentication.getName()`,
  jamás de un id del body (mismo patrón que Fase 3).
- **Asignación de rutinas restringida a acompañados**: `asignar` exige que
  el cliente tenga una asignación ACTIVA cuyo entrenador sea el creador de
  la rutina. Un cliente no puede recibir rutinas de un desconocido.
- **Cliente de solo lectura**: `GET /api/rutinas/mias` filtra
  `rutina.activo = true` en la query (defensa en profundidad) y devuelve
  SOLO las rutinas asignadas al cliente autenticado. `@PreAuthorize` por rol
  en ambos lados (ENTRENADOR vs CLIENTE) con barrera real en Spring Security.
- **Un solo acompañante ACTIVO por cliente**: chequeo check-then-act +
  índice único parcial `uq_acompanante_activo_por_cliente` (migración 004)
  como red de seguridad a nivel BD para el caso concurrente, mismo patrón
  que la migración 001 para suscripciones ACTIVA. La cancelación solo puede
  ejecutarla el entrenador de esa asignación (`findByIdAndEntrenadorId`),
  y usa `@Version` contra cancelaciones concurrentes (409 vía
  OptimisticLockingFailureException).
- **Sin mass assignment**: los request DTOs nuevos (`RutinaRequestDTO`,
  `EjercicioRequestDTO`) no declaran `entrenadorId`, `rutinaId`, `orden` ni
  `activo` — el servidor los controla (re-enlace en `RutinaService`, orden
  derivado del índice, estado vía acción explícita de desactivar). El
  patrón de verificación sigue el de `DtoMassAssignmentRegressionTest`.
- **Nunca se expone el email de los clientes** en `ClienteElegibleDTO`
  (solo id + nombre + marca de acompañamiento + id de asignación propia).
- **Solo el entrenador de la asignación puede cancelarla** (403 si no es
  el suyo) y `EntrenadorService.asignarme` rechaza la auto-asignación
  ("no podés ser tu propio acompañante", 400).
- **Tests**: `RutinaServiceTest` (ownership, rutina inactiva, cliente no
  acompañado, duplicado, idempotencia del quitar), `EntrenadorServiceTest`
  (elegibilidad calculada, auto-asignación, plan sin beneficio, duplicado,
  ownership de cancelación), 2 controller tests (identidad del principal),
  e `EntrenadorRutinaIntegrationTest` (flujo end-to-end contra Postgres
  real: registro → plan → suscripción → acompañamiento → rutina →
  asignación → lectura cliente + 409s duplicados + 403 de rol cruzado).

### Iteración 2 (2026-08-03): quitar rutina en la UI + historial de acompañamientos

Ampliación aprobada por William tras la fase 4 (propuesta
`collab/historial/propuestas-cerradas/2026-08-03-fase4-iteracion2-quitar-rutina-y-historial.md`):

- **`asignados` en `RutinaResponseDTO` solo para ENTRENADOR**: `GET
  /api/rutinas` (vista ENTRENADOR) puebla la lista de clientes asignados a
  cada rutina; `GET /api/rutinas/mias` (vista CLIENTE) la devuelve siempre
  vacía (`RutinaResponseDTO.from(rutina)` sin sobrecarga). El CLIENTE no
  puede deducir qué otros clientes comparten una rutina — no filtra
  identidades ajenas.
- **Sin N+1 en ninguna de las dos listas**: `RutinaService.listarMias`
  arma el mapa `rutinaId → asignados` con una sola query de
  `AsignacionRutinaRepository.findByRutinaEntrenadorId` (con `join fetch
  ar.cliente`, sin N+1 de nombres) y `EntrenadorService.listarClientesElegibles`
  sigue usando una sola query de suscripciones ACTIVAS para todo el batch
  de candidatos (`findByEstadoAndUsuarioIdIn`) en vez de una por cliente
  (los chequeos per-cliente de `asignarme` se conservan, solo cambia la
  lectura). Sin cache: el costo es 2 queries planas por petición.
- **`GET /api/entrenador/mi-historial`** (CLIENTE, `@PreAuthorize`): todas
  las asignaciones del cliente autenticado, más reciente primero,
  `findByClienteIdOrderByAsignadoEnDesc`. Devuelve `HistorialAcompanamientoDTO`
  (entrenadorNombre + activa + asignadoEn) — **nunca emails**. La identidad
  sale de `authentication.getName()` (mismo patrón que Fase 3). El
  frontend trata el fallo del endpoint como silencioso (la sección es
  informativa, no bloquea la página).
- **Quitar rutina por UI**: `DELETE /rutinas/{id}/asignar/{clienteId}`
  existente (idempotente, ownership estricto) ahora es alcanzable desde dos
  lugares con una sola fuente de datos (`rutinas[].asignados`): chip en la
  card de la rutina y sub-fila del cliente acompañado.
- **Tests**: `RutinaServiceTest` (asignados poblados con tuple
  `(rutinaId, ClienteAsignadoDTO)`), `EntrenadorServiceTest` (batch
  `findByEstadoAndUsuarioIdIn` + nunca `findByUsuarioIdAndEstado` por
  cliente; historial completo del cliente), `EntrenadorControllerTest`
  (historial usa el email del principal), `EntrenadorRutinaIntegrationTest`
  (asignados en la vista ENTRENADOR, historial tras cancelar + volver a
  acompañar, quitar rutina 204). Frontend: `rutinas-role.test.tsx` 8→11
  (quitar desde card, quitar desde sub-fila, historial CLIENTE con
  ACTIVO/CANCELADO).
