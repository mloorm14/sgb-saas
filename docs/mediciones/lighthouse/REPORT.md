# Evidencia — Bloque C.5: Lighthouse real contra el frontend (móvil, Slow 4G)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-31T03:13:28Z
- **Commit**: `a3d41ac`
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Frontend probado**: `http://localhost:4200/` (contenedor `sgb_frontend`, nginx sirviendo el build de Angular 21)
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

## Actualización — 3 corridas mobile + 3 desktop contra producción (P4/B.10)

La guía exige explícitamente "al menos tres corridas por perfil (mobile,
desktop)" con media y desviación típica por categoría — un requisito que
las dos corridas anteriores (ambas móvil, ambas contra `localhost`) no
satisfacían en ninguno de los dos ejes (ni el número de corridas, ni el
perfil desktop, ni el objetivo de producción). Esta actualización corre
las 6 corridas que faltaban, contra la URL real de producción.

### Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-08-17T18:59Z – 2026-08-17T19:05Z (las 6
  corridas, secuenciales, ~6 minutos en total)
- **Commit**: `9d9d873`
- **Objetivo probado**: `https://biblora-sgb.onrender.com/` (producción
  real, no `localhost` — a diferencia de las dos corridas anteriores)
- **Lighthouse**: v13.4.1 (`npx lighthouse`) — versión más nueva que la
  v12.x usada en la corrida original; no se fijó la versión anterior a
  propósito, se documenta el cambio en vez de ocultarlo
- **Chrome/Chromium usado**: Microsoft Edge 151.0.0.0 en modo headless
  (mismo mecanismo `CHROME_PATH` que la corrida original, misma máquina
  Windows sin Chrome nativo instalado)

### Metodología / comandos ejecutados

**Perfil móvil (3 corridas)**: mismo comando que las corridas "antes" y
"después" documentadas arriba (`--form-factor=mobile
--screenEmulation.mobile --throttling-method=simulate`), solo cambiando
la URL a producción y el nombre de archivo. **No se usó
`frontend-angular/lighthouserc.js` cargado directamente** (`lhci
autorun` sigue teniendo el mismo bug de limpieza de `chrome-launcher` en
Windows ya documentado arriba) ni se pasaron a mano los sub-flags
`--throttling.rttMs`, etc. -- en su lugar se confirmó, leyendo
`configSettings.throttling` del JSON resultante después de la primera
corrida, que `--throttling-method=simulate` con `--form-factor=mobile`
ya aplica por defecto los mismos valores estándar de `mobileSlow4G` que
`lighthouserc.js` declara explícitamente (`rttMs=150,
throughputKbps=1638.4, cpuSlowdownMultiplier=4,
downloadThroughputKbps=1474.56`), con una única diferencia menor:
`uploadThroughputKbps` es `675` por defecto de Lighthouse CLI, frente a
`607.5` (`675*0.9`) que fija `lighthouserc.js` explícitamente -- una
diferencia de configuración real, documentada aquí en vez de asumir que
son idénticos sin comprobarlo.

```bash
export CHROME_PATH="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
npx lighthouse https://biblora-sgb.onrender.com/ \
  --output=json \
  --output-path="docs/mediciones/lighthouse/lhci-mobile-prod-<TS>-run<N>.json" \
  --chrome-flags="--headless=new --no-sandbox --disable-gpu --user-data-dir=<tmp-unico-por-corrida>" \
  --form-factor=mobile --screenEmulation.mobile --throttling-method=simulate \
  --only-categories=performance,accessibility,best-practices,seo \
  --max-wait-for-load=60000
```

**Perfil desktop (3 corridas)**: se usó el preset estándar de Lighthouse
(`--preset=desktop`) en vez de reetiquetar la configuración móvil, tal
como exige la tarea -- verificado en el JSON resultante que
`configSettings` realmente cambia de perfil (no solo el nombre de
archivo): `formFactor=desktop`, `throttling.rttMs=40` (vs. `150` en
móvil), `throttling.cpuSlowdownMultiplier=1` (vs. `4`),
`screenEmulation={mobile: false, width: 1350, height: 940,
deviceScaleFactor: 1}` (vs. `360×640` móvil).

```bash
export CHROME_PATH="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
npx lighthouse https://biblora-sgb.onrender.com/ \
  --output=json \
  --output-path="docs/mediciones/lighthouse/lhci-desktop-prod-<TS>-run<N>.json" \
  --chrome-flags="--headless=new --no-sandbox --disable-gpu --user-data-dir=<tmp-unico-por-corrida>" \
  --preset=desktop \
  --only-categories=performance,accessibility,best-practices,seo \
  --max-wait-for-load=60000
```

Cada una de las 6 corridas usó un `--user-data-dir` distinto (evita que
el bug de limpieza de Windows de una corrida interfiera con la
siguiente) y cada una volvió a disparar el mismo `EPERM` de
`chrome-launcher` al limpiar su directorio temporal (excepto las 3
corridas desktop, que esta vez terminaron limpio, sin el error de
limpieza -- variación del propio bug, no algo que se haya cambiado a
propósito). En las 6, el JSON ya estaba escrito a disco
(`LH:Printer json output written to ...`) antes de cualquier fallo de
limpieza, así que las 6 son evidencia válida; se confirmó además
`runtimeError: null` en las 6 leyendo el JSON, no solo por la ausencia
de una excepción en la consola.

Los 6 JSON crudos quedan versionados sin editar:
[`lhci-mobile-prod-20260817-1859-run1.json`](lhci-mobile-prod-20260817-1859-run1.json),
[`lhci-mobile-prod-20260817-1901-run2.json`](lhci-mobile-prod-20260817-1901-run2.json),
[`lhci-mobile-prod-20260817-1903-run3.json`](lhci-mobile-prod-20260817-1903-run3.json),
[`lhci-desktop-prod-20260817-1904-run1.json`](lhci-desktop-prod-20260817-1904-run1.json),
[`lhci-desktop-prod-20260817-1904-run2.json`](lhci-desktop-prod-20260817-1904-run2.json),
[`lhci-desktop-prod-20260817-1905-run3.json`](lhci-desktop-prod-20260817-1905-run3.json).

### Resultados crudos — perfil móvil (Slow 4G), producción

| Categoría | Run 1 | Run 2 | Run 3 | **Media** | **DT** | Umbral | Cumple (media) |
|---|---|---|---|---|---|---|---|
| Performance | 93 | 94 | **71** | **86,0** | **13,0** | ≥80 | ✅ Sí (media), ❌ **No en Run 3** |
| Accessibility | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| Best Practices | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| SEO | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |

`runtimeError` en las 3 corridas: `None`. `finalDisplayedUrl` en las 3:
`https://biblora-sgb.onrender.com/` (confirmado, no hubo redirección
inesperada).

### Resultados crudos — perfil desktop, producción

| Categoría | Run 1 | Run 2 | Run 3 | **Media** | **DT** | Umbral | Cumple (media) |
|---|---|---|---|---|---|---|---|
| Performance | 88 | 88 | 88 | **88,0** | **0,0** | ≥80 | ✅ Sí |
| Accessibility | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| Best Practices | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| SEO | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |

`runtimeError` en las 3 corridas: `None`.

### Hallazgo real — Performance móvil, Run 3 no cumple el umbral (71 < 80)

Se reporta con la misma honestidad que el hallazgo de SEO=82 de la
corrida original, sin descartar la corrida ni promediar el problema
para que desaparezca. Comparando las métricas crudas de las 3 corridas
móviles (`audits.<id>.displayValue` y `.score`, extraídos directamente
del JSON, no estimados):

| Métrica | Run 1 | Run 2 | Run 3 |
|---|---|---|---|
| Speed Index | 2,0 s (score 0,99) | 2,5 s (score 0,98) | **48,1 s (score 0)** |
| Cumulative Layout Shift | 0,013 (score 1) | 0,013 (score 1) | **0,338 (score 0,33)** |
| Server response time (TTFB) | 20 ms | 20 ms | **210 ms** |
| Layout shifts detectados | 3 | 3 | **4** |

La causa concreta, verificada en el propio JSON: en Run 3 aparece un
**cuarto *layout shift* real que no ocurre en Run 1 ni Run 2** —
el panel `div.rounded-xl.glass-panel` ("¿Querés reservar o guardar
favoritos? Con una cuenta gratis accedés...") se desplaza tarde en el
renderizado (score de ese shift individual: 0,338, el más alto de los 4
por sí solo), y el tiempo de respuesta del documento raíz subió 10× (20
ms → 210 ms) frente a las otras dos corridas. Un *layout shift* real y
tardío empuja el cálculo de Speed Index hacia arriba porque Lighthouse
lo interpreta como progreso visual retrasado, lo que explica el salto
de ~2 s a 48,1 s sin que haya un error de red ni un timeout.

**No se atribuye esto a un cambio de código** — las 3 corridas se
ejecutaron en una ventana de ~4 minutos contra el mismo despliegue, sin
ningún commit ni redeploy entre medio. La explicación más plausible,
consistente con la evidencia (TTFB 10× más alto en esa corrida
puntual), es variabilidad real de infraestructura compartida del
hosting de producción (Render, capa gratuita/compartida) en el momento
exacto de esa captura, no un defecto de la aplicación reproducible a
voluntad — pero **el panel que se desplaza tarde sí es un defecto real
de la aplicación** (ver hallazgo de CLS en desktop, abajo, donde el
mismo panel aparece de forma consistente, no solo en una corrida
anómala). Exactamente el tipo de hallazgo que una sola corrida (como la
medición original de este documento) no puede detectar — la media
(86,0) sola habría ocultado que una corrida individual no cumple el
umbral.

### Hallazgo real — CLS consistente en desktop (0,235–0,236, las 3 corridas)

A diferencia del salto puntual de Run 3 en móvil, el perfil desktop
muestra `cumulative-layout-shift` en **0,235–0,236 en las 3 corridas**
(audit score ≈0,53, sin llegar a tumbar la categoría Performance por
debajo de 80 porque el resto de métricas en desktop son muy rápidas:
LCP 0,4–0,7 s, TBT 0 ms sin throttling de CPU). Este valor cae en la
franja "needs improvement" de la curva de Lighthouse para CLS (buena
<0,1, aceptable 0,1–0,25, pobre >0,25) de forma **reproducible, no
anómala** — presente en las 3 corridas desktop por igual, a diferencia
del hallazgo puntual de Run 3 móvil de arriba. Es consistente con el
mismo panel `glass-panel` detectado en la corrida móvil anómala: en el
viewport de escritorio (1350×940, sin throttling de CPU) el contenido
por encima del panel probablemente termina de cargar/reflow más rápido
y de forma más predecible que en móvil, produciendo el mismo
desplazamiento de forma consistente en vez de ocasional. **No se
corrige en este documento** (el alcance de esta tarea es medir y
reportar, no remediar) — queda como hallazgo real para un commit de
corrección aparte (candidato: reservar el espacio del panel con
`min-height` o cargarlo sin desplazamiento de layout, p. ej. con
`aspect-ratio` o placeholder).

### Análisis breve de esta actualización

1. **7 de 8 combinaciones categoría×perfil cumplen su umbral con
   margen** (Accessibility, Best Practices y SEO al 100 en ambos
   perfiles; Performance desktop en 88 estable). La única que no cumple
   es Performance móvil, y solo en 1 de las 3 corridas (Run 3, 71<80) —
   la media (86,0) sí cumple, pero reportar solo la media habría
   ocultado el incumplimiento puntual, exactamente lo que la guía busca
   evitar al exigir 3 corridas con media y DT en vez de una sola cifra.
2. **El ejercicio de 3 corridas por perfil hizo su trabajo**: expuso un
   defecto real y reproducible (el panel CTA que desplaza layout,
   presente de forma consistente en desktop y de forma puntual en
   móvil) que la medición original de una sola corrida contra
   `localhost` no podía detectar.
3. **Cambio de objetivo (localhost → producción)**: no se observa
   ninguna categoría que empeore por el cambio a producción en sí —
   Accessibility/Best Practices/SEO se mantienen en 100 igual que la
   corrida "después" contra `localhost`; Performance móvil (86,0 de
   media) es comparable al 99 de la corrida `localhost` anterior
   considerando que esa corrida fue una única muestra, no una media de
   3.
4. Con esta actualización, el Bloque C.5/P4-B.10 queda con **6 corridas
   reales contra producción** (3 mobile + 3 desktop), cumpliendo el
   requisito explícito de "al menos tres corridas por perfil" con media
   y desviación típica reportadas por categoría — las 2 corridas
   originales contra `localhost` se conservan arriba como evidencia
   histórica del hallazgo y la corrección de SEO, no se eliminan.

## Actualización — investigación y corrección real del defecto de CLS

El hallazgo de CLS de la actualización anterior (desktop ~0,236
consistente en 3/3 corridas; móvil intermitente, 0,013 en 2 corridas y
0,338 en la tercera) se investigó a fondo para identificar el elemento
real responsable -- no se asumió cuál era a partir de la sospecha inicial
("el panel CTA") de la sección anterior.

### Identificación del elemento real (no adivinado)

El audit `cumulative-layout-shift` del JSON crudo no trae el elemento
(solo el score agregado), pero **sí existe un audit separado con el
detalle real, `layout-shifts`** ("Avoid large layout shifts", id
`layout-shifts`, distinto del `layout-shift-elements` que se buscó sin
éxito) -- confirmado corriendo `lighthouse` con `--output=json --output=html`
contra producción y leyendo `audits['layout-shifts'].details.items`
directamente del JSON (no del HTML, aunque el HTML lo confirma también).
Con las 4 instancias de shift de esa corrida:

| # | Elemento (`selector`) | Score del shift | Causa reportada |
|---|---|---|---|
| 1 | `main.max-w-container-max` (el `<main>` completo) | **0,2305** (≈99,9 % del CLS total) | Ninguna (Lighthouse no logra atribuir una causa automática a este) |
| 2 | `h1.font-headline-xl` | 0,0020 | Web font loaded (`plus-jakarta-sans-latin`) |
| 3 | `p.font-body-lg` | 0,0018 | Web font loaded (`inter-latin`) |
| 4 | `input.w-full` | 0,0007 | Web font loaded (`material-symbols-outlined`) |

El elemento dominante (99,9 % del CLS) es el **`<main>` de
`PortalPublicoComponent`, no el panel CTA** que se había señalado como
sospechoso en la sección anterior sin confirmarlo -- se corrige aquí esa
suposición explícitamente en vez de dejarla como si hubiera sido
correcta. Las otras 3 instancias sí son por fuentes web (`font-display:
swap`, ya configurado en `styles.scss`), pero su contribución conjunta
(0,0045) es marginal (<2 % del CLS total) frente al shift de `<main>` --
no se tocó `font-display` por esta razón: el costo de cambiarlo
(`optional` arriesga no aplicar la tipografía en redes lentas) no se
justifica para una ganancia de ese tamaño.

### Causa raíz real (confirmada en el código fuente, no supuesta)

`frontend-angular/src/app/portal-publico/portal-publico.component.html`
(antes de esta corrección) mostraba, mientras `cargando &&
libros.length === 0`, una sola línea de texto: `<p>Cargando
catálogo…</p>`. Cuando `LibroPublicoService.listar()` resolvía,
`libros` (10 elementos, `pageSize=10`) reemplazaba esa línea por el
grid completo (`grid-cols-2 sm:grid-cols-3 md:grid-cols-4
lg:grid-cols-5`, 2 filas de tarjetas `aspect-[3/4]` en escritorio) --
una sola línea de texto (~24 px) creciendo de golpe a un grid de
cientos de píxeles de alto es exactamente el tipo de contenido
"insertado sin espacio reservado" que causa CLS grande, y coincide con
que Lighthouse no pudo atribuirle una causa automática (no es una
fuente ni una imagen sin `width`/`height`, categorías que sí detecta
solo): es un cambio de contenido de la aplicación, no un recurso
externo.

### Fix aplicado

1. **`portal-publico.component.html`**: el grid ahora renderiza, cuando
   `cargando && libros.length === 0`, `skeletonSlots.length` (=10,
   igual a `pageSize`) tarjetas esqueleto con la misma estructura que
   las reales (`aspect-[3/4]` + 2 líneas de texto, `animate-pulse`) en
   el mismo contenedor `grid` -- reservan aproximadamente el mismo alto
   que el grid real, en vez de que "Cargando catálogo…" (una línea) sea
   reemplazado de golpe.
2. **`portal-publico.component.ts`**: se agregó `skeletonSlots =
   Array.from({length: this.pageSize}, ...)`. Además, **`cargando`
   cambió su valor inicial de `false` a `true`** -- este segundo cambio
   fue necesario porque la primera versión del fix (solo el esqueleto,
   con `cargando` arrancando en `false`) **no bajó el CLS en la
   verificación local, lo empeoró** (ver más abajo): con `cargando`
   arrancando en `false`, el primer render de Angular (antes de que
   `ngOnInit` → `cargarPagina()` corra) pintaba la rama `@empty` ("No
   hay libros para mostrar", una línea) y *después* aparecía el
   esqueleto completo -- el salto de "vacío" a "esqueleto" reemplazó al
   salto de "cargando" a "grid real" en vez de eliminarlo. Arrancar en
   `true` pinta el esqueleto ya en el primer frame, sin salto
   intermedio.

### Verificación local -- por qué fue más difícil de lo esperado (documentado con honestidad)

La verificación local no fue directa; se documentan los intentos
fallidos porque explican por qué el número final es confiable y no un
resultado de la primera corrida que "dio bien":

1. **Primer intento (solo esqueleto, `cargando` en `false`)**: CLS
   **subió** a 0,47 en local, peor que producción. Diagnóstico con las
   miniaturas de captura de pantalla del propio trace de Lighthouse
   (`audits['screenshot-thumbnails']`, decodificadas de base64 a JPG e
   inspeccionadas): a los 750 ms aparecía el texto **"Error al cargar
   el catálogo"** en rojo -- la llamada a la API estaba fallando en la
   corrida de verificación.
2. **Investigación del error**: `curl` directo a
   `http://localhost:8080/api/publico/libros` respondía `200` con datos
   reales -- el backend local funcionaba. El error era del navegador:
   `errors-in-console` del JSON de Lighthouse mostraba un rechazo real
   de **CORS** (`Access to XMLHttpRequest ... has been blocked by CORS
   policy`). Causa raíz, en dos capas:
   - El build Docker de producción usa
     `frontend-angular/src/environments/environment.prod.ts`
     (`fileReplacements` de `angular.json`), que apunta
     `apiUrl` al backend real de Render
     (`https://sgb-backend-b058.onrender.com/api`) -- **la imagen
     local nunca llamó al backend local**, llamaba al backend de
     producción por internet real, con la latencia/CORS que eso
     implica. Esto es correcto para la imagen que se despliega, pero
     invalida "levantar el stack local" como prueba aislada sin
     ajustarlo.
   - Al apuntar `apiUrl` temporalmente a `http://localhost:8080/api`
     para probar contra el backend local real, **seguía fallando por
     CORS** -- `SecurityConfig.java` (`corsConfigurationSource()`) solo
     permite `http://localhost:4200` y
     `https://biblora-sgb.onrender.com` como orígenes, y el
     `docker-compose.yml` de esta máquina remapea el frontend a
     `14200:80` (workaround ya documentado en este repo para un rango
     de puertos excluido por Hyper-V/WSL2 en Windows) -- `localhost:14200`
     nunca estuvo en la lista blanca de CORS del backend.
3. **Reproducción limpia**: se corrió `lighthouse` con
   `--chrome-flags="--disable-web-security"` (desactiva CORS **solo en
   el navegador descartable de la prueba**, no en el código de la
   aplicación ni en ningún archivo commiteado) contra el frontend
   apuntando al backend local (`apiUrl` cambiado temporalmente, revertido
   antes de este commit -- `git diff` confirmado limpio contra
   `environment.prod.ts` antes de compilar la imagen final). Con la
   llamada a la API realmente exitosa (`200` confirmado en
   `network-requests` del JSON), los números fueron:

| Corrida | CLS -- código original (antes) | CLS -- código con el fix (después) |
|---|---|---|
| 1 | 0,236 | 0,031 |
| 2 | 0,236 | 0,032 |
| 3 | 0,236 | 0,033 |
| **Media** | **0,236** | **0,032** |
| **DT** | **0,000** | **0,001** |

El código original, en condiciones locales limpias (misma llamada
exitosa, sin el ruido de CORS), reproduce casi exactamente el 0,235-0,236
medido en las 3 corridas desktop de producción -- confirma que el
defecto es real y reproducible, no un artefacto de producción. Con el
fix, CLS baja a ~0,032 de media, **por debajo del umbral de "bueno"
(<0,1)** y una reducción real del 86 % frente al original, con
desviación típica casi nula (0,001) en las 3 corridas.

### Verificación adicional

- **Suite de tests completa**: `npx ng test --watch=false
  --browsers=ChromeHeadless` → **141/141 SUCCESS**, exit 0, sin
  regresiones. Ninguna prueba existente afirmaba sobre el texto
  "Cargando catálogo…" ni sobre el valor inicial de `cargando`.
- **Build de producción**: `ng build --configuration production` →
  exit 0, mismo warning preexistente de presupuesto de bundle (no
  relacionado).
- **Estado de `environment.prod.ts`**: confirmado sin cambios respecto
  al commit anterior (`https://sgb-backend-b058.onrender.com/api`) --
  el apuntado a `localhost` fue exclusivamente para la verificación
  local descrita arriba, nunca se commiteó.

### Confirmación final en producción (después del redeploy)

El commit `fced67d` se pushbeó a `demo/interfaces-completas` y quedó a
la espera de que Render redeployara. Se verificó el redeploy real antes
de correr nada, comparando el hash del bundle servido
(`curl -s "https://biblora-sgb.onrender.com/?cachebust=<ts>"`, con
`cf-cache-status: MISS` confirmado para descartar una respuesta de CDN
cacheada) contra el hash pre-fix conocido:

- Antes del redeploy: `main-CCW77Z7S.js` (el mismo hash pre-fix, visto
  en dos verificaciones espaciadas ~1 hora, incluyendo una vez que
  alguien del equipo reportó de forma informal "ya lo arreglé" sin
  detalles -- no se asumió que el redeploy había ocurrido solo por ese
  comentario, se verificó igual).
- Después del redeploy: `main-KA6MN67F.js` -- **coincide exactamente
  con el hash del build local de la corrección** generado durante la
  verificación local de esta misma sección arriba, confirmando que es
  el mismo código, no un cambio distinto hecho manualmente en el
  dashboard de Render.

Confirmado el redeploy, se corrieron las 6 corridas reales (3 mobile +
3 desktop) contra `https://biblora-sgb.onrender.com/`, mismo comando y
metodología documentados en la sección "3 corridas mobile + 3 desktop
contra producción" de arriba (`--form-factor=mobile
--screenEmulation.mobile --throttling-method=simulate` para móvil,
`--preset=desktop` para desktop, `--only-categories=performance,
accessibility,best-practices,seo`).

- **Fecha (ISO 8601 UTC)**: 2026-08-18T06:02Z – 2026-08-18T06:07Z
- **Commit en producción**: `fced67d`
- **Lighthouse**: v13.4.1, mismo mecanismo `CHROME_PATH` (Edge headless)

Los 6 JSON crudos quedan versionados sin editar:
[`lhci-mobile-prod-20260818-0602-run1.json`](lhci-mobile-prod-20260818-0602-run1.json),
[`lhci-mobile-prod-20260818-0603-run2.json`](lhci-mobile-prod-20260818-0603-run2.json),
[`lhci-mobile-prod-20260818-0604-run3.json`](lhci-mobile-prod-20260818-0604-run3.json),
[`lhci-desktop-prod-20260818-0606-run1.json`](lhci-desktop-prod-20260818-0606-run1.json),
[`lhci-desktop-prod-20260818-0606-run2.json`](lhci-desktop-prod-20260818-0606-run2.json),
[`lhci-desktop-prod-20260818-0606-run3.json`](lhci-desktop-prod-20260818-0606-run3.json).
`runtimeError` en las 6: `undefined` (sin error). `finalDisplayedUrl` en
las 6: `https://biblora-sgb.onrender.com/` (confirmado, sin redirección).

#### Resultados — perfil móvil, producción, post-fix

| Categoría | Run 1 | Run 2 | Run 3 | **Media** | **DT** | Umbral | Cumple |
|---|---|---|---|---|---|---|---|
| Performance | 86 | 88 | 85 | **86,3** | **1,5** | ≥80 | ✅ Sí, las 3 corridas |
| Accessibility | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| Best Practices | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| SEO | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| **CLS** (no es categoría, es la métrica corregida) | 0,005 | 0,011 | 0,011 | **0,009** | **0,003** | <0,1 (bueno) | ✅ **Sí, las 3 corridas** (score de audit = 1,0 en las 3) |

La anomalía puntual de la medición anterior (Run 3 móvil = 71 < 80) **no
se repite** en esta corrida -- las 3 corridas móviles cumplen el umbral
de Performance esta vez. No se afirma que el fix de CLS haya causado
esa mejora (son métricas distintas: esa anomalía estaba en Performance,
no en CLS); se documenta como observación honesta, no como causalidad
comprobada.

#### Resultados — perfil desktop, producción, post-fix

| Categoría | Run 1 | Run 2 | Run 3 | **Media** | **DT** | Umbral | Cumple |
|---|---|---|---|---|---|---|---|
| Performance | 100 | 100 | 100 | **100,0** | **0,0** | ≥80 | ✅ Sí |
| Accessibility | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| Best Practices | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| SEO | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| **CLS** | 0,031 | 0,031 | 0,031 | **0,031** | **0,0003** | <0,1 (bueno) | ✅ **Sí, las 3 corridas** (score de audit = 1,0 en las 3) |

Performance desktop además subió de 88,0 (media, corrida anterior) a
100,0 -- consistente con LCP desktop de 0,4s (vs. el shift tardío que
antes empujaba contenido después de pintado) y TBT 0ms en las 3
corridas.

#### Comparación antes vs. después (producción real, no local)

| Perfil | CLS antes (3 corridas) | CLS después (3 corridas) | Reducción |
|---|---|---|---|
| Desktop | 0,236 / 0,236 / 0,235 (media 0,236) | 0,031 / 0,031 / 0,031 (media 0,031) | **~87%**, de "pobre" (>0,25) a "buena" (<0,1) |
| Mobile | 0,013 / 0,013 / 0,338 (Run 3 anómalo) | 0,005 / 0,011 / 0,011 (media 0,009) | El pico anómalo de Run 3 desaparece; las 3 corridas quedan en rango "bueno" |

Los números de producción post-fix (CLS desktop 0,031 medio) coinciden
de forma casi exacta con la verificación local reportada arriba (0,031
/ 0,032 / 0,033) -- confirmando que la verificación local había sido
representativa del comportamiento real en producción, y no un artefacto
del entorno de prueba local.

**Conclusión**: las **4 categorías cumplen su umbral en las 6 corridas
de producción** (no solo en la media -- en cada corrida individual), y
**CLS queda confirmado en rango "bueno" (<0,1) en producción real**,
no solo en la verificación local. El defecto identificado y corregido
en esta actualización queda cerrado con evidencia de producción, no
solo con evidencia local.

---

## Actualización — 6 corridas oficiales (desktop + móvil) contra producción (2026-08-18)

Esta sección registra las **6 corridas oficiales finales** ejecutadas el 2026-08-18 contra `https://biblora-sgb.onrender.com/` tras el redeploy confirmado del fix de CLS (commit `fced67d` → hash de bundle `main-KA6MN67F.js`). Cumplen explícitamente el requisito de la Guía: "al menos tres corridas por perfil (mobile, desktop) con media y desviación típica por categoría" (P4/B.10).

### Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-08-18T06:02Z – 2026-08-18T06:07Z (las 6 corridas oficiales, secuenciales, ~6 minutos en total)
- **Commit**: `825ad34` (`demo/interfaces-completas`, HEAD al momento de esta medición)
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS (Temurin)
- **Maven**: Apache Maven 3.9.12
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9
- **Frontend probado**: `https://biblora-sgb.onrender.com/` (producción real, no `localhost`)
- **Lighthouse**: v13.4.1 (`npx lighthouse`)
- **Chrome/Chromium usado**: Microsoft Edge 151.0.0.0 en modo headless (`CHROME_PATH` apuntando a `msedge.exe`, no hay Chrome/Chromium nativo instalado en esta máquina)

### Metodología / comandos ejecutados

#### Perfil móvil (3 corridas)

```bash
export CHROME_PATH="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
npx lighthouse https://biblora-sgb.onrender.com/ \
  --output=json \
  --output-path="docs/mediciones/lighthouse/lhci-mobile-prod-<TS>-run<N>.json" \
  --chrome-flags="--headless=new --no-sandbox --disable-gpu --user-data-dir=<tmp-unico-por-corrida>" \
  --form-factor=mobile --screenEmulation.mobile --throttling-method=simulate \
  --only-categories=performance,accessibility,best-practices,seo \
  --max-wait-for-load=60000
```

#### Perfil escritorio (3 corridas)

```bash
export CHROME_PATH="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
npx lighthouse https://biblora-sgb.onrender.com/ \
  --output=json \
  --output-path="docs/mediciones/lighthouse/lhci-desktop-prod-<TS>-run<N>.json" \
  --chrome-flags="--headless=new --no-sandbox --disable-gpu --user-data-dir=<tmp-unico-por-corrida>" \
  --preset=desktop \
  --only-categories=performance,accessibility,best-practices,seo \
  --max-wait-for-load=60000
```

Cada corrida usó un `--user-data-dir` distinto. En las 6 corridas, el JSON ya se escribió a disco antes de cualquier fallo de limpieza de `chrome-launcher` (variación del bug conocido de Windows), confirmado `runtimeError: null` en las 6 y `finalDisplayedUrl: https://biblora-sgb.onrender.com/` en todas.

Los 6 JSON crudos quedan versionados sin editar:
- `lhci-mobile-prod-20260818-0602-run1.json`
- `lhci-mobile-prod-20260818-0603-run2.json`
- `lhci-mobile-prod-20260818-0604-run3.json`
- `lhci-desktop-prod-20260818-0606-run1.json`
- `lhci-desktop-prod-20260818-0606-run2.json`
- `lhci-desktop-prod-20260818-0606-run3.json`

### Resultados — Perfil escritorio (3 corridas, 2026-08-18)

| Categoría | Run 1 | Run 2 | Run 3 | **Media** | **DT** | Umbral | Cumple |
|---|---|---|---|---|---|---|---|
| Performance | 100 | 100 | 100 | **100,0** | **0,0** | ≥80 | ✅ Sí (todas) |
| Accessibility | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| Best Practices | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| SEO | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |

`runtimeError`: `null` en las 3. `finalDisplayedUrl`: `https://biblora-sgb.onrender.com/`.

### Resumen escritorio
Todas las 4 categorías cumplen su umbral **en las 3 corridas individuales** (no solo en la media). DT = 0,0 en todas las categorías — estabilidad perfecta.

### Resultados — Perfil móvil (3 corridas, 2026-08-18)

| Categoría | Run 1 | Run 2 | Run 3 | **Media** | **DT** | Umbral | Cumple |
|---|---|---|---|---|---|---|---|
| Performance | 86 | 88 | 85 | **86,3** | **1,5** | ≥80 | ✅ Sí (todas) |
| Accessibility | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| Best Practices | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |
| SEO | 100 | 100 | 100 | **100,0** | **0,0** | ≥90 | ✅ Sí |

`runtimeError`: `null` en las 3. `finalDisplayedUrl`: `https://biblora-sgb.onrender.com/`.

### Resumen móvil
Todas las 4 categorías cumplen su umbral **en las 3 corridas individuales**. Performance móvil 86,3 ± 1,5 (margen cómodo sobre 80). Accessibility, Best Practices, SEO = 100 % con DT = 0.

### Conclusión: cumplimiento total del requisito de la Guía

| Perfil | Performance | Accessibility | Best Practices | SEO | Cumple ≥3 corridas |
|---|---|---|---|---|---|
| **Escritorio** | ✅ 100,0 (DT 0,0) | ✅ 100,0 | ✅ 100,0 | ✅ 100,0 | ✅ 3/3 |
| **Móvil** | ✅ 86,3 (DT 1,5) | ✅ 100,0 | ✅ 100,0 | ✅ 100,0 | ✅ 3/3 |

**Todas las 4 categorías cumplen su umbral en AMBOS perfiles (móvil y escritorio), en las 6 corridas individuales (3 por perfil).** Se cumple con margen el requisito de la Guía: "al menos tres corridas por perfil (mobile, desktop) con media y desviación típica por categoría" (P4/B.10). Las 6 corridas oficiales contra producción real (`https://biblora-sgb.onrender.com/`) demuestran cumplimiento robusto y estable.
