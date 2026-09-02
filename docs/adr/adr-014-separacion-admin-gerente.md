# ADR-014: Separación de responsabilidades entre ADMIN y GERENTE

## Estado

Aceptado

## Contexto

El catálogo de roles (`roles`: LECTOR, BIBLIOTECARIO, GERENTE, ADMIN) ya
existe desde el modelo de datos original, pero hasta el Módulo 5 no había
ningún endpoint que necesitara distinguir explícitamente entre GERENTE y
ADMIN: ambos roles aparecían juntos en los `@PreAuthorize` existentes
(p.ej. `hasAnyRole('GERENTE','ADMIN')` en `MultaController.anular`).

Al construir el panel de administración de usuarios (`UsuarioAdminController`)
surge una pregunta concreta: ¿quién puede cambiar el rol o el estado de
otro usuario? Dar esa capacidad a GERENTE junto con ADMIN sería seguir el
patrón usado hasta ahora, pero tiene una implicación distinta a anular una
multa o generar un reporte: cambiar el rol de un usuario es, en la
práctica, la capacidad de otorgarse (o de otorgarle a un tercero) cualquier
otro permiso del sistema — incluido ADMIN.

## Decisión

Se separan las dos responsabilidades:

- **GERENTE** administra la **operación diaria**: catálogo, inventario,
  reportes, y — en el caso de este módulo — puede **listar** usuarios
  (`GET /api/v1/admin/usuarios`) para consultar el padrón, ver quién tiene
  multas pendientes, etc.
- **ADMIN** administra **permisos y configuración del sistema**: es el
  único rol que puede cambiar el rol de un usuario
  (`PATCH /api/v1/admin/usuarios/{id}/rol`) o su estado
  (`PATCH /api/v1/admin/usuarios/{id}/estado`). Mismo criterio ya aplicado
  en `ConfiguracionSistemaController` (ver `adr-006-acceso-datos-orm-sp.md`
  y el propio controller): ADMIN es quien toca lo que afecta al sistema
  como un todo, no la operación de un día concreto.

Esta separación no es simétrica a propósito: GERENTE nunca queda excluido
de *ver* el padrón (lo necesita para su trabajo diario), pero sí de
*modificar* permisos.

## Consecuencias

- `UsuarioAdminController.listar` usa `hasAnyRole('ADMIN','GERENTE')`.
- `UsuarioAdminController.cambiarRol` y `.cambiarEstado` usan
  `hasRole('ADMIN')` exclusivamente — un GERENTE que lo intente recibe 403
  (ver `UsuarioAdminControllerSecurityTest`).
- Cualquier módulo futuro que agregue una capacidad de "cambiar permisos o
  parámetros globales" debe seguir este mismo criterio (ADMIN-only) en vez
  de agrupar con GERENTE por comodidad, para no diluir esta separación caso
  por caso.
- Como contrapartida, si en el futuro se requiere que un GERENTE pueda
  bloquear/activar usuarios sin pasar por un ADMIN (p.ej. para agilizar la
  operación en sucursal), eso requiere una decisión explícita nueva, no una
  ampliación silenciosa de este `@PreAuthorize`.
