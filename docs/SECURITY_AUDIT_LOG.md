# GymFlow — Bitácora de auditorías de seguridad

> Historial completo de proceso: qué se auditó, qué se encontró, qué se
> corrigió y cuándo. Este archivo NO es el threat model (eso es
> [`THREAT_MODEL.md`](THREAT_MODEL.md)) — es la evidencia detrás de la
> tabla de estados del [`SECURITY_CHANGELOG.md`](SECURITY_CHANGELOG.md).
> Última actualización: 2026-08-01.

---

## 2026-07-09/10 — Auditoría de código real (primera sesión)

> Resumen ejecutivo: [`SECURITY_CHANGELOG.md`](SECURITY_CHANGELOG.md).

### 7.0 Hallazgo nuevo, no anticipado — el más grave de toda la auditoría

**Escalada de privilegios en registro público.** `RegisterRequest` exponía un
campo `rol` obligatorio, y `AuthService.registrar()` lo usaba tal cual
(`.rol(request.getRol())`) sin ninguna restricción server-side. Cualquiera
podía registrarse como ADMIN con un solo `POST /api/auth/register` sin
autenticación previa. Más grave que varios hallazgos ya marcados como
Críticos porque no depende de ninguna infraestructura mal configurada — es
un bug de lógica de negocio puro, explotable con cero esfuerzo.
**Estado: CONFIRMADO y CORREGIDO.** `RegisterRequest` ya no acepta `rol`;
`AuthService` fuerza `Rol.CLIENTE` siempre.

### 7.1 Hallazgos confirmados y corregidos en esta sesión

| # | Hallazgo | Confirmación | Fix aplicado |
|---|---|---|---|
| 1.1 | Redis sin auth | `docker-compose.yml`: sin `requirepass`, puerto `6379:6379` expuesto al host. `application.yaml`: sin `spring.data.redis.password`. | No corregido en código (es config de infra) — **acción pendiente para William**: agregar `requirepass` y no exponer el puerto al host. |
| 1.2 | Deserialización insegura Redis | `RedisCacheConfig` usa `GenericJackson2JsonRedisSerializer` con `findAndAddModules()`, sin `PolymorphicTypeValidator` restrictivo. | No corregido — depende de resolver 1.1 primero; requiere endurecer el `ObjectMapper` en una sesión dedicada. |
| 1.3 | CVE-2026-40976 (Boot 4.x) | `pom.xml`: hoy sin `spring-boot-health`, con `spring-boot-starter-actuator` — cumple 3/4 condiciones de la CVE. Aplica a la migración pendiente (PR #4). | No corregido (aún en Boot 3.5.16) — **bloqueante documentado**: no mergear PR #4 sin agregar `spring-boot-health` o revisar el advisory. |
| 2.1 | Fail-closed rate limit sin manejo de fallo de Redis | `LoginRateLimitFilter` no envolvía la llamada a Redis en try/catch — un fallo de Redis producía una excepción sin manejar, no una política deliberada. | **CORREGIDO.** Ahora captura `RedisConnectionFailureException` y responde 503 explícito, distinguible de 429. |
| 2.2 | X-Forwarded-For sin validar | `obtenerIpCliente()` confía en el header tal cual, sin validar el proxy de origen. | **Documentado en código, no resuelto** — requiere conocer el rango de IP del proxy de Railway, que no es siempre estático en PaaS. Mitigación parcial: asegurar que el backend no sea alcanzable directo desde internet. |
| 2.3 | Sin unique constraint para suscripción activa | `SuscripcionService.crear()`: check-then-act clásico (`findByUsuarioIdAndEstado` + `save()` no atómico). Confirmado, sin `@Version` en `Suscripcion`. | **CORREGIDO POR COMPLETO (sesión 3).** `@Version` en la entidad + índice único parcial `uq_suscripcion_activa_por_usuario` aplicado en dev (`scripts/migrations/001_unique_suscripcion_activa.sql`, verificado sin duplicados previos). Ambos casos de la carrera (update concurrente, creación concurrente de dos filas activas) cerrados. **Pendiente:** aplicar en cualquier otro entorno (staging/prod) — migración manual, no se propaga sola. |
| 2.5 | CSRF | `SecurityConfig`: `.csrf(csrf -> csrf.disable())` confirmado — sin ninguna defensa CSRF en el backend. El proxy de Next.js es el punto de exposición real. | **CORREGIDO (defensa en profundidad).** El proxy `[...path]/route.ts` ahora valida `Origin` contra el origen esperado en todo método que muta estado. |
| 2.6 | SSRF / path traversal en proxy Next.js | Confirmado: `new URL()` normaliza `..`, permitiendo que el path escape del prefijo `/api/` (ej. `/api/backend/../actuator/prometheus`). | **CORREGIDO.** `esPathSeguro()` rechaza segmentos `.`, `..`, vacíos, o con `/`/`\` antes de construir la URL destino. |
| 2.7 | DoS de parsing JWT sin auth previa | Confirmado como bug real: `JwtAuthFilter` llamaba a `jwtUtil.extraerUsername()` sin try/catch — cualquier `Authorization: Bearer <basura>` producía una excepción sin manejar en cada request. | **CORREGIDO.** Try/catch alrededor del parseo (`JwtException`, `IllegalArgumentException`, `UsernameNotFoundException`), más límite de tamaño (2048 chars) antes de invocar el parser. |
| 3.7 | Sin headers de seguridad HTTP | Confirmado: `next.config.ts` no tenía ninguna configuración de headers. | **CORREGIDO.** Agregado `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, `Strict-Transport-Security`, `Content-Security-Policy` (frame-ancestors 'none'). |
| — | Sin `@ControllerAdvice` global | Confirmado: no existía ningún manejo centralizado de excepciones; `RuntimeException` genéricas de los servicios salían como 500 sin status code correcto. | **CORREGIDO.** `GlobalExceptionHandler` nuevo cubre validación, credenciales inválidas (mensaje genérico anti-enumeración), conflictos de integridad/optimistic locking, y Redis caído. |

### 7.2 Correcciones a hallazgos previos (más matizados de lo que se pensó)

- **3.5 (registro sin política de password/anti-bot):** parcialmente incorrecto. `RegisterRequest` sí valida `@Size(min = 8)` en el password, y `/api/auth/register` SÍ está cubierto por el mismo `LoginRateLimitFilter` (10 intentos/min). Sigue faltando: complejidad de password (solo longitud) y CAPTCHA — pero no es la ausencia total que se había asumido. *(Luego corregido por completo el 07-12: min=12 + `@NotCommonPassword`.)*
- **4.6 (contenedor root):** no aplica tal como estaba escrito — el repo no tenía `Dockerfile` propio; el deploy en Railway probablemente usa build automático (Nixpacks). *(Luego resuelto el 07-13 con `Dockerfile` propio y usuario no-root.)*

### 7.4 Verificación en runtime (sesión 3)

Probado contra el backend real corriendo en local (`.\mvnw.cmd spring-boot:run`), no solo lectura de código:

- **Escalada de privilegios (7.0):** `POST /api/auth/register` con `"rol":"ADMIN"` devuelve un usuario con `rol: CLIENTE` en la respuesta. Confirmado corregido en runtime.
- **2.7 (DoS/excepción sin manejar en JwtAuthFilter):** `GET /api/planes` con `Authorization: Bearer esto-no-es-un-jwt-valido` devuelve **403 Forbidden** limpio. Antes del fix esto hubiera propagado una excepción de JJWT sin capturar.
- Nota aparte: los stack traces de `AuthorizationDeniedException` visibles en el log con `SECURITY_LOG_LEVEL=TRACE` son el mecanismo interno normal de Spring Security (control de flujo vía excepciones), no relacionados con el bug corregido — no confundir con una regresión.

---

## 2026-07-11 — Hardening de Redis + corrección 1.2 (Codex Security)

**1.1 (Redis sin auth): CONFIRMADO con evidencia de runtime real**, no solo
lectura de config. Codex levantó Redis con el `docker-compose.yml` actual y
confirmó con `redis-cli PING/SET/GET` que acepta comandos sin AUTH, y que
Docker publica el puerto en `0.0.0.0:6379` (todas las interfaces), no solo
loopback — peor de lo que teníamos documentado. Fix: `requirepass` + bind a
`127.0.0.1` en `docker-compose.yml`, password en `application.yaml`.

**1.2 (deserialización → RCE): la severidad Crítica estaba sobrestimada.**
Dos cosas que se nos escaparon a Claude y a William:
1. Un harness Java real probó que, con el constructor actual
   (`new GenericJackson2JsonRedisSerializer(objectMapper)`, sin
   `setDefaultTyping`), el campo `@class` de un payload NO se interpreta como
   type hint ejecutable — se deserializa como `LinkedHashMap` normal, dato
   inerte. La cadena de RCE documentada originalmente asumía default typing
   activo, que no está activo con esta config específica.
2. Más importante todavía: cuando se aplicó la propuesta 3.3 (paginación),
   se sacó el `@Cacheable` de `PlanService.listar()` como parte de ese mismo
   cambio. Sin ningún `@Cacheable` activo en el proyecto, **no hay ningún
   punto donde el backend lea valores de Redis como caché y los deserialice**
   — el sink que la cadena de ataque necesitaba ya no existe en el código
   actual.

**Reclasificación honesta:** 1.2 pasa de "Crítica, confirmada en teoría" a
"needs_review / hardening preventivo" — el riesgo real hoy es que alguien
reintroduzca `@Cacheable` en el futuro sin revisar el modelo de serialización.
`RedisCacheConfig` y `@EnableCaching` fueron removidos por completo
(superficie muerta = riesgo futuro innecesario); Redis se conserva para el
rate limiting que sí lo usa. Referencia:
`collab/historial/propuestas-cerradas/codex-security-triage-redis-1.1-1.2.md`.

---

## 2026-07-23 — Auditoría de pendientes reales (verificación exhaustiva)

Verificación de TODO lo listado como "pendiente" en `collab/MAPA.md` y en
THREAT_MODEL contra el código real, git log, y — para la CVE — el advisory
oficial. Resultado: la gran mayoría ya estaba resuelto, en varios casos hace
más de una semana, sin documentar.

- **§1.3 (CVE-2026-40976) — verificado como NO aplicable.** El rango
  afectado es Boot 4.0.0–4.0.5, corregido en 4.0.6+. GymFlow está en 4.1.0
  (migración ya hecha). Además la CVE solo aplica a apps con el filter chain
  por default sin `SecurityConfig` propio — GymFlow lo tiene.
- **§1.4 (DevTools) — riesgo bajo confirmado, no bloqueante.**
  `spring-boot-devtools` con `scope=runtime` + `optional=true` se excluye
  del JAR de producción automáticamente.
- **Security Deep Dive (GLM-5.2): los 12/12 hallazgos están cerrados**, no 6:
  §2 (Swagger) y §3 (login sin límite)…§11 (mensaje 409 hardcodeado) — los
  cinco primeros en `af0d0f6` (William, 07-15) y §11 en `f81f1b8` (William,
  07-21), ambos sin documentar hasta esta verificación.
- **Propuestas de Codex (3.3-4.5, 1.1/1.2): todas aplicadas** (verificadas
  archivo + línea).
- **3 vulnerabilidades HIGH en npm (`next`/`postcss`/`sharp`)** — único
  hallazgo nuevo genuino. Fix aplicado los días siguientes (ver sección
  siguiente).

**Lección de proceso (tercera vez que se repite):** William aplica fixes
directo (sin pasar por `collab/`), y quedan invisibles hasta que una
verificación de disco los encuentra. Una línea en el handoff de la sesión
alcanza.

---

## 2026-07-23/24 — Cierre del ítem npm (3 HIGH) + §2.2 + §2.3

- **npm audit (3 HIGH):** `932da34` (07-23) bumpeó `next` a 16.2.11
  (resolvía 9 advisories: SSRF en rewrites/Server Actions, DoS en Image
  Optimization, bypass de middleware/proxy con Turbopack, exposición de
  Server Functions); `cebff48` (07-24) forzó con `overrides`
  `postcss@8.5.22`/`sharp@0.35.3` (vendorizadas dentro de next, no
  resolubles con el bump solo). `next/image` no se usa, el pipeline de
  sharp no corre en runtime. Verificado en 0 vulnerabilidades el 07-27 y
  de nuevo el 08-01 (ver `collab/historial/aplicado/2026-08-01-cierre-npm-audit.md`).
- **§2.2 (X-Forwarded-For): CERRADO (07-24).** La propuesta original de
  Antigravity asumía que la IP real va al *principio* de `X-Forwarded-For`;
  verificado contra soporte oficial de Railway: va al **final**. Fix final
  vía `RemoteIpValve` nativo de Tomcat (`forward-headers-strategy: native`
  + `internal-proxies`) que resuelve la IP real antes de que
  `LoginRateLimitFilter` vea el request. Commit `b1420ed`, 42/42 tests.
- **§2.3 (índice único): verificado en Railway prod (07-24).** El índice
  `uq_suscripcion_activa_por_usuario` NO existía en prod; 0 duplicados
  verificados; aplicado manualmente vía consola `psql` del contenedor.

---

## 2026-08-01 — Revisión de features nuevas (chat, notificaciones, dashboard)

Revisión de seguridad del estado actual enfocada en lo agregado después de
la última auditoría (chatbot `/api/chat` con LLM, notificaciones vía Resend
+ scheduler, persistencia del chat en sessionStorage, dashboard con stats).
**Resultado: ninguna vulnerabilidad activa explotable.** Lo verificado:

- `/api/chat` requiere autenticación (`anyRequest().authenticated()`),
  rate limit 20/min/IP fail-closed, mensaje limitado a 2000 chars.
- Markdown del chat renderizado con elementos React, sin
  `dangerouslySetInnerHTML` — sin XSS, incluso con sessionStorage.
- Prompt injection mitigado estructuralmente (system_instruction separada
  del mensaje del usuario, sin tools — el bot no puede mutar datos).
- Keys de API (Gemini/Anthropic/Resend) solo como placeholders en
  `.env.example`; ningún `.env` commiteado (grep de patrones `AIza`/`re_`/
  `sk-ant-` limpio en ambos repos).
- Proxy conserva `esPathSeguro` + validación de Origin; CTA de email
  escapado con `HtmlUtils`; CTA_URL solo desde config.
- Los 2 moderados del 14/07 también resueltos (0 moderate hoy).

**Correcciones aplicadas en esta sesión (aprobadas por William):**

- **M1 — RestClient sin timeouts** (gemini/anthropic/resend): default del
  JDK HttpClient es infinito → un proveedor colgado ocupa el thread de
  Tomcat indefinidamente. Timeouts connect 3s / read 30s en los 3 clientes.
- **L1 — email de usuario en logs** (`NotificacionVencimientoService`):
  PII en logs persistidos por Railway; ahora se loguea `usuario id`.
- **L2 — axios sin timeout**: `timeout: 15000` (30s explícitos en `/chat`).

**Diferido (documentado, no aplicado):** M2 — CSP mínima en el frontend
(solo `frame-ancestors 'none'`; sin `script-src`/`object-src`/`base-uri`).
Mejora futura del portafolio con nonce vía middleware de Next.js — requiere
testeo real porque Next inyecta scripts inline.

Ver `collab/historial/aplicado/2026-08-01-revision-seguridad-features.md`.

---

## Pendientes que siguen abiertos (a 2026-08-01)

1. **Confirmar en runtime de producción (Railway) que `requirepass` de
   Redis (1.1) está efectivamente activo ahí.** En dev está confirmado
   (`docker-compose.yml`: `requirepass` + bind a `127.0.0.1`). No
   verificado en prod por falta de acceso a la consola — requiere acceso
   de William a Railway, no trabajo de código.
2. **M2 — CSP con nonce** (ver arriba) — mejora opcional de portafolio.
