# GymFlow — Changelog de seguridad

> Puerta de entrada a la documentación de seguridad del proyecto. Para el
> detalle de amenazas y su estado actual: [`THREAT_MODEL.md`](THREAT_MODEL.md).
> Para la bitácora completa de auditorías y fixes: [`SECURITY_AUDIT_LOG.md`](SECURITY_AUDIT_LOG.md).
> Última actualización: 2026-08-01.

## Resumen ejecutivo

| Métrica | Valor |
|---|---|
| Hallazgos confirmados en auditorías (2026-07-09 → 2026-08-01) | 34 |
| Corregidos / cerrados (incluidos reclasificados y documentados) | 34 |
| Pendientes de código | 0 |
| Pendientes de verificación en producción | 1 (Redis `requirepass` en Railway, requiere consola) |
| `npm audit` (frontend) | 0 vulnerabilidades (verificado 2026-08-01) |
| CVE-2026-40976 (Spring Boot 4.x + Actuator) | No aplica (Boot 4.1.0, fuera de rango + `SecurityConfig` propio) |

**Lo que este proyecto demuestra en seguridad:** no "actualizar dependencias
y rezar" — bugs propios de diseño encontrados, priorizados, corregidos y con
tests de regresión. Los tres hallazgos más interesantes (de diseño, no CVEs):

1. **Escalada de privilegios en registro público.** `RegisterRequest` aceptaba
   un campo `rol` sin restricción server-side: cualquiera podía registrarse
   como `ADMIN` con un solo POST sin autenticación. Corregido forzando
   `Rol.CLIENTE` siempre. (Hallazgo 7.0, el más grave de la auditoría.)
2. **Race condition de suscripción activa (TOCTOU).** El chequeo
   "¿ya tiene una activa?" y el `save()` no eran atómicos: dos requests
   concurrentes podían crear dos suscripciones activas para el mismo usuario.
   Cerrado con `@Version` (updates concurrentes) **y** índice único parcial en
   Postgres (creaciones concurrentes) — las dos mitades de la carrera.
3. **SSRF / path traversal en el proxy de Next.js.** `new URL()` normalizaba
   `..`, permitiendo escapar del prefijo `/api/` (ej. `/api/backend/../actuator/prometheus`).
   Corregido validando cada segmento del path antes de construir el destino.

## Tabla de hallazgos → estado

| # | Hallazgo | Severidad | Estado | Resuelto en |
|---|---|---|---|---|
| 7.0 | Escalada de privilegios en registro | Crítica | ✅ Corregido | `AuthService.registrar()` fuerza `CLIENTE` (sesión 07-10) |
| 1.1 | Redis sin auth / puerto expuesto | Crítica | ✅ Corregido | `requirepass` + bind `127.0.0.1` (`docker-compose.yml`, 07-11) |
| 1.2 | Deserialización insegura vía Redis → RCE | ~~Crítica~~ | ✅ Reclasificado (hardening preventivo) | `RedisCacheConfig`/`@EnableCaching` removidos (07-11); degradación con evidencia de sandbox, ver audit log |
| 1.3 | CVE-2026-40976 (Boot 4.x + Actuator) | Crítica | ✅ No aplica | Boot 4.1.0 fuera de rango + `SecurityConfig` propio (verificado 07-23) |
| 1.4 | DevTools remote secret | Alta | ✅ No aplica | `scope=runtime` + `optional=true`, excluido del JAR de prod |
| 2.1 | Fail-closed rate limit sin manejo de fallo | Alta | ✅ Corregido | 503 explícito ante fallo de Redis (07-10) |
| 2.2 | X-Forwarded-For sin validar (bypass de rate limit) | Alta | ✅ Corregido | `RemoteIpValve` nativo de Tomcat (07-24) |
| 2.3 | Sin unique constraint de suscripción activa | Alta | ✅ Corregido | `@Version` + índice único parcial, dev y Railway prod (07-24) |
| 2.4 | JWT sin revocación | Alta | 🟡 Aceptado | Decisión documentada en `ARCHITECTURE.md` §8; revalidación de `activo` en cada request como mitigación futura |
| 2.5 | CSRF vía cookie httpOnly + proxy | Alta | ✅ Corregido | Validación de `Origin` en el proxy (defensa en profundidad) |
| 2.6 | SSRF / passthrough en proxy de Next.js | Alta | ✅ Corregido | `esPathSeguro()` valida segmentos (07-10) |
| 2.7 | DoS de parsing JWT (malformado → excepción) | Alta | ✅ Corregido | try/catch + límite 2048 chars previo al parseo (07-10) |
| 3.1 | Enumeración de usuarios (error/timing) | Media | ✅ Corregido | Fix A (mensaje genérico) + Fix B (timing-safe + rehash) (07-19) |
| 3.2 | Mass assignment vía DTOs | Media | ✅ Auditado | DTOs con whitelist explícita; test de regresión (07-25) |
| 3.3 | Listados sin paginación | Media | ✅ Corregido | `Pageable` en todos los listados (07-11) |
| 3.4 | Sin límite de header/body | Media | ✅ Corregido | `max-http-request-header-size` + form-post/swallow (07-11) |
| 3.5 | Registro sin política de password | Media | ✅ Corregido | `@Size(min=12,max=128)` + `@NotCommonPassword` (NIST 800-63B) (07-12) |
| 3.6 | Stack traces / header Server en prod | Media | ✅ Corregido | `include-stacktrace: never`, `server-header: ""` (07-11) |
| 3.7 | Sin headers de seguridad HTTP | Media | ✅ Corregido | X-Frame-Options, CSP, HSTS, etc. en `next.config.ts` (07-10) |
| 4.1 | BCrypt con cost factor implícito | Baja | ✅ Corregido | `BCRYPT_STRENGTH = 12` explícito (07-11) |
| 4.2 | Log injection vía X-Request-ID | Baja | ✅ Corregido | `CorrelationIdFilter` sanitiza (07-11) |
| 4.3 | Dependabot mergeado sin revisar changelog | Baja | ✅ Proceso | Regla de revisar changelog antes de mergear (vigente) |
| 4.4 | Workflows con acceso a PROD_DATABASE_URL | Baja | 🟡 Checklist | Verificar triggers antes de hacer el repo público |
| 4.5 | Credenciales de dev hardcodeadas en compose | Baja | ✅ Documentado | Advertencia "DEV ONLY" explícita en `docker-compose.yml` (07-11) |
| 4.6 | Contenedor backend como root | Baja | ✅ Corregido | `Dockerfile` con usuario `gymflow` no-root (07-13) |
| §2/3/5/7/9/11 | Hallazgos menores del Security Deep Dive | Mixta | ✅ 6/6 corregidos | `af0d0f6` (07-15) + `f81f1b8` (07-21) |
| npm | 3 vulnerabilidades HIGH (`next`/`postcss`/`sharp`) | Alta | ✅ Corregido | `932da34` (07-23) + `cebff48` (07-24); `npm audit` en 0, verificado 08-01 |
| M1 | RestClient sin timeouts (chat/email → agotamiento de threads) | Media | ✅ Corregido | Timeouts connect 3s / read 30s en los 3 clientes (08-01) |
| L1 | Email de usuario en logs de notificaciones (PII) | Baja | ✅ Corregido | Log con `usuario id` en vez de email (08-01) |
| L2 | axios sin timeout | Baja | ✅ Corregido | `timeout: 15000` (30s en chat) (08-01) |
| C1 | Chat: PII del usuario hacia el proveedor LLM (tier gratis entrena con el tráfico) | Media | ✅ Corregido | Filtro de emails en `ChatService` corta antes del proveedor + aviso en UI + prompt endurecido (08-01) |
| C2 | Chat: ingeniería de prompt contra el bot (revelar instrucciones/stack, inventar datos) | Media | ✅ Corregido | 3 reglas explícitas en el system prompt (08-01) |
| C3 | Chat: sin kill-switch para prod | Baja | ✅ Corregido | `app.chat.enabled` / `APP_CHAT_ENABLED` → 503 (08-01) |
| C4 | Cuota del tier gratis sin observabilidad | Baja | ✅ Corregido | Log de uso sin contenido (proveedor/modelo/tokens/duración) en ambos clientes (08-01) |

## ¿Quién hizo qué?

- **Claude** — llevó el hilo de la auditoría (07-09 a 07-13), coordinó
  `collab/`, verificó hallazgos contra disco real y redactó el grueso del
  threat model.
- **Codex (OpenAI)** — propuso los fixes de la mitad de la tabla (3.3-4.5,
  1.1/1.2) y, con el plugin Codex Security, **degradó la severidad de 1.2**
  con un harness Java real en sandbox (la evidencia más valiosa del proceso:
  ver `SECURITY_AUDIT_LOG.md` §7.5).
- **GLM-5.2 (Z Code)** — el Security Deep Dive de 12 hallazgos y el
  hardening de user enumeration (Fix A/B).
- **OpenCode CLI (deepseek-v4-flash-free)** — cierre de 2.2 vía
  `RemoteIpValve`, notificaciones Resend, y la revisión de features nuevas
  del 01/08 (M1/L1/L2).
- **William** — decidió qué investigar y en qué orden, priorizó, tomó las
  decisiones de diseño (password policy 3.5, Opción C de §4,
  `reveal-email=false`) y **encontró él mismo el bug de paginación del 13/07**
  en el navegador — el hallazgo más valioso de esa sesión, que ninguna IA
  detectó. Su propia respuesta a "¿qué hiciste vos?" está abajo.

## Respuesta de William ("¿qué hiciste vos?")

> *(Sección para escribir en primera persona — nadie más puede hacerlo con
> autenticidad. Un párrafo o dos honestos: qué decidiste, qué priorizaste,
> qué encontraste vos mismo. El bug de paginación del 13/07 es el ejemplo
> perfecto de algo que ninguna IA encontró.)*
