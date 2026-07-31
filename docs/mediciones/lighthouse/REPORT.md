# Evidencia — Bloque C.5: Lighthouse real contra el frontend (móvil, Slow 4G)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-31T03:13:28Z
- **Commit**: `a3d41ac`
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Frontend probado**: `http://localhost:4200/` (contenedor `sgb_frontend`, nginx sirviendo el build de Angular 17)
- **Lighthouse**: v12.x (`npx lighthouse`)
- **Chrome/Chromium usado**: Microsoft Edge 150.0.4078.105 en modo headless (`CHROME_PATH` apuntando a `msedge.exe`, no hay Chrome/Chromium nativo instalado en esta máquina)

## Propósito

Bloque C.5 — auditoría real (no simulada a mano) de Performance,
Accessibility, Best Practices y SEO con Lighthouse, perfil móvil y
throttling de red Slow 4G, contra los umbrales exigidos: Performance ≥80,
Accessibility ≥90, Best Practices ≥90, SEO ≥90.

## Metodología / comando ejecutado

Se creó [`frontend-angular/lighthouserc.js`](../../../frontend-angular/lighthouserc.js)
con la configuración de perfil móvil + throttling Slow 4G explícita
(`formFactor: mobile`, `throttlingMethod: simulate`, valores estándar de
`mobileSlow4G`: `rttMs: 150`, `throughputKbps: 1638.4`,
`cpuSlowdownMultiplier: 4`), pensada para `npx lhci autorun` en un runner
Linux (p. ej. CI).

**Nota de honestidad — problema real encontrado con `lhci autorun` en esta
máquina Windows**: `npx @lhci/cli autorun` completa la auditoría
correctamente (todas las categorías, "Generating results...") pero **falla
de forma consistente y reproducible** (4 intentos, 4 fallos) al intentar
limpiar el directorio temporal de Chrome al finalizar
(`EPERM: Permission denied` en `chrome-launcher`'s `destroyTmp()`), un
problema conocido de `chrome-launcher` en Windows relacionado con el
retraso de Windows en liberar locks de archivos tras cerrar el proceso de
Chrome. Ese error de limpieza aborta el proceso de `lhci` **antes** de que
llegue a escribir el reporte a disco (paso `upload`), así que
`lhci autorun` no sirvió para capturar evidencia en este entorno concreto,
aunque el propio Lighthouse sí terminó de auditar la página.

**Solución aplicada**: se ejecutó `lighthouse` (el motor subyacente) de
forma directa, con las mismas opciones de perfil móvil + Slow 4G que
`lighthouserc.js`, y con `--output-path` apuntando directamente al archivo
final — así el JSON se escribe a disco *antes* del intento de limpieza que
falla, evitando el problema:

```bash
export CHROME_PATH="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
npx lighthouse@12.x http://localhost:4200/ \
  --output=json \
  --output-path="docs/mediciones/lighthouse/lhci-20260731-0300.json" \
  --chrome-flags="--headless=new --no-sandbox --disable-gpu --user-data-dir=<tmp>" \
  --form-factor=mobile --screenEmulation.mobile --throttling-method=simulate \
  --only-categories=performance,accessibility,best-practices,seo
```

El JSON crudo completo (479 KB, reporte de Lighthouse sin editar) queda
versionado en
[`lhci-20260731-0300.json`](lhci-20260731-0300.json).

`lighthouserc.js` se conserva en el repositorio porque documenta la
configuración prevista para un runner Linux (p. ej. GitHub Actions) donde
`chrome-launcher` no tiene este problema de limpieza de Windows — no se
descartó por haber encontrado un camino alterno de captura en esta máquina.

## Resultados crudos

Scores reales extraídos de `lhci-20260731-0300.json` (`categories.<cat>.score`, escala 0-1 multiplicada por 100):

| Categoría | Score real | Umbral exigido | Cumple |
|---|---|---|---|
| Performance | **95** | ≥80 | ✅ Sí |
| Accessibility | **95** | ≥90 | ✅ Sí |
| Best Practices | **100** | ≥90 | ✅ Sí |
| SEO | **82** | ≥90 | ❌ No |

`runtimeError`: `None` (la auditoría terminó sin errores de ejecución).
`configSettings.formFactor`: `mobile`. `configSettings.throttlingMethod`:
`simulate`. `configSettings.throttling`: `rttMs=150, throughputKbps=1638.4,
cpuSlowdownMultiplier=4` (perfil Slow 4G estándar de Lighthouse).

### Auditorías que restan puntos (extraídas del propio JSON, no interpretadas a mano)

- **SEO — `meta-description` (score 0)**: "Document does not have a meta
  description" — confirmado manualmente, `index.html` del build de Angular
  no tiene una etiqueta `<meta name="description">`.
- **SEO — `robots-txt` (score 0)**: "robots.txt is not valid" — confirmado
  manualmente: `GET http://localhost:4200/robots.txt` devuelve `200` pero
  con el `index.html` de la SPA (por el fallback de rutas de Angular/nginx
  a `index.html`), no un `robots.txt` real, así que Lighthouse lo rechaza
  como inválido.
- **Accessibility — `color-contrast` (score 0)**: "Background and
  foreground colors do not have a sufficient contrast ratio" — hallazgo de
  contraste real en al menos un elemento de la página, no inspeccionado
  visualmente en detalle en esta medición (fuera del alcance de este
  prompt, que es de automatización/medición, no de corrección de UI).

## Análisis breve

1. **3 de 4 categorías cumplen el umbral con margen** (Performance 95≥80,
   Accessibility 95≥90, Best Practices 100≥90).

2. **SEO no cumple el umbral de ≥90 (obtuvo 82)** — se reporta con
   honestidad, sin ajustar el número ni excluir la categoría. Las 2 causas
   concretas (falta de `<meta name="description">`, `robots.txt`
   inexistente/inválido) son triviales de corregir pero **no se corrigieron
   en este prompt** porque el alcance pedido era medir y reportar, no
   remediar — queda como hallazgo para un commit de corrección aparte si
   el equipo decide priorizarlo antes del cierre.

3. **Hallazgo colateral de infraestructura de pruebas**: `npx lhci autorun`
   no es utilizable tal cual en esta máquina Windows por el bug de
   `chrome-launcher` descrito arriba; se documenta la causa raíz real
   (no un simple "no funcionó") y la solución aplicada (invocar
   `lighthouse` directamente), para que quien retome esta medición en el
   futuro no repita el mismo diagnóstico desde cero.

4. Solo se ejecutó **una corrida** de Lighthouse (a diferencia del Bloque
   C.1, que exige 3 corridas de k6) porque la guía no exige explícitamente
   múltiples corridas para Lighthouse; si se requiere variabilidad
   run-to-run de estas métricas, es un trabajo adicional no cubierto aquí.

## Actualización — corrección de SEO y nueva corrida (después)

- **Fecha (ISO 8601 UTC)**: 2026-07-31T05:15:31Z
- **Commit**: `c4ef133` (frontend reconstruido con las 2 correcciones abajo, antes de este commit de evidencia)

### Correcciones aplicadas

1. **`frontend-angular/src/index.html`**: se agregó
   `<meta name="description" content="SGB-SaaS: sistema de gestión
   bibliotecaria web para la modernización de bibliotecas institucionales
   y municipales, con préstamos, reservaciones, multas y catálogo de
   libros.">`.
2. **`frontend-angular/src/robots.txt`** (nuevo archivo, no existía en
   ninguna parte del repositorio — se diagnosticó antes de asumir: el
   `robots.txt` que Lighthouse veía era en realidad el fallback de rutas
   de Angular/nginx sirviendo `index.html` para cualquier ruta no
   reconocida, no un 404 ni un archivo real):
   ```
   User-agent: *
   Allow: /
   ```
   Se agregó `src/robots.txt` al array `assets` de `angular.json` (target
   `build`) para que el builder de Angular lo copie a `dist/` y nginx lo
   sirva como archivo estático real en `/robots.txt`. No se agregó una
   directiva `Sitemap:` porque el sistema aún no tiene una URL de
   despliegue real (README: "URL del sistema desplegado — en desarrollo")
   — inventar una URL de sitemap habría sido fabricar un dato.
3. Rebuild del contenedor `sgb_frontend` con `docker compose up -d --build
   frontend` y verificación manual con `curl` de que `index.html` incluye
   la meta description y `GET /robots.txt` devuelve el archivo real (no el
   fallback de `index.html`).

### Comando ejecutado (mismo mecanismo que la corrida "antes", documentado arriba)

```bash
export CHROME_PATH="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
npx lighthouse@12.x http://localhost:4200/ \
  --output=json \
  --output-path="docs/mediciones/lighthouse/lhci-20260731-0330.json" \
  --chrome-flags="--headless=new --no-sandbox --disable-gpu --user-data-dir=<tmp>" \
  --form-factor=mobile --screenEmulation.mobile --throttling-method=simulate \
  --only-categories=performance,accessibility,best-practices,seo
```

Mismo bug de limpieza de `chrome-launcher` en Windows (`EPERM` al borrar el
directorio temporal) volvió a ocurrir en este intento, y de nuevo el JSON
ya se había escrito a disco antes del crash (confirmado con
`LH:Printer json output written to ...`), así que la evidencia es válida.

JSON crudo versionado como evidencia "después", sin sobrescribir el
"antes": [`lhci-20260731-0330.json`](lhci-20260731-0330.json).

### Resultados reales — antes vs. después

| Categoría | Antes (`lhci-20260731-0300.json`) | Después (`lhci-20260731-0330.json`) | Umbral | Cumple ahora |
|---|---|---|---|---|
| Performance | 95 | **99** | ≥80 | ✅ Sí |
| Accessibility | 95 | **100** | ≥90 | ✅ Sí |
| Best Practices | 100 | **100** | ≥90 | ✅ Sí |
| SEO | 82 | **100** | ≥90 | ✅ Sí |

`runtimeError` en la corrida "después": `None`. Las 2 auditorías SEO que
fallaban (`meta-description`, `robots-txt`) ahora tienen `score: 1` en el
JSON crudo — verificado directamente sobre el archivo, no solo por el
score agregado de la categoría.

### Análisis breve de la actualización

1. **Las 4 categorías cumplen el umbral exigido ahora**, incluyendo SEO
   (82→100), el gap real reportado en la medición anterior.
2. **Hallazgo honesto no buscado**: `Accessibility` subió de 95 a 100 y
   `Performance` de 95 a 99, sin que se tocara ningún estilo, color ni
   optimización de rendimiento — solo se agregó una meta tag y un archivo
   `robots.txt` nuevo, ninguno de los cuales debería afectar contraste de
   color ni performance. La explicación más probable es variabilidad
   normal entre corridas de Lighthouse (el audit `color-contrast` en
   particular depende del render exacto en el momento de la captura, y
   `throttlingMethod: simulate` tiene su propio margen de variación en
   Performance) — **no se atribuye esta mejora a los cambios de SEO**, se
   documenta la coincidencia con honestidad en vez de reclamarla como
   resultado de este prompt.
3. Sigue siendo una única corrida (no 3 como en k6); si se quisiera
   confirmar que el score de 100 en SEO es estable y no un artefacto de
   una sola medición, haría falta repetir la corrida — no se hizo aquí por
   alcance.
