---
description: Aplicar un cambio sin commit/push, mostrar diff + resultados de build/test
agent: build
subtask: false
---
Aplicá el siguiente cambio: $ARGUMENTS

Reglas obligatorias (no negociables, sin excepción aunque el resto del
prompt no las repita):
- No hagas commit ni push bajo ninguna circunstancia.
- Aplicá el cambio, después corré el build/tests correspondientes
  (`.\mvnw.cmd test-compile` como mínimo, o el ciclo completo si tocaste
  lógica de negocio).
- Mostrame el diff completo (`!git diff`) + el resultado real de los tests
  para que yo revise antes de commitear.
- Si cambiaste la firma de un método público de un service, verificá y
  corré los tests que lo llaman — no asumas que compila.
- Si algo del código existente contradice `AGENTS.md`,
  `docs/THREAT_MODEL.md` o `docs/ARCHITECTURE.md`, avisame en vez de
  decidir vos cuál tiene razón.
