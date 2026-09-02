---
title: Agregar filtro de búsqueda por autor en catálogo
branch: feature/filtro-autor
skills: sgb-backend-conventions, sgb-frontend-conventions, sgb-testing
priority: high
---

# Agregar filtro de búsqueda por autor en catálogo de libros

## Contexto

El catálogo de libros (`GET /api/v1/libros`) solo filtra por título e
ISBN. Los usuarios necesitan buscar por nombre del autor. El campo
`autor` es un VARCHAR en la tabla `libros` (V1__schema_inicial.sql).
No hay endpoint separado para autores — el filtro va en el controller
existente.

## Objetivo

Que el usuario pueda escribir un nombre de autor en el campo de
búsqueda del catálogo y vea solo los libros de ese autor.

## Requisitos

- [ ] Backend: agregar parámetro opcional `autor` al endpoint GET /api/v1/libros
- [ ] Backend: filtro case-insensitive con ILIKE
- [ ] Frontend: campo de texto "Autor" en la barra de filtros del catálogo
- [ ] Frontend: debounce de 300ms en el campo
- [ ] Tests: 1 test de controller + 1 test de frontend

## Restricciones

- **NO tocar:** `PrestamoService`, `ReservacionService`, ningún archivo de migración
- **Branch base:** `demo/interfaces-completas`
- **Convenciones:** sgb-backend-conventions (nunca inventar contratos), sgb-frontend-conventions (@if/@for, Tailwind tokens)
- **Testing:** 1 controller test (@WebMvcTest), 1 frontend test (Jasmine)

## Archivos relevantes

| Archivo | Acción | Notas |
|---------|--------|-------|
| `backend/.../LibroController.java` | editar | agregar param `@RequestParam Optional<String> autor` |
| `backend/.../LibroService.java` | editar | agregar lógica de filtro |
| `backend/.../LibroRepository.java` | editar | agregar query con ILIKE |
| `frontend/.../catalogo.component.ts` | editar | agregar campo autor con debounce |
| `frontend/.../catalogo.component.html` | editar | agregar input en barra de filtros |
| `backend/.../LibroControllerTest.java` | editar | agregar test de filtro |
| `frontend/.../catalogo.component.spec.ts` | editar | agregar test de filtro |

## Pasos sugeridos

1. Activar skill sgb-backend-conventions
2. Leer LibroController actual para ver patrón de filtros existentes
3. Agregar `@RequestParam Optional<String> autor` al endpoint
4. Agregar filtro ILIKE en repository (query nativa si JPQL falla con NULL)
5. Agregar test de controller: filtro con autor retorna solo esos libros
6. Activar skill sgb-frontend-conventions
7. Agregar campo "Autor" al HTML del catálogo
8. Agregar lógica de debounce en el .ts
9. Agregar test de frontend
10. Correr tests: `make test-backend && make test-frontend`
11. Push a `feature/filtro-autor`

## Criterio de aceptación

- [ ] `GET /api/v1/libros?autor=Garcia` retorna solo libros de autores "Garcia"
- [ ] Sin parámetro autor, retorna todos los libros (regresión)
- [ ] Frontend muestra campo "Autor" y filtra con debounce
- [ ] ng build compila sin errores
- [ ] Tests: 285 backend + 197 frontend, 0 fallos
- [ ] Pusheado a `feature/filtro-autor`

## Notas adicionales

- Usar ILIKE (no LIKE) para case-insensitive en PostgreSQL
- Si el parámetro viene vacío, no filtrar (patrón `(:param IS NULL OR ...)` con CAST explícito)
