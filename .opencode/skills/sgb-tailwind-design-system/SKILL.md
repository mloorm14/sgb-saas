---
name: sgb-tailwind-design-system
description: Design system de Tailwind CSS en SGB-SaaS — tokens de color MD3, tipografía, espaciado, dark mode, y container queries. Usar SIEMPRE al crear o estilizar componentes Angular — cubre los tokens disponibles en tailwind.config.js, cómo usar dark mode, y las restricciones de CSP para assets.
---

# Design System — SGB-SaaS

## Regla de oro: solo tokens de tailwind.config.js

NUNCA usar colores hex, fuentes o spacing suelto fuera de los tokens definidos en `tailwind.config.js`. Si un token no existe, agregalo al config antes de usarlo.

## Tokens de color (Material Design 3)

### Colores principales

| Token | Uso | Valor |
|-------|-----|-------|
| `primary` | Botones principales, links, acentos | `#003694` |
| `secondary` | Elementos secundarios | `#006b5f` |
| `tertiary` | Acentos terciarios | `#503a00` |
| `error` | Estados de error | `#ba1a1a` |
| `surface` | Fondo general | `#f8f9ff` |
| `surface-container` | Contenedores, cards | `#e6eeff` |
| `primary-container` | Botones primary rellenos | `#1e4db7` |

### Colores funcionales

| Token | Uso | Valor |
|-------|-----|-------|
| `success` | Estado positivo (>3 días restantes) | `#1d9e75` |
| `warning` | Estado de advertencia (1-3 días) | `#fec004` |

### Tokens `on-*`

Cada color principal tiene un token `on-*` para el color del texto encima:
- `text-on-primary` (texto sobre `primary`)
- `text-on-primary-container` (texto sobre `primary-container`)
- `text-on-surface` (texto sobre `surface`)
- `text-on-surface-variant` (texto secundario sobre `surface`)

### Tokens `*-fixed` y `*-variant`

- `primary-fixed`, `secondary-fixed`, `tertiary-fixed` — variantes fijas para fondos
- `primary-variant`, `secondary-variant`, `tertiary-variant` — variantes para bordes, icons

### Tokens inversos

- `inverse-surface` — fondo para snackbar/toast
- `inverse-on-surface` — texto sobre inverse-surface
- `inverse-primary` — elementos primarios sobre inverse-surface

## Tipografía

| Familia | Uso | Tailwind class |
|---------|-----|----------------|
| Plus Jakarta Sans | Headlines (xl, lg, lg-mobile, md) | `font-headline-xl`, `font-headline-lg`, `font-headline-md` |
| Inter | Body (lg, md, sm) + Labels (lg, md, sm) | `font-body-lg`, `font-body-md`, `font-body-sm`, `font-label-lg`, `font-label-md`, `font-label-sm` |

**REGLA:** No existe `font-headline` ni `font-body` genérico. Siempre
usar el token con tamaño: `font-headline-lg` (no `font-headline`),
`font-body-md` (no `font-body`). Las fuentes están auto-hospedadas en
`src/assets/fonts/` (CSP-compliant).

### Tamaños de fuente

| Token | Tamaño | Uso |
|-------|--------|-----|
| `headline-xl` | 2.25rem | Títulos de página |
| `headline-lg` | 1.75rem | Secciones |
| `headline-md` | 1.5rem | Sub-secciones |
| `headline-sm` | 1.25rem | Cards, encabezados |
| `body-lg` | 1rem | Texto principal |
| `body-md` | 0.875rem | Texto secundario |
| `body-sm` | 0.75rem | Captions, metadata |
| `label-lg` | 0.875rem | Botones grandes |
| `label-md` | 0.75rem | Botones, inputs |
| `label-sm` | 0.6875rem | Badges, chips |

## Espaciado

| Token | Valor | Uso |
|-------|-------|-----|
| `gutter` | 24px | Espaciado general entre secciones |
| `container-max` | 1280px | Ancho máximo de containers |
| `margin-mobile` | 16px | Margen en mobile |

## Border radius

| Token | Valor |
|-------|-------|
| `DEFAULT` | 0.125rem |
| `lg` | 0.25rem |
| `xl` | 0.5rem |
| `full` | 0.75rem |

## Dark mode

El proyecto usa dark mode con estrategia `"class"`:

```html
<!-- Activar dark mode en el body -->
<html class="dark">

<!-- Usar tokens dark mode en Tailwind -->
<div class="bg-surface dark:bg-inverse-surface">
  <span class="text-on-surface dark:text-inverse-on-surface">
    Texto que se adapta
  </span>
</div>
```

## Ejemplo de componente completo

```html
<!-- Card de libro -->
<div class="bg-surface-container rounded-xl p-4 shadow-sm">
  <h3 class="font-headline text-headline-sm text-on-surface">
    Título del Libro
  </h3>
  <p class="font-body text-body-md text-on-surface-variant mt-1">
    Descripción del libro
  </p>
  <div class="flex gap-2 mt-3">
    <button class="bg-primary text-on-primary font-label-lg px-4 py-2 rounded-full">
      Reservar
    </button>
    <button class="border border-primary text-primary font-label-lg px-4 py-2 rounded-full">
      Ver detalle
    </button>
  </div>
</div>
```

## Restricciones de CSP

- NUNCA usar CDNs externos (Google Fonts, Tailwind CDN, etc.)
- Todo asset va auto-hospedado en `src/assets/`
- Si necesitás una fuente nueva, descargala y agregala en `src/assets/fonts/`
- Si necesitás una imagen externa (ej. portada de Google Books), el backend hace de proxy

## Container queries

El plugin `@tailwindcss/container-queries` está habilitado. Para usar:

```html
<div class="@container">
  <div class="@sm:flex @lg:grid @lg:grid-cols-3">
    <!-- Layout responsive por container, no por viewport -->
  </div>
</div>
```

## Cómo agregar un token nuevo

Si necesitás un color, fuente o spacing que no existe:

1. Abrir `tailwind.config.js`
2. Agregar el token en la sección correspondiente (`theme.extend.colors`,
   `theme.extend.fontFamily`, etc.)
3. Usar SOLO el token nuevo, nunca el valor raw

```javascript
// Ejemplo: agregar un color de acento
theme: {
  extend: {
    colors: {
      'accent': '#ff6b35',  // NUEVO token
    }
  }
}
// Uso: class="bg-accent text-on-surface" (NO class="bg-[#ff6b35]")
```
