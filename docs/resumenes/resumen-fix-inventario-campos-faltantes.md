# Resumen — Fix: Campos faltantes del formulario de inventario

**Fecha:** 2026-08-17 · **Rama:** `fix/inventario-campos-faltantes` (integrada en `demo/interfaces-completas`, merge `682d252`) · **Base:** `feature/panel-administrativo` (la rama `fix/catalogos-y-ux-panel-administrativo` NO existe — ver convergencia pendiente)

## Alcance

Corrección de gaps reales detectados en una auditoría del formulario de inventario de libros contra el backend real (`LibroRequestDTO`/`LibroResponseDTO`/`LibroService`). Los contratos fueron verificados en el código antes de tocar nada. Incluye verificación del estado de la rama `feature/portada-libro-binaria`.

## Qué se hizo (2 commits + integración)

| Commit | Cambio |
|---|---|
| `1fab939` | `feat(backend): ubicacionFisica en LibroRequestDTO y mapeo crear/actualizar` — campo opcional (`@Size(max=50)`, mismo criterio que `resumen`) entre `resumen` y `portadaUrl`; `setUbicacionFisica` en `LibroService.actualizar()` y en `fromDTO()` (crear); `toDTO` ya devolvía el campo. Tests: +2 en `LibroServiceTest` (persistencia vía captor + DTO de respuesta; mapeo en actualizar); `LibroControllerSecurityTest` actualizado al nuevo constructor. **15/15 SUCCESS.** |
| `95c429b` | `feat(frontend): campos faltantes del formulario de inventario (mockup 19)` — ver detalle abajo. Spec +6 casos. **19/19** en libros. |
| `682d252` | Merge limpio (ort) en `demo/interfaces-completas` — conservó la limpieza de código muerto de demo (`authService/router/cerrarSesion` fuera de `libros.component.ts`) y trajo también `c711520` (mockups inventario v3 y dashboard gerente). Pusheado a origin. |

## Detalle del frontend (`95c429b`)

1. **`anioPublicacion` — bug real:** el `FormGroup` ya lo tenía como `Validators.required` pero el HTML nunca renderizaba el `<input>` → un libro creado sin pasar por el autocompletar ISBN quedaba con el form permanentemente inválido sin motivo visible. Input `number` (min 1000, max 2100) junto a Título.
2. **Portada manual:** nuevo `onArchivoPortadaSeleccionado()` que reusa el mismo canal `portadaPreviewBlob`/`portadaPreviewUrl` del autocompletar; whitelist PNG/JPEG/WEBP y 2MB (confirmado `max_tamano_portada_mb = 2` en `V13__portada_imagen.sql` y `application.yml`). Preview único junto al input file (se quitó el `<img>` duplicado del bloque ISBN). `guardarPortadaAutocompletada` → `guardarPortadaPendiente()`: la subida tras guardar no cambia, solo quién llena el Blob; el nombre del archivo se deriva del tipo (`portada.png`).
3. **`ubicacionFisica`:** input de texto libre (maxlength 50, ej. "Estante A-12") con `formControl` nuevo y precarga al editar; `LibroRequest.ubicacionFisica?` en el modelo. Requiere el commit backend `1fab939`.

## Bug `[value]` vs `[ngValue]`

**No estaba presente:** en esta rama editorial/idioma/estado NO son `<select>` — son inputs numéricos de ID (decisión de la Rama F: no existe `EditorialController` en el backend). Aun así, se cambió `[value]` → `[ngValue]` en los dos selects reales del form (categorías/autores) para comparación por valor real al preseleccionar.

## Confirmaciones pedidas (vía tests en `libros.component.spec.ts`)

1. **Crear sin autocompletar ISBN:** test "crea un libro SIN autocompletar ISBN: año tipeado a mano deja el form válido" — `form.valid` true con año manual, `crear()` llamado, `buscarPorIsbn` nunca llamado.
2. **Portada manual:** tests aceptan PNG, rechazan GIF (mensaje exacto "Formato no permitido. Usá PNG, JPEG o WEBP.") y rechazan >2MB; `guardarPortadaPendiente` sube `portada.png` al guardar.
3. **Selects al editar:** tests "precarga ubicacionFisica y los ids de editorial/idioma/estado al editar" (valores exactos `1/1/1`, ubicación `Estante A-12`) y "preselecciona categorías/autores por nombre" (`[1]`, `[7]`).

## Verificación

- Frontend: `ng build` sin errores; suite completa **128/128 SUCCESS** (en demo, post-merge).
- Backend: `LibroServiceTest` **15/15**, BUILD SUCCESS.

## Estado de `feature/portada-libro-binaria` (comprobado, no reimplementado)

- El commit `831c493` es ancestro de `feature/panel-administrativo`.
- `V13__portada_imagen.sql` existe en `database/migrations/`.
- `POST/GET /api/v1/libros/{id}/portada` y `lookup-isbn/portada` ya viven en `LibroController` → el pull ya está integrado; solo faltaba exponerlo en el formulario (hecho en esta tarea: subida manual + preview).

## Notas y convergencia pendiente

- `fix/catalogos-y-ux-panel-administrativo` **no existe** (ni local ni remota): la tarea de selects editorial/idioma/estado (con `[ngValue]` cuando se haga) va a converger con esta rama, que ya toca el mismo formulario.
- `feature/panel-administrativo` tiene el commit `c711520` (mockups dashboard gerente e inventario v3) ya integrado en demo vía este merge; las pantallas correspondientes (dashboard, inventario v3) siguen pendientes de implementar.
- `LibroResponseDTO` ya exponía `ubicacionFisica` antes de esta tarea; la UI de la tabla del inventario aún no la muestra (no pedido).

## Integración

`fix/inventario-campos-faltantes` → `origin/fix/inventario-campos-faltantes`; merge `682d252` en `demo/interfaces-completas` → `origin/demo/interfaces-completas`. Sin PR, sin push a main.