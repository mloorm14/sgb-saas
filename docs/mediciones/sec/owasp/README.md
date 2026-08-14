# Evidencia de seguridad OWASP — índice resumen

Carpeta con la evidencia de medición de seguridad de la entrega
(Bloque C.2 y Módulos 10.1/10.2 de la guía). Cada archivo sigue la
convención `YYYY-MM-DD-owasp-aXX-<tema>.md`; los `-fix-*` documentan la
corrección sobre un gap previo (antes/después, sin editar el original).

## Tabla resumen

| Control OWASP | Estado | Archivos |
|---|---|---|
| A01 Control de acceso roto | **PASA** (gap cerrado: rol ADMIN en `LibroController`) | [`2026-07-30-owasp-a01-control-acceso-roto.md`](2026-07-30-owasp-a01-control-acceso-roto.md), [`2026-07-30-owasp-a01-fix-rol-admin-libros.md`](2026-07-30-owasp-a01-fix-rol-admin-libros.md) |
| A02 Fallos criptográficos | **PASA** (gap de TLS cerrado: ADR-015 CERRADO, preparación de código, TLS real verificado en vivo — HSTS activo) | [`2026-07-30-owasp-a02-fallo-criptografico.md`](2026-07-30-owasp-a02-fallo-criptografico.md), [`2026-08-10-owasp-a02-fix-tls-transporte.md`](2026-08-10-owasp-a02-fix-tls-transporte.md) |
| A03 Inyección | **PASA** | [`2026-07-30-owasp-a03-inyeccion.md`](2026-07-30-owasp-a03-inyeccion.md) |
| A05 Mala configuración de seguridad | **PASA** (gap cerrado: CSP backend, stacktraces, Swagger, non-root; verificación real en Docker; + cabeceras del frontend en esta rama) | [`2026-07-30-owasp-a05-mala-configuracion-seguridad.md`](2026-07-30-owasp-a05-mala-configuracion-seguridad.md), [`2026-08-10-owasp-a05-fix-csp-stacktrace-swagger-nonroot.md`](2026-08-10-owasp-a05-fix-csp-stacktrace-swagger-nonroot.md), [`2026-08-11-owasp-a05-verificacion-real.md`](2026-08-11-owasp-a05-verificacion-real.md) |
| A07 Fallos de identificación y autenticación | **PASA** (gap cerrado: rate limiting de login) — *ver nota de incidente 2026-08-14* | [`2026-07-30-owasp-a07-fallo-identificacion-autenticacion.md`](2026-07-30-owasp-a07-fallo-identificacion-autenticacion.md), [`2026-07-30-owasp-a07-fix-rate-limiting-login.md`](2026-07-30-owasp-a07-fix-rate-limiting-login.md) |
| A09 Fallos de registro y monitoreo | **PASA** (gap cerrado: logging de autenticación) | [`2026-07-30-owasp-a09-fallo-registro-monitoreo.md`](2026-07-30-owasp-a09-fallo-registro-monitoreo.md), [`2026-07-30-owasp-a09-fix-logging-autenticacion.md`](2026-07-30-owasp-a09-fix-logging-autenticacion.md) |
| Re-verificación automatizada | **PASA** (A01/A03/A07/A09 re-ejecutados 2026-08-11) | [`2026-08-11-owasp-audit-automatizado.md`](2026-08-11-owasp-audit-automatizado.md) |

> **Nota sobre A07 (incidente operativo 2026-08-14)**: durante la
> verificación en vivo de esta rama se detectó que los endpoints de
> autenticación de producción devolvían 500 por una falla de la
> dependencia Redis (Upstash). Se aplicó degradación controlada en el
> código (fail-open/fail-closed documentado, 503 honesto) y la
> restauración de la dependencia es un pendiente del dashboard de
> Upstash. Ver
> [`../2026-08-14-incidente-500-auth-redis-produccion.md`](../2026-08-14-incidente-500-auth-redis-produccion.md).

## Cabeceras de seguridad (esta rama)

| Cabecera | Backend (prod, verificado 2026-08-14) | Frontend (prod, verificado 2026-08-14) |
|---|---|---|
| `Content-Security-Policy` | ✓ (restrictiva, `frame-ancestors 'none'`, `form-action 'none'`) | ✓ `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' https://sgb-backend-b058.onrender.com; frame-ancestors 'none'; base-uri 'self'; object-src 'none'` |
| `X-Frame-Options` | ✓ `DENY` | ✓ `DENY` |
| `X-Content-Type-Options` | ✓ `nosniff` | ✓ `nosniff` |
| `Strict-Transport-Security` | ✓ `max-age=31536000; includeSubDomains` | ✓ automática del edge de Render (`max-age=315360000; includeSubdomains; preload`) |

El frontend quedó con CSP con `style-src 'unsafe-inline'` (requerido por
Angular, riesgo aceptado y documentado abajo) y `connect-src` limitado al
host del backend.

> **Nota de configuración (2026-08-14):** los Static Sites de Render **no
> soportan el archivo `_headers`** (lo sirven como archivo plano, no lo
> interpretan). Las cabeceras del frontend se configuran en el **Dashboard
> de Render** (Servicio → Headers, patrón `/*`), no en el repositorio. La
> CSP cargada en el Dashboard no incluye `form-action 'none'` ni
> `script-src-attr 'none'` que sí estaban en el draft del `_headers` —
> pendiente opcional: agregarlas a la regla del Dashboard para igualar la
> política más estricta. Detalle y valores completos en
> [`docs/despliegue/DEPLOYMENT.md`](../../despliegue/DEPLOYMENT.md) §5.4.1.

## ZAP Baseline (2026-08-14, ZAP 2.17.0)

| Objetivo | High | Medium | Low | Informational | FAIL-NEW | Resultado |
|---|---|---|---|---|---|---|
| Backend prod (`https://sgb-backend-b058.onrender.com`) | 0 | 0 | 0 | 1 (`Non-Storable Content`, 403s) | 0 | **PASA** |
| Frontend prod (`https://biblora-sgb.onrender.com`, Angular 21.2.20, cabeceras desde Dashboard de Render) | 0 | 2 (ambos `CSP` plugin 10055 — `style-src unsafe-inline` y `Failure to Define Directive with No Fallback`, aceptados) | 4 (COEP/COOP/CORP, Permissions-Policy — opcionales) | 4 | 0 | **PASA** |
| Frontend local (histórico — build de la rama servido en localhost, pre-deploy) | 0 | 1 (`CSP: style-src unsafe-inline` — aceptado) | 4 | 3 | 0 | **PASA** |

Reportes: [`zap/`](../zap/). El reporte vigente del frontend es
`reporte-zap-baseline-frontend-prod-2026-08-14.{xml,html}`, corrido contra
la URL real de producción; el de localhost se conserva como evidencia
histórica del proceso (`reporte-zap-baseline-frontend-local-2026-08-14`).
El hallazgo High del frontend original (`Vulnerable JS Library` —
`@angular/core 17.3.12`, CVEs 2026) se resolvió con el upgrade a Angular
21.2.20 (ver DEPLOYMENT.md §5.4.2); el reporte con ese hallazgo no se
archivó.

### Hallazgo Medium aceptado (ZAP frontend)

- **Hallazgo:** CSP: `style-src` incluye `'unsafe-inline'` y no define
  directivas de fallback por tipo de recurso (ZAP plugin id 10055, CWE-693;
  dos ítems en el reporte prod: `style-src unsafe-inline` y `Failure to
  Define Directive with No Fallback`).
- **Causa:** Angular inyecta estilos de componentes como `<style>` inline en tiempo
  de ejecución; sin `unsafe-inline` la aplicación no renderiza correctamente. El
  segundo ítem aparece porque la CSP cargada en el Dashboard de Render no define
  `script-src-elem`/`style-src-elem`/etc., y con `style-src` presente, `unsafe-inline`
  aplica como fallback por defecto — mitigación: definir explícitamente
  `script-src-elem 'self'`, `script-src-attr 'none'`, `style-src-elem 'self' 'unsafe-inline'`
  y `style-src-attr 'unsafe-inline'` en la regla del Dashboard (pendiente opcional).
- **Decisión:** aceptado como riesgo residual. El resto de la CSP es estricta
  (`default-src 'self'`, `frame-ancestors 'none'`, `object-src 'none'`, `script-src 'self'`),
  lo que limita el impacto de este punto a un vector secundario (requiere una
  vulnerabilidad de inyección previa para ser explotable). Migrar a CSP basada en
  nonces requeriría generación de nonce por request en el servidor, no viable con
  el Static Site actual de Render sin un cambio de arquitectura del frontend —
  queda registrado como línea de trabajo futuro.
