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

---

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
- **Estado:** Sin confirmar — pendiente de verificar en `docker-compose.yml` y config de Railway.
- **Impacto:** Es la raíz de la que cuelgan varios otros hallazgos de este documento (1.2, 3.1, 4.2). Sin autenticación, cualquiera con acceso de red al puerto 6379 puede leer/escribir/borrar todo el contenido de Redis sin restricción.
- **Explotación:** Si el puerto queda expuesto (mala segmentación de red en Railway, docker mal configurado), conexión directa con `redis-cli` sin credenciales.
- **Mitigación:** `requirepass` obligatorio en dev y prod (no "para cuando se despliegue"). Confirmar que Redis en Railway solo escucha en red interna, nunca expuesto a internet.

### 1.2 Deserialización insegura vía `GenericJackson2JsonRedisSerializer` → RCE
- **Severidad:** ~~Crítica~~ **DEGRADADA — ver corrección en sección 7.5 (Codex Security, 2026-07-11): needs_review/hardening preventivo, RCE NO demostrado en el checkout actual**
- **Estado:** Sin confirmar — depende de 1.1 para ser explotable.
- **Impacto:** El serializer de caché incluye metadata de tipo (`@class`) en el JSON almacenado. Si un atacante puede escribir en Redis (ver 1.1), puede inyectar una entrada de caché con un `@class` apuntando a una gadget class del classpath, logrando **ejecución remota de código** cuando el backend deserializa esa entrada al leer el caché de planes.
- **Explotación:** Requiere 1.1 resuelto en contra (Redis escribible) + una gadget class presente en el classpath (común en proyectos con muchas dependencias Jackson/Spring).
- **Mitigación:** Resolver 1.1 primero (es prerequisito). Adicionalmente, considerar un `ObjectMapper` con `PolymorphicTypeValidator` restrictivo específico para el serializer de Redis, en vez de confiar en el default.

### 1.3 CVE-2026-40976 — Spring Boot 4.0.0–4.0.5, bypass de autorización con Actuator
- **Severidad:** Crítica (CVSS 9.1)
- **Estado:** Confirmado como riesgo futuro — aplica directamente a la migración pendiente (PR #4, Dependabot, Spring Boot 3.5.16 → 4.1.0).
- **Impacto:** En apps servlet-based que usan el filter chain por defecto de Spring Security, dependen de `spring-boot-actuator-autoconfigure` y NO dependen de `spring-boot-health`, la configuración de seguridad por defecto falla en aplicar autorización — puede exponer **todos los endpoints, incluidos los de ADMIN**, sin autenticación.
- **Por qué aplica a GymFlow específicamente:** el proyecto es servlet-based, usa el filter chain de Spring Security, y agregó Actuator en la sesión del 2026-07-07 — cumple 3 de las 4 condiciones de la CVE. Falta confirmar la cuarta (dependencia a `spring-boot-health`).
- **Mitigación:** Antes de mergear PR #4, confirmar explícitamente la dependencia a `spring-boot-health` o revisar el filter chain manualmente contra el advisory oficial de Spring (`spring.io/security/cve-2026-40976`). No mergear el PR sin este chequeo, sin importar que la CI pase en verde — la CI no testea esta condición.

### 1.4 CVE-2026-40972 — timing attack contra DevTools remote secret (Boot, misma migración)
- **Severidad:** Alta (CVSS 7.5)
- **Estado:** Sin confirmar — depende de si DevTools está activo en algún entorno compartido.
- **Impacto:** Atacante en la misma red puede explotar timing contra el remote secret de DevTools, con potencial de RCE en casos extremos.
- **Mitigación:** Confirmar que DevTools nunca está activo fuera de la máquina de desarrollo local, especialmente no en contenedores compartidos.

---

## 2. Altos — comprometen autenticación, autorización o disponibilidad significativa

### 2.1 Fail-closed en `LoginRateLimitFilter` depende de que Redis esté protegido (ver 1.1)
- **Severidad:** Alta
- **Estado:** Decisión de diseño tomada (fail-closed) — condicionada a resolver 1.1 primero.
- **Impacto:** Con la política fail-closed decidida, si un atacante logra que Redis deje de responder (trivial si 1.1 no está resuelto), tumba el login de **todo el sistema** para todos los usuarios.
- **Mitigación:** No implementar fail-closed hasta confirmar 1.1. El orden importa: fail-closed sin Redis protegido convierte una optimización en un botón de apagado del sistema.

### 2.2 `X-Forwarded-For` sin validar contra proxy confiable
- **Severidad:** Alta
- **Estado:** Sin confirmar.
- **Impacto:** Si el filtro de rate limiting confía ciegamente en el header `X-Forwarded-For` sin validar que proviene realmente del proxy de Railway, un atacante puede mandar un valor distinto en cada request y hacer que cada intento cuente como una IP diferente — bypass total del rate limit de login sin tocar Redis.
- **Mitigación:** Validar que el request entra desde la IP del proxy conocido de Railway antes de confiar en `X-Forwarded-For`; si no, usar `getRemoteAddr()` directo.

### 2.3 Ausencia de unique constraint a nivel de base de datos para suscripción activa por usuario
- **Severidad:** Alta
- **Estado:** Sin confirmar — requiere revisar `SuscripcionService.crear()` y el esquema de `suscripciones`.
- **Impacto:** Si el chequeo "¿ya tiene una suscripción activa?" y el `save()` no son atómicos (TOCTOU), requests concurrentes pueden crear múltiples suscripciones activas para el mismo usuario. Esto es un bug de integridad de negocio, no de concurrencia de updates — el `@Version` planeado para Fase 5 NO lo resuelve.
- **Mitigación:** Constraint única a nivel de Postgres (ej. índice parcial único `WHERE estado = 'ACTIVA'`), no solo chequeo en Java.

### 2.4 JWT sin revocación — `activo=false` no invalida tokens ya emitidos
- **Severidad:** Alta
- **Estado:** Confirmado como limitación conocida (documentada en `ARCHITECTURE.md` §8).
- **Impacto:** Desactivar un usuario comprometido no tiene efecto hasta que su JWT expire (hasta 24h). Lo mismo aplica a "logout" — solo limpia cookies del navegador, el token sigue siendo válido en el backend.
- **Mitigación (proporcional al proyecto, no "nivel banco"):** revalidar `activo` contra la base en `JwtAuthFilter` en cada request (costo: una consulta extra, mitigable con cache corto), o reducir `JWT_EXPIRATION` significativamente y aceptar la ventana de riesgo como trade-off documentado.

### 2.5 CSRF vía la cookie httpOnly + proxy genérico de Next.js
- **Severidad:** Alta
- **Estado:** Sin confirmar — depende de si el proxy `[...path]/route.ts` valida origen de la request.
- **Impacto:** La cookie `token` se manda automáticamente en cada request al dominio. Con `sameSite: lax`, las mutaciones vía GET (si existieran) o escenarios futuros que requieran relajar la política quedan expuestos a CSRF clásico. El proxy genérico es el punto más goloso porque convierte la cookie en un `Authorization` header válido hacia el backend real sin fricción adicional.
- **Mitigación:** Confirmar que ninguna mutación de estado ocurre vía GET. Agregar validación de origen (header custom tipo `X-Requested-With`, o token CSRF double-submit) en el proxy antes de reenviar al backend.

### 2.6 SSRF / passthrough no sanitizado en el proxy `[...path]/route.ts`
- **Severidad:** Alta
- **Estado:** Sin confirmar.
- **Impacto:** Si el proxy toma el `path` del request entrante sin validarlo contra una lista de rutas permitidas del backend, puede usarse como puente hacia rutas internas no destinadas a exposición pública, especialmente peligroso si `BACKEND_URL` apunta a una red privada de Railway.
- **Mitigación:** Whitelist explícita de rutas permitidas en el proxy, o al menos validación de que el path resuelve dentro del namespace esperado de la API.

### 2.7 DoS de parsing JWT sin autenticación previa (Billion Hashes / Compression DoS)
- **Severidad:** Alta
- **Estado:** Sin confirmar.
- **Impacto:** Investigación reciente de fuzzing sobre JJWT (la librería usada en GymFlow) muestra que tokens maliciosamente construidos pueden forzar cantidades desproporcionadas de operaciones de hash o descompresión, consumiendo CPU/memoria del servidor sin necesidad de romper la firma ni estar autenticado.
- **Mitigación:** Chequeo barato de estructura (tres segmentos, tamaño máximo razonable, algoritmo declarado) antes de invocar el parser completo de JJWT.

---

## 3. Medios — amplifican otros ataques o exponen información

### 3.1 Enumeración de usuarios vía mensajes de error o timing en login/registro
- **Severidad:** Media
- **Estado:** Sin confirmar.
- **Impacto:** Si `AuthService` distingue "usuario no encontrado" de "password incorrecta" (por mensaje o por tiempo de respuesta, ya que BCrypt agrega latencia medible solo cuando el usuario existe), un atacante puede enumerar cuentas válidas antes de intentar brute-force dirigido.
- **Mitigación:** Mensaje idéntico y tiempo de respuesta constante independientemente de si el email existe (ejecutar un BCrypt "dummy" cuando el usuario no existe, para igualar timing).

### 3.2 Mass assignment vía DTOs sin whitelist explícita de campos
- **Severidad:** Media
- **Estado:** Sin confirmar — requiere revisar `PlanRequestDTO` y `SuscripcionRequestDTO`.
- **Impacto:** Si el binding de JSON a DTO no excluye explícitamente campos como `id`, `activo`, `creadoEn`, `fechaFin`, un cliente puede intentar sobrescribirlos aunque la lógica de negocio no lo espere.
- **Mitigación:** Confirmar que cada DTO de request solo declara los campos que el cliente debería poder setear.

### 3.3 Endpoints sin paginación — resource exhaustion y scraping
- **Severidad:** Media
- **Estado:** Confirmado — ningún endpoint de listado (`/api/planes`, `/api/usuarios`, `/api/suscripciones`) tiene paginación documentada.
- **Impacto:** Cada request devuelve el dataset completo; costo de serialización crece con los datos, y permite scraping completo en una sola llamada si el rol lo permite.
- **Mitigación:** Paginación (`Pageable` de Spring Data) en todos los endpoints de listado.

### 3.4 Sin límite de tamaño de request body
- **Severidad:** Media
- **Estado:** Sin confirmar.
- **Impacto:** Sin `server.max-http-request-header-size` o límite equivalente configurado, un POST con payload de varios MB fuerza parseo costoso antes de cualquier validación de negocio.
- **Mitigación:** Configurar límite explícito de tamaño de body en Tomcat/Spring.

### 3.5 Registro sin política de password ni control anti-bot
- **Severidad:** Media
- **Estado:** Sin confirmar.
- **Impacto:** Sin validación de fortaleza de contraseña ni CAPTCHA/rate limit dedicado en `/api/auth/register`, es trivial automatizar creación masiva de cuentas con passwords débiles — munición para credential stuffing y ruido en la base de datos.
- **Mitigación:** Validación mínima de longitud/complejidad de password en el DTO de registro. Considerar rate limiting específico (ya existe el mecanismo, extenderlo a `/register`).

### 3.6 Fingerprinting vía headers de servidor y páginas de error
- **Severidad:** Media
- **Estado:** Sin confirmar.
- **Impacto:** Header `Server` de Tomcat expuesto, o Whitelabel Error Page con stack trace completo si el perfil `dev` queda activo accidentalmente en producción, regalan versión exacta del stack — acelera la explotabilidad de CVEs conocidas del stack (incluida 1.3).
- **Mitigación:** Confirmar `SPRING_PROFILES_ACTIVE=prod` en Railway (ya está en el checklist de deploy de `ARCHITECTURE.md` §7); deshabilitar/personalizar el header `Server`.

### 3.7 Falta de headers de seguridad HTTP (CSP, X-Frame-Options, HSTS)
- **Severidad:** Media
- **Estado:** Sin confirmar.
- **Impacto:** Sin `X-Frame-Options`/CSP `frame-ancestors`, el frontend es embebible en iframe ajeno (clickjacking). Sin HSTS, la primera conexión de cada usuario es vulnerable a downgrade de HTTPS a HTTP en redes no confiables.
- **Mitigación:** Configurar headers en `next.config.js` (frontend) y `HeaderWriterFilter` (backend, ya presente en la cadena de Spring Security — falta configurar los headers específicos).

---

## 4. Bajos / operacionales — no explotables directamente pero aumentan el impacto de otros hallazgos

### 4.1 `BCrypt` con cost factor por defecto sin verificar
- **Severidad:** Baja individualmente, alta en combinación con una filtración de datos
- **Mitigación:** Confirmar cost factor ≥ 12 explícito en la configuración de `PasswordEncoder`.

### 4.2 Log injection vía `X-Request-ID` si se acepta del cliente sin sanitizar
- **Severidad:** Baja
- **Mitigación:** Confirmar que `CorrelationIdFilter` genera el ID server-side siempre, o sanitiza el header entrante si lo reutiliza.

### 4.3 Supply chain — actualizaciones de Dependabot mergeadas sin revisión de changelog
- **Severidad:** Baja (mitigado parcialmente por buena práctica ya existente de pausar PRs riesgosos)
- **Mitigación:** Revisar changelog real, no solo confiar en que la CI pase, incluso en updates menores.

### 4.4 GitHub Actions — workflows con acceso a `PROD_DATABASE_URL`
- **Severidad:** Baja mientras el repo sea privado; sube a Media/Alta si el repo se hace público
- **Mitigación:** Checklist previo a hacer el repo público: revisar triggers de cada workflow (`pull_request` vs `pull_request_target`) y qué secrets tiene disponibles cada uno.

### 4.5 Credenciales de Docker Compose hardcodeadas en el repo
- **Severidad:** Baja (son credenciales de dev sin valor real)
- **Mitigación:** Comentario explícito en el archivo o separación clara del compose de producción para evitar repetir el incidente del JWT secret purgado.

### 4.6 Contenedor backend potencialmente corriendo como root
- **Severidad:** Baja individualmente, multiplica el daño de 1.2 si se combina
- **Mitigación:** `USER` no-root explícito en el Dockerfile.

---

## 5. Priorización sugerida (severidad × esfuerzo de explotación)

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

### 7.5 Corrección importante — Codex Security, validación en sandbox (2026-07-11)

> Ver `collab/propuestas/codex/codex-security-triage-redis-1.1-1.2.md` para el
> detalle completo con evidencia y `collab/propuestas/codex/1.1-1.2-redis-hardening-propuesta.md`
> para la propuesta de fix. Resumen honesto de lo que cambia:

**1.1 (Redis sin auth): CONFIRMADO con evidencia de runtime real**, no solo
lectura de config. Codex levantó Redis con el `docker-compose.yml` actual y
confirmó con `redis-cli PING/SET/GET` que acepta comandos sin AUTH, y que
Docker publica el puerto en `0.0.0.0:6379` (todas las interfaces), no solo
loopback — peor de lo que teníamos documentado. Propuesta de fix lista,
pendiente de aplicar.

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
   actual. Ninguno había notado esta conexión entre el trabajo de 3.3 y el
   estado de 1.2.

**Reclasificación honesta:** 1.2 pasa de "Crítica, confirmada en teoría" a
"needs_review / hardening preventivo" — el riesgo real hoy es que alguien
reintroduzca `@Cacheable` en el futuro sin revisar el modelo de serialización,
no una vulnerabilidad activa explotable en el checkout actual. Propuesta de
Codex: remover `RedisCacheConfig` y `@EnableCaching` por completo mientras no
haya cache activo (superficie muerta = riesgo futuro innecesario), conservando
Redis para el rate limiting que sí lo usa. Pendiente de decisión de William.

Esto quedó documentado en el propio JSON de salida de Codex Security con
formato `triage-finding/v0`, incluyendo cadena de razonamiento, evidencia,
y contraevidencia — exactamente el tipo de segunda opinión con capacidad de
validación en sandbox que ni Claude leyendo código ni William podían hacer.

## 6. Lo que este documento NO cubre (límites honestos del análisis)

- Configuración real de red/firewall de Railway — no verificable desde esta sesión.
- Resultado de un pentest o escaneo automatizado real contra el sistema desplegado.
- Vulnerabilidades de día cero no públicas.
- Ingeniería social, phishing dirigido, o compromiso físico de la máquina de desarrollo.

Este documento es un punto de partida priorizado, no un certificado de seguridad.

---

## 7. Auditoría de código real — resultados (2026-07-10)

> Cada ítem revisado contra el código fuente real de `gymflow-backend` y
> `gymflow-frontend`. Estado final tras la auditoría y los fixes aplicados
> en esta sesión.

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
| 2.3 | Sin unique constraint para suscripción activa | `SuscripcionService.crear()`: check-then-act clásico (`findByUsuarioIdAndEstado` + `save()` no atómico). Confirmado, sin `@Version` en `Suscripcion`. | **CORREGIDO POR COMPLETO (sesión 3).** `@Version` en la entidad + índice único parcial `uq_suscripcion_activa_por_usuario` aplicado en dev (`scripts/migrations/001_unique_suscripcion_activa.sql`, verificado sin duplicados previos, `CREATE INDEX` exitoso). Ambos casos de la carrera (update concurrente a la misma fila, y creación concurrente de dos filas activas) están cerrados. **Pendiente:** aplicar el mismo script en cualquier otro entorno (staging/prod) antes de asumir que está resuelto ahí también — esta migración es manual, no se propaga sola. |
| 2.5 | CSRF | `SecurityConfig`: `.csrf(csrf -> csrf.disable())` confirmado — sin ninguna defensa CSRF en el backend. El proxy de Next.js es el punto de exposición real. | **CORREGIDO (defensa en profundidad).** El proxy `[...path]/route.ts` ahora valida `Origin` contra el origen esperado en todo método que muta estado. |
| 2.6 | SSRF / path traversal en proxy Next.js | Confirmado: `new URL()` normaliza `..`, permitiendo que el path escape del prefijo `/api/` (ej. `/api/backend/../actuator/prometheus`). | **CORREGIDO.** `esPathSeguro()` rechaza segmentos `.`, `..`, vacíos, o con `/`/`\` antes de construir la URL destino. |
| 2.7 | DoS de parsing JWT sin auth previa | Confirmado como bug real, no solo hipotético: `JwtAuthFilter` llamaba a `jwtUtil.extraerUsername()` sin try/catch — cualquier `Authorization: Bearer <basura>` producía una excepción sin manejar en cada request. | **CORREGIDO.** Try/catch alrededor del parseo (`JwtException`, `IllegalArgumentException`, `UsernameNotFoundException`), más límite de tamaño (2048 chars) antes de invocar el parser. |
| 3.7 | Sin headers de seguridad HTTP | Confirmado: `next.config.ts` no tenía ninguna configuración de headers. | **CORREGIDO.** Agregado `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, `Strict-Transport-Security`, `Content-Security-Policy` (frame-ancestors 'none'). |
| — | Sin `@ControllerAdvice` global | Confirmado: no existía ningún manejo centralizado de excepciones; `RuntimeException` genéricas de los servicios (ej. "Usuario no encontrado") salían como 500 sin status code correcto. | **CORREGIDO.** `GlobalExceptionHandler` nuevo cubre validación, credenciales inválidas (mensaje genérico anti-enumeración), conflictos de integridad/optimistic locking, y Redis caído. |

### 7.2 Correcciones a hallazgos previos (más matizados de lo que se pensó)

- **3.5 (registro sin política de password/anti-bot):** parcialmente incorrecto. `RegisterRequest` sí valida `@Size(min = 8)` en el password, y `/api/auth/register` SÍ está cubierto por el mismo `LoginRateLimitFilter` (10 intentos/min). Sigue faltando: complejidad de password (solo longitud) y CAPTCHA — pero no es la ausencia total que se había asumido.
- **4.6 (contenedor root):** no aplica tal como estaba escrito — el repo no tiene `Dockerfile` propio; el deploy en Railway probablemente usa build automático (Nixpacks). Revisar si Railway expone alguna opción de usuario no-root cuando se confirme el método de build real.

### 7.4 Verificación en runtime (2026-07-10, sesión 3)

Probado contra el backend real corriendo en local (`.\mvnw.cmd spring-boot:run`), no solo lectura de código:

- **Escalada de privilegios (7.0):** `POST /api/auth/register` con `"rol":"ADMIN"` en el body devuelve un usuario con `rol: CLIENTE` en la respuesta. Confirmado corregido en runtime, no solo en el código fuente.
- **2.7 (DoS/excepción sin manejar en JwtAuthFilter):** `GET /api/planes` con `Authorization: Bearer esto-no-es-un-jwt-valido` devuelve **403 Forbidden** limpio. Antes del fix esto hubiera propagado una excepción de JJWT sin capturar. Confirmado corregido en runtime.
- Nota aparte: los stack traces de `AuthorizationDeniedException` visibles en el log con `SECURITY_LOG_LEVEL=TRACE` son el mecanismo interno normal de Spring Security (control de flujo vía excepciones), no relacionados con el bug corregido — no confundir con una regresión.

### 7.3 Pendientes reales (requieren decisión de William o trabajo de infraestructura, no solo código)

1. `requirepass` en Redis + no exponer el puerto al host (1.1) — **el más urgente, desbloquea 1.2 y reduce el riesgo real de 2.1**.
2. ~~Aplicar `scripts/migrations/001_unique_suscripcion_activa.sql`~~ **HECHO en dev (2026-07-10)** — falta replicar en staging/prod cuando existan esos entornos.
3. Confirmar dependencia a `spring-boot-health` antes de mergear PR #4 (1.3).
4. Confirmar `DevTools` nunca activo en entornos compartidos (1.4).
5. Paginación en endpoints de listado (3.3) — **HECHO (2026-07-11)**, aplicado desde propuesta de Codex, revisado por Claude.
6. Password policy más allá de longitud (3.5) — **HECHO (2026-07-12)**: `@Size(min=12, max=128)` sin composición + `@NotCommonPassword` (lista offline chica), aplicado por Claude desde propuesta ya lista de Codex. Límite de tamaño de request body — **HECHO**.
7. ~~Aplicar fix de Redis auth (1.1)~~ **HECHO (2026-07-11)** — `requirepass` + bind a 127.0.0.1, ver `collab/aplicado/2026-07-11-redis-hardening.md`. **Verificación en runtime pendiente.**
8. ~~Decidir sobre remover RedisCacheConfig/@EnableCaching (1.2)~~ **HECHO (2026-07-11)** — removido, movido a `docs/codigo-removido/`. **Verificación en runtime pendiente.**
