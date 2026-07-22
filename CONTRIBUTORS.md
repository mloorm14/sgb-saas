# Contribuidores — SGB-SaaS

Roles asignados según la taxonomía [CRediT](https://credit.niso.org/) (14
roles estándar). Este archivo se basa en el **trabajo real commiteado
hasta la fecha** (ver `git log`), no en el rol nominal de cada integrante
en el equipo — un rol CRediT solo se asigna si hay evidencia concreta de
que esa persona lo ejerció.

> **Nota de honestidad y alcance**: esta entrega (Tercera Entrega) está en
> curso. Backend de Préstamos/Reservas (Cajas) y ajustes de frontend
> (Panama) sobre ese módulo aún no están commiteados al momento de
> escribir este archivo. Este documento **debe revisarse y actualizarse
> al cierre de la entrega**, cuando el trabajo de los 3 integrantes esté
> completo — no antes, y no se anticipan roles que todavía no se han
> ejercido.

## Marlon Loor Medranda (Loor Medranda, Marlon Taylor)

Rol nominal en el equipo: Tech Lead / DevOps / Seguridad.

- **Software** — implementación de autenticación JWT+RBAC, cookies
  HttpOnly, blacklist de tokens en Redis, cache del catálogo con TTL
  externo, corrección de `GlobalExceptionHandler`/RFC 7807, pinning de
  imágenes Docker por digest, scripts de build (`build-init-sql.sh`,
  `mediciones-header.sh`).
- **Project administration** — coordinación de la estrategia de ramas,
  ADRs, esquema de versionado y estructura de `docs/`.
- **Supervision** — revisión y corrección de hallazgos de QA sobre
  trabajo de otros integrantes (ver commits `00ecff4`, `1c30b2e`, entre
  otros).
- **Validation** — verificación en vivo de cada cambio de seguridad/cache
  contra el stack Docker real, documentada en `docs/mediciones/sec/`.

## Irvin Cajas Ibarra (Cajas Ibarra, Irvin Marcelo)

Rol nominal en el equipo: Backend (CRUD/Préstamos).

- **Software** — CRUD de libros (backend), en desarrollo el módulo de
  Préstamos/Reservas/Multas.

> Sin evidencia commiteada aún de otros roles CRediT (ej. Validation,
> Data curation) para este integrante — se agregan cuando corresponda a
> trabajo real, no antes.

## Moises Panama Murillo (Panama Murillo, Moises Antonio)

Rol nominal en el equipo: Frontend.

- **Software** — módulos Angular de autenticación (login/registro,
  guards, interceptor JWT) y CRUD de libros (frontend).

> Sin evidencia commiteada aún de **Visualization** (ej. dashboards o
> reportes visuales) para este integrante en el momento de escribir este
> archivo — se agrega cuando el módulo correspondiente exista, no se
> anticipa.

## Referencias

- CRediT Contributor Roles Taxonomy: https://credit.niso.org/
- `CITATION.cff` (afiliación y nombres completos en formato CFF)
- `git log --oneline` (evidencia de autoría de cada commit)
