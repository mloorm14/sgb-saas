---
name: sgb-backend-conventions
description: Convenciones de Spring Boot, JPA, seguridad por rol, y patrones de auditoría ya establecidos en el backend de SGB-SaaS. Usar SIEMPRE al crear o editar cualquier controller, service, repository o entidad en backend-springboot/ — cubre la regla de nunca inventar contratos, el patrón de projections nativas, el bug de PostgreSQL con parámetros NULL, y el patrón de auditoría.
---

# Convenciones de backend — SGB-SaaS

## Regla de oro: nunca inventes un contrato

Antes de asumir un campo de un DTO, un endpoint, un rol permitido en
`@PreAuthorize`, un nombre de método o de tabla — LEÉLO en el código
real (`backend-springboot/src`). Investigaciones de tareas anteriores
pueden estar desactualizadas (el código avanza rápido en este proyecto,
con varios compañeros trabajando en paralelo) — confirmá siempre contra
el código actual, no confíes en lo que diga una tarea vieja o un
resumen anterior si contradice lo que ves al abrir el archivo.

## Entidades sin relaciones JPA (a propósito)

Varias entidades (`Prestamo`, `Reservacion`, etc.) NO tienen
`@ManyToOne`/`@OneToMany` a propósito — es una decisión arquitectónica
documentada en el Javadoc de la propia entidad ("repositorio CRUD libre
de joins"). Para resolver nombres/títulos relacionados (ej. mostrar el
título del libro de una reservación), usá una projection nativa con
`@Query(nativeQuery = true)` y una interfaz `XxxProjection` en
`repository/projection/` — mismo patrón que ya usa
`PrestamoActivoProjection`. No agregues una relación JPA nueva para
resolver esto, no es el patrón del proyecto.

## Bug conocido: PostgreSQL + parámetro NULL en JPQL

El patrón `(:param IS NULL OR columna = :param)` en JPQL con Hibernate
sobre PostgreSQL puede fallar con `could not determine data type of
parameter` cuando el parámetro es NULL sin contexto de tipo (pasa
típicamente cuando TODOS los filtros de un endpoint vienen vacíos). Si
escribís un filtro opcional así, usá query nativa con cast explícito:

```java
@Query(value = "SELECT * FROM tabla t WHERE " +
    "(CAST(:param AS bigint) IS NULL OR t.columna = CAST(:param AS bigint)) ...",
    nativeQuery = true)
```

## Seguridad por rol

Roles reales: `LECTOR`, `BIBLIOTECARIO`, `GERENTE`, `ADMIN`. Nunca
asumas qué rol tiene acceso a qué endpoint por el nombre o por lo que
"tendría sentido" — leé el `@PreAuthorize` real de cada método. La
matriz de acceso NO es simétrica ni obvia (ejemplos reales de este
proyecto: ADMIN no tiene acceso a Reportes/Préstamos/Reservaciones/
Multas; BIBLIOTECARIO sí veía Reportes pero se le quitó por decisión de
jerarquía; GERENTE y ADMIN comparten gestión de usuarios/auditoría pero
no reportes). Confirmá siempre el `@PreAuthorize` real antes de replicar
un patrón de acceso de un endpoint a otro.

## Patrón de auditoría

Cualquier acción de negocio real (crear, actualizar, eliminar, cambiar
estado) en un service debe registrar un evento en `bitacora_auditoria`,
mismo patrón que ya usan los services existentes:

```java
private void registrarAuditoria(Long ejecutorId, Long registroId, String detalles) {
    BitacoraAuditoria evento = BitacoraAuditoria.builder()
            .usuarioId(ejecutorId)
            .tipoOperacion("INSERT" /* o UPDATE/DELETE */)
            .tablaAfectada(TABLA_XXX)
            .registroId(registroId)
            .detalles(detalles)
            .fechaHora(OffsetDateTime.now())
            .build();
    bitacoraAuditoriaRepo.save(evento);
}
```

Antes de agregar auditoría a un método nuevo, verificá si el service ya
tiene una constante `TABLA_XXX` y un método `registrarAuditoria` — no
dupliques el patrón con otro nombre. Si estás agregando un método
nuevo a un service que ya audita otras acciones (ej. agregás `anular()`
a un service que ya audita `pagar()`), confirmá que AMBOS terminen
llamando a auditoría de forma simétrica — es un bug común en este
proyecto que un método hermano quede sin instrumentar.

## Catálogos de referencia (Editorial, Idioma, Estado*)

Antes de agregar un `<select>` en el frontend para un campo que en
realidad es un ID de catálogo (editorial, idioma, estado de libro,
estado de préstamo, estado de reservación), confirmá si ya existe un
`CatalogoController`/endpoint que lo exponga. Estas tablas suelen
existir en el backend como entidad+repository pero sin controller — es
un gap real y recurrente en este proyecto, no asumas que porque la
entidad existe ya hay endpoint.
