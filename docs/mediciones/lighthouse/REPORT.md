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
