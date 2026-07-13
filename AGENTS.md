# GymFlow Backend — Contexto para agentes de código

Este proyecto se desarrolla con ayuda de múltiples agentes de IA en paralelo
(Claude, Codex, GLM-5.2 vía Z Code) coordinados por William. Leé esto antes
de tocar cualquier código.

## Punto de entrada obligatorio

**Antes de proponer o aplicar cualquier cambio, leé en este orden:**

1. `collab/MAPA.md` (en la raíz de `C:\proyectos\gymflow\`) — índice de todo
   lo que existe: qué está confirmado, qué está pendiente, qué propuso cada
   herramienta, qué se aplicó y cuándo.
2. `docs/THREAT_MODEL.md` — estado de verdad de seguridad. Si algo contradice
   este documento, el documento tiene razón, no tu suposición.
3. `docs/ARCHITECTURE.md` — decisiones de diseño y por qué. **Nota: este
   archivo puede estar desactualizado respecto a `THREAT_MODEL.md`** — en
   caso de conflicto entre ambos, `THREAT_MODEL.md` es más reciente y manda.

## Regla de oro del flujo de trabajo

**No apliques cambios directo al código sin dejar rastro.** El flujo es:

1. Generás una propuesta como archivo `.md` en `collab/propuestas/<tu-nombre>/`
   (ej. `collab/propuestas/codex/`, `collab/propuestas/glm/`).
2. Otra herramienta (o Claude) la revisa contra el código real — no contra tu
   descripción de lo que hiciste.
3. William decide si se aplica.
4. Lo aplicado se registra en `collab/aplicado/` con fecha y qué archivos
   reales se tocaron.

Excepción: si William te pide explícitamente "aplicá esto directo", podés
saltar el paso de propuesta — pero igual documentá qué hiciste en
`collab/aplicado/`.

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
- **Codex** (OpenAI) — acceso total a la máquina de William (filesystem + terminal), plugin "Codex Security" instalado (threat modeling, validación de hallazgos en sandbox real). Tiene conectores adicionales (GitHub, Context7, Superpowers — framework de planning/TDD/debugging).
- **GLM-5.2 vía Z Code** (Zhipu AI) — fuerte en frontend, verifica contra código real con buen nivel de detalle. Corriendo en modo Max (contexto 1M tokens).
- **ChatGPT** (chat, sin acceso a archivos) — usado puntualmente como tercera opinión de criterio en decisiones de diseño, ver `collab/opiniones-externas/`.

## Diagramas de flujo

Cuando haga falta visualizar un flujo (arquitectura, proceso de auth, flujo
de datos), generá el diagrama como bloque Mermaid dentro de un archivo `.md`
en `docs/diagrams/` (crear la carpeta si no existe) — texto plano,
versionable en git, legible por cualquier herramienta o humano sin depender
de un conector específico. No uses formatos propietarios de diagramas.

## Obsidian

William tiene todo `C:\proyectos\gymflow\` abierto como bóveda de Obsidian,
con `collab/MAPA.md` como nodo central del grafo visual. Cualquier archivo
`.md` nuevo que generés en `collab/` debería agregarse a `MAPA.md` con un
link relativo Markdown estándar (no wikilinks `[[...]]`, esos no los
entienden ni vos ni las demás herramientas) para que aparezca conectado en
el grafo.

## Stack

Spring Boot 3.5.16 + Java 21 + Maven + PostgreSQL 16 + Redis 7 + Docker.
Paquete base: `com.gymflow.backend`. API en `localhost:8080`.

## Cosas que NO tocar sin coordinar primero

Revisá `collab/MAPA.md` sección "Propuestas" para ver qué está en curso por
otra herramienta en este momento, antes de tocar el mismo archivo.
