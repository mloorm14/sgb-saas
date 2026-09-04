# ADR-029 — Hueco V29 nunca asignada (V28 → V30)

## Estado
Aceptada — 2026-09-04

## Contexto
`database/migrations/` contiene 41 migraciones versionadas `V1..V28` + `V30..V41` + `R__`. `V29` nunca existió (`git log --all -- "*V29*"` vacío). `V28__config_reservas_max_y_hora_limite.sql` y `V30__backup_tablas.sql` son consecutivos reales.

## Decisión
No se crea `V29` placeholder. `backend-springboot/src/main/resources/application.yml:38` `flyway.baseline-version: 37` + `baseline-on-migrate: true` engloba `V1..V37` como ya aplicadas en producción (`db/schema.sql` snapshot). El hueco `V29` nunca se intenta migrar; `flyway validate` no falla. Trazabilidad queda en `V37__indices_optimizacion.sql:2`, `application.yml:37` y `sgb-flyway-migrations/SKILL.md:54`.

## Consecuencias
Impacto 0 funcional. Hueco visible pero documentado. Si en el futuro se requiere numeración correlativa perfecta, crear `V29` sería no-op por baseline y generaría checksums divergentes según origen (reprovisionado vs baselined).
