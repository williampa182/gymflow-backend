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

## 3. Cliente se suscribe a un plan
1. Login como CLIENTE.
2. Ve lista de planes disponibles.
3. Se suscribe a uno.
4. Ve su suscripción activa reflejada en su panel.
5. (Admin) ve esa suscripción reflejada en `/dashboard/admin/estadisticas`.

**Estado:** verificado en Fase 3/4, pero no re-verificado en producción (Railway)
después del deploy. Pendiente re-confirmar en producción.

## 4. Admin cancela una suscripción y el cliente lo ve reflejado
1. Login como ADMIN, cancela una suscripción de un cliente.
2. Login como ese cliente (o revisar su vista), confirmar que el estado
   cambió a CANCELADA y no puede seguir usando ese plan.

**Estado:** no verificado end-to-end explícitamente, solo a nivel de API/unit tests.

---

## Próximos pasos
- [x] Agregar smoke test de Playwright para el Journey 1 (registro → dashboard). (15 jul 2026)
- [x] Re-verificar Journey 1 contra producción (Railway). (15 jul 2026, passed)
- [ ] Re-verificar Journey 3 en producción (Railway) tras el deploy del 14 jul 2026.
- [ ] Decidir si Journey 2 necesita endpoint de cambio de rol o se deja solo admin-por-SQL.
