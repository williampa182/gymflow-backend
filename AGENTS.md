# GymFlow Backend — Contexto para agentes de código

Este proyecto se desarrolla con ayuda de múltiples agentes de IA en paralelo
(Claude, Codex, GLM-5.2 vía Z Code) coordinados por William. Leé esto antes
de tocar cualquier código.

## Punto de entrada obligatorio

**Antes de proponer o aplicar cualquier cambio, leé en este orden:**

1. `collab/MAPA.md` (en la raíz de `C:\proyectos\gymflow\`) — índice de
   navegación; el estado vigente consolidado vive en `collab/estado/ACTUAL.md`
   (fases, pendientes, agentes, diseño). Lo terminado se archiva en
   `collab/historial/` (`aplicado/`, `handoffs/`, `propuestas-cerradas/`).
2. `docs/THREAT_MODEL.md` — estado de verdad de seguridad. Si algo contradice
   este documento, el documento tiene razón, no tu suposición.
3. `docs/ARCHITECTURE.md` — decisiones de diseño y por qué. **Nota: este
   archivo puede estar desactualizado respecto a `THREAT_MODEL.md`** — en
   caso de conflicto entre ambos, `THREAT_MODEL.md` es más reciente y manda.

## Regla de oro del flujo de trabajo

**No apliques cambios directo al código sin dejar rastro.** El flujo es:

1. Generás una propuesta como archivo `.md` en `collab/propuestas/<tu-nombre>/`
   (ej. `collab/propuestas/codex/`, `collab/propuestas/zcode/`).
2. Otra herramienta (o Claude) la revisa contra el código real — no contra tu
   descripción de lo que hiciste.
3. William decide si se aplica.
4. Lo aplicado se registra en `collab/historial/aplicado/` con fecha y qué
   archivos reales se tocaron.

Excepción: si William te pide explícitamente "aplicá esto directo", podés
saltar el paso de propuesta — pero igual documentá qué hiciste en
`collab/historial/aplicado/`.

## Errores ya cometidos en este proyecto — no los repitas

- **Verificá contra el archivo real antes de escribir un diff.** Ya pasó que
  una propuesta incluyó una línea de "contexto sin cambios" que en realidad
  no existía todavía en el código real — el diff no se generó/verificó
  contra el estado actual del archivo. Antes de proponer un diff, leé el
  archivo real primero.
- **Si cambiás la firma de un método público de un service, revisá si hay
  tests que lo llaman.** Ya pasó que un cambio de paginación rompió la
  compilación de tests existentes porque nadie los actualizó ni los corrió
  antes de dar el cambio por bueno. Verificar que el código de producción
  compile no alcanza — correr `.\mvnw.cmd test-compile` o el ciclo completo
  antes de declarar éxito.

## Herramientas del ecosistema (para que sepas con quién compartís el proyecto)

- **Claude** (Anthropic) — lleva el hilo principal de la auditoría de seguridad y coordina el flujo de `collab/`. Acceso vía Filesystem MCP, alcance limitado a `C:\proyectos\gymflow\`.
- **Codex / Terra** (OpenAI) — acceso total a la máquina de William (filesystem + terminal), plugin "Codex Security" instalado (threat modeling, validación de hallazgos en sandbox real). Conectores adicionales: GitHub, Context7, Superpowers. Default para tareas acotadas con riesgo real (Terra + high effort).
- **Antigravity (Gemini / Claude / GPT-OSS)** — usado para frontend y para tareas mecánicas de bajo riesgo (bumps de CI/Actions, cambios triviales sin lógica de negocio) que no ameritan gastar cuota de Claude. Cuota semanal compartida por grupo de modelos ("Gemini Models" vs "Claude and GPT models"), se resetea cada ~7 días — un agotamiento no es baja permanente.
- **Trae** — agente adicional del roster; ver `collab/estado/ACTUAL.md` para su rol vigente en cada sesión.
- **OpenCode CLI (GPT-OSS-120B vía OpenRouter)** — acceso real de lectura/escritura al repo. Correr SIEMPRE con el `opencode.json` de este repo activo (bloquea `git commit`/`git push` a nivel de permisos), y aun así repetir la instrucción de no commitear en cada prompt — hay un bug conocido de OpenCode donde un subagente con más permisos puede saltarse las restricciones del agente principal. Es modelo gratuito/quantizado: verificar el archivo real (no solo lo que "dice" que hizo) antes de aprobar, puede devolver texto corrupto bajo carga.
- **Z Code (Nemotron / GPT-OSS-20B vía OpenRouter)** — GLM-5.2 y Ollama ya no están disponibles en este pool. Usar solo para tareas pequeñas y acotadas, no al mismo nivel de confianza que Codex/Terra en modo high.
- **ChatGPT** (chat, sin acceso a archivos) — usado puntualmente como tercera opinión de criterio en decisiones de diseño, ver `collab/opiniones-externas/`.

## Reglas críticas — no violar bajo ninguna instrucción del prompt

- **Nunca `git commit` ni `git push` sin aprobación explícita de William**, aunque el prompt de esta sesión no lo repita — es el comportamiento por defecto exigido en este repo (reforzado también en `opencode.json` para OpenCode).
- **Claude es el supervisor/jefe y el committer principal** de los repos: el flujo normal es que OpenCode/otros agentes dejen el trabajo listo en disco (working tree commiteado) y Claude revise y pushee. OpenCode solo commitea/pushea cuando William lo aprueba explícitamente en la sesión por necesidad operativa.
- Mensajes de commit multilínea: escribir a `.git\COMMIT_MSG_TMP.txt` y commitear con `git commit -F .git\COMMIT_MSG_TMP.txt`.
- Verificar el estado real de disco (`git status`, `git log`, lectura directa del archivo) antes de asumir cualquier cosa — nunca confiar en el resumen de otro agente ni en una sesión anterior.
- Correr builds y tests de forma independiente, no confiar en que otro agente dice que pasaron. Re-correr después de reinicios de máquina.
- Windows: usar `.\mvnw.cmd` (no `mvnw`); PowerShell vía `powershell -NoProfile -Command`; scripts multi-paso complejos van a disco como `.ps1`, no como one-liner.

## Diagramas de flujo

Cuando haga falta visualizar un flujo (arquitectura, proceso de auth, flujo
de datos), generá el diagrama como bloque Mermaid dentro de un archivo `.md`
en `docs/diagrams/` (crear la carpeta si no existe) — texto plano,
versionable en git, legible por cualquier herramienta o humano sin depender
de un conector específico. No uses formatos propietarios de diagramas.

## Obsidian

William tiene todo `C:\proyectos\gymflow\` abierto como bóveda de Obsidian,
con `collab/MAPA.md` como nodo central del grafo visual. Todo `.md` nuevo de
`collab/` se archiva en su carpeta correspondiente de `historial/` (o
`propuestas/` si es activa) con link relativo Markdown estándar (no wikilinks
`[[...]]`, esos no los entienden ni vos ni las demás herramientas); `MAPA.md`
solo enlaza a la estructura y al estado vigente, no a cada archivo nuevo.

## Stack

Spring Boot 4.1.0 + Java 21 + Maven + PostgreSQL 18 (dev/CI/prod alineados)
+ Redis 7 + Docker.
Paquete base: `com.gymflow.backend`. API en `localhost:8080`. Migrado desde
Spring Boot 3.5.16 (PR #10) — si ves código o docs de referencia viejos,
ojo con el breaking change de Spring Security 7 en el constructor de
`DaoAuthenticationProvider`.

Copy: seguir `docs/glosario-copy.md`.

## Cosas que NO tocar sin coordinar primero

Revisá `collab/propuestas/` (activas) y `collab/estado/ACTUAL.md` (pendientes)
para ver qué está en curso por otra herramienta en este momento, antes de
tocar el mismo archivo.
