# Código removido — registro

## RedisCacheConfig.java — removido 2026-07-11

**Por qué:** hallazgo 1.2 del `docs/THREAT_MODEL.md` (sección 7.5). Este
`@Configuration` configuraba un `CacheManager` de Redis con
`GenericJackson2JsonRedisSerializer`, pero desde que se aplicó la propuesta
de paginación (3.3) no quedaba ningún `@Cacheable` activo en el proyecto que
lo usara — era superficie de código muerta. Se removió junto con
`@EnableCaching` en `GymflowBackendApplication.java` y los `@CacheEvict`
residuales en `PlanService.java`.

**Riesgo que mitiga:** que alguien en el futuro reintroduzca `@Cacheable`
sin revisar el modelo de seguridad del serializer (el `@class` metadata en
el JSON, aunque no se demostró explotable con la config actual — ver
Codex Security triage en `collab/propuestas/codex/codex-security-triage-redis-1.1-1.2.md`).

**Si en algún momento se quiere volver a agregar cache:** no reintroducir
este archivo tal cual. Usar un serializer tipado por cache específica (no
genérico), sin default typing activo, y agregar un test que confirme que un
payload con `@class` inyectado en Redis no instancia tipos arbitrarios.
Ver la alternativa propuesta en `collab/propuestas/codex/1.1-1.2-redis-hardening-propuesta.md`.

El archivo original queda en `RedisCacheConfig.java.txt` en esta misma
carpeta como referencia, no como código activo.
