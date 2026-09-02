---
name: sgb-frontend-conventions
description: Convenciones de Angular, Tailwind, y UX ya establecidas en el frontend de SGB-SaaS. Usar SIEMPRE al crear o editar cualquier componente, servicio, ruta o formulario en frontend-angular/ — cubre sintaxis de control de flujo, manejo de errores, selects con IDs numéricos, CSP y assets, rutas anidadas por rol, y copy visible al usuario.
---

# Convenciones de frontend — SGB-SaaS

## Sintaxis y estructura

- Angular standalone, sintaxis `@if`/`@for` — NUNCA `*ngIf`/`*ngFor`.
- `ReactiveFormsModule` para formularios, nunca template-driven.
- Componentes nuevos: `templateUrl` a un `.html` separado — nunca
  `template:` inline en el `.ts`, aunque sea corto.
- Tailwind con los tokens de `tailwind.config.js` únicamente. Nunca un
  color hex, fuente o spacing suelto fuera de esos tokens.
- Componentes nuevos SIEMPRE `standalone: true` (default en Angular 17+).

## Rutas lazy-loaded

Las rutas de dashboard usan lazy-loading via `loadComponent` para
mejorar el tiempo de carga inicial:

```typescript
// En app.routes.ts
{
  path: 'dashboard-bibliotecario',
  component: DashboardBibliotecarioComponent,
  canActivate: [authGuard, roleGuard(['BIBLIOTECARIO'])],
  children: [
    { path: '', component: DashboardBibliotecarioHomeComponent },
    {
      path: 'libros',
      loadComponent: () => import('./admin/libros/libros.component')
        .then(m => m.LibrosComponent)
    },
  ]
}
```

**Regla:** Si el componente es pesado (>500 líneas), usar
`loadComponent`. Si es liviano, importarlo directamente.

## Manejo de errores en services

Cada service de dominio maneja errores con el mismo patrón `catchError`
+ `ProblemDetail` (RFC 7807: `type`, `title`, `status`, `detail`,
`errores?`) que ya usan los servicios existentes (`libro.service.ts`,
`prestamo.service.ts`, etc.). Mirá uno existente antes de escribir uno
nuevo — no inventes un patrón distinto.

## Selects con valores numéricos (IDs)

Usá `[ngValue]`, NUNCA `[value]`, en cualquier `<select>` cuyo valor
real sea un `number` (editorialId, idiomaId, estadoId, etc.):

```html
<!-- MAL: [value] compara todo como string, puede no preseleccionar
     al editar un registro existente -->
<option [value]="item.id">{{ item.nombre }}</option>

<!-- BIEN -->
<option [ngValue]="item.id">{{ item.nombre }}</option>
```

## Content-Security-Policy y assets

El proyecto tiene una CSP real y restrictiva
(`frontend-angular/public/_headers`). NUNCA uses CDNs externos (Google
Fonts, imágenes de terceros, librerías por `<script src="https://...">`)
— se rompe en producción aunque funcione en local con internet libre.
Todo asset (fuentes, íconos) va auto-hospedado en `src/assets/`. Si
necesitás mostrar una imagen de una API externa (ej. portada de Google
Books), el backend debe hacer de proxy — nunca pongas la URL externa
directo en un `<img src>`.

## Rutas anidadas por rol (shells)

Cada rol tiene su propio shell con sidebar y rutas hijas anidadas:
`/dashboard-lector`, `/dashboard-bibliotecario`, `/dashboard-admin`.
Cualquier `routerLink` interno entre pantallas del MISMO rol debe llevar
el prefijo completo del shell:

```html
<!-- MAL: saca al usuario del shell nuevo sin que se note -->
<a routerLink="/catalogo">...</a>

<!-- BIEN -->
<a routerLink="/dashboard-lector/catalogo">...</a>
```

Antes de dar por terminada una pantalla nueva dentro de un shell, grepeá
tus propios `routerLink` para confirmar que ninguno quedó sin el
prefijo.

## Copy visible al usuario

NUNCA texto técnico como subtítulo o descripción de pantalla — nada de
URLs de endpoint (`GET /api/v1/...`), verbos HTTP, ni nombres de rol
tipo "BIBLIOTECARIO y GERENTE" visible al usuario final. Esa info va en
un comentario de código. La pantalla lleva lenguaje natural en español,
por ejemplo: "Administrá los libros disponibles en la biblioteca" en vez
de "GET /api/v1/libros — BIBLIOTECARIO y GERENTE".

## Mockups de referencia

Si una tarea menciona mockups HTML, están en `docs/mockups/rama-b/`,
numerados correlativamente. Son SOLO guía visual — nunca copies el HTML
literal a un componente Angular real, nunca los uses como fuente de
datos real.
