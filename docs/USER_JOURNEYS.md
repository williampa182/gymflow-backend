# User Journeys críticos — GymFlow

Este documento existe porque el 14 de julio de 2026 se detectó que la app
llevaba semanas "completa" según checklist de features, pero un usuario
nuevo real no podía crear una cuenta: no existía la página `/register`.
El error no fue de código, fue de proceso — se verificaba que cada
componente funcionara aislado (login OK, CRUD OK, dashboard OK), pero
nunca se recorrió un journey completo de punta a punta desde la
perspectiva de alguien que llega por primera vez.

**Regla desde ahora:** antes de declarar una fase o feature "completa",
recorrer al menos el journey relevante de esta lista, no solo verificar
que el componente aislado funcione.

## 1. Usuario nuevo se registra y empieza a usar la app
1. Visitante anónimo llega a `/login`.
2. Click en "¿No tienes cuenta? Regístrate" → `/register`.
3. Llena nombre, email, password (12+ caracteres, no común).
4. Submit → queda logueado automáticamente (rol CLIENTE) → redirige a `/dashboard`.
5. Ve su dashboard de cliente (no el de admin).

**Estado:** ✅ implementado y verificado con smoke test automatizado, tanto
local como contra producción (Railway) — `e2e/registro.spec.ts` en
`gymflow-frontend`, Playwright, 15 jul 2026
(`BASE_URL=https://gymflow-frontend-production.up.railway.app npm run test:e2e`).

## 2. Admin gestiona usuarios
1. Login como ADMIN.
2. Ve tabla de usuarios en `/dashboard/usuarios`.
3. Activa/desactiva un usuario.
4. Promueve CLIENTE → ENTRENADOR/ADMIN vía `PATCH /api/usuarios/{id}/rol`
   protegido con `@PreAuthorize("hasRole('ADMIN')")`, expuesto en la UI de
   `/dashboard/usuarios` (resolución C02, 2026-08-03).

**Estado:** verificado (2026-08-03): endpoint + guard + UI + tests.
Checkbox "decidir si Journey 2 necesita endpoint de cambio de rol" resuelto:
ya existe `PATCH /api/usuarios/{id}/rol` implementado y funcional.

## 3. Cliente se suscribe a un plan
1. Login como CLIENTE.
2. Ve lista de planes disponibles.
3. Se suscribe a uno.
4. Ve su suscripción activa reflejada en su panel.
5. (Admin) ve esa suscripción reflejada en `/dashboard/admin/estadisticas`.

**Estado:** ✅ verificado en producción (Railway) — T5, 2026-08-06.
Playwright headless contra `https://gymflow-frontend-production.up.railway.app`.
Usuario de prueba: `journey.t5.20260806@test.local` (id=9, rol CLIENTE).
Evidencia en `collab/evidencia/qa-visual/2026-08-06-journeys-3-4/`:
  - `01-s0-register-page.png` … `03-s0-registered-dashboard.png`: registro OK
  - `04-s1-planes-list.png`: lista de planes visible con botón "Inscribirme"
  - `05-s2-after-inscribir-click.png` … `08-s2-subscribed.png`: inscripción + modal "Pagar (demo)" OK
  - `09-s3-dashboard-cliente.png`, `10-s3-planes-post-sub.png`: dashboard post-suscripción OK
Paso 5 (admin ve estadísticas): ✅ verificado (2026-08-07, T5) — el admin
ve el resumen en `/dashboard` (usuarios por rol, suscripciones por estado,
ingresos). Nota: `/dashboard/admin/estadisticas` NO existe como ruta (404)
— las estadísticas viven en el dashboard admin; el script de verificación
de T5 usaba la ruta equivocada.

## 4. Admin cancela una suscripción y el cliente lo ve reflejado
1. Login como ADMIN, cancela una suscripción de un cliente.
2. Login como ese cliente (o revisar su vista), confirmar que el estado
   cambió a CANCELADA y no puede seguir usando ese plan.

**Estado:** ✅ verificado end-to-end en producción (Railway) — T5, 2026-08-07.
- 2026-08-06: bloqueado (password del admin daba 401).
- 2026-08-07: password del admin reseteada vía SQL (BCrypt cost 12, valor
  censurado en `collab/estado/ACTUAL.md`) → login admin OK.
- Admin cancela la suscripción de `journey.t5.20260806@test.local` (id=9):
  la lista pasa a CANCELADA (evidencia `22-`…`24-` en
  `collab/evidencia/qa-visual/2026-08-06-journeys-3-4/`).
- El cliente ve "No tienes un plan activo" en el dashboard y el check-in
  es rechazado con toast "No tenés un plan activo para registrar tu
  entrada" (400) (evidencia `25-`…`27-`).
- Hallazgo en la auditoría del flujo de borrado (2026-08-07): el proxy
  del frontend crasheaba al reenviar respuestas 204 → el botón Eliminar
  mostraba "No se pudo eliminar" aunque el borrado SÍ ocurría en BD.
  Corregido (fix + test de regresión en `route.ts`, commit `f1ea3a4`);
  push hecho 07/08, deploy a Railway pendiente de confirmar.
- Re-verificación post-fix (2026-08-07): frontend local contra backend de
  prod — el botón Eliminar mostró toast "Usuario eliminado." y el proxy
  devolvió 204 (evidencia `31-reverificacion-eliminar-usuario-204-toast-ok.png`).
- Usuarios de prueba BORRADOS de prod: `triage.journey34.20260806@test.local`
  (id 7), `triage.j3j4.20260806@test.local` (id 8) y
  `journey.t5.20260806@test.local` (id 9, suscripción CANCELADA) — los tres
  con sus suscripciones en cascada. Quedan en prod: admin + 2 smoketests
  (ids 3 y 5) pendientes de la limpieza final para el entregable "app
  vacía" (decisión en `collab/estado/ACTUAL.md`).

---

## Próximos pasos
- [x] Agregar smoke test de Playwright para el Journey 1 (registro → dashboard). (15 jul 2026)
- [x] Re-verificar Journey 1 contra producción (Railway). (15 jul 2026, passed)
- [x] Re-verificar Journey 3 en producción (Railway) tras el deploy del 14 jul 2026. (06 ago 2026, T5, passed)
- [x] Decidir si Journey 2 necesita endpoint de cambio de rol o se deja solo admin-por-SQL. (resuelto: existe PATCH /api/usuarios/{id}/rol desde 2026-08-03)
- [x] Re-ejecutar Journey 4 en producción. (06 ago 2026: bloqueado por password; 07 ago 2026: ✅ verificado end-to-end tras reset de password + fix del proxy)
- [x] Borrar usuarios de prueba de prod. (07 ago 2026: ids 7, 8 y 9 borrados con sus suscripciones en cascada — id 9 vía botón Eliminar durante la re-verificación del fix 204)
- [ ] Limpieza final de prod para el entregable "app vacía": borrar los 2 smoketests restantes (ids 3 y 5) — decisión de entrega en `collab/estado/ACTUAL.md`.
