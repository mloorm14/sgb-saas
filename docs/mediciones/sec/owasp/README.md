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

| Cabecera | Backend (prod, verificado 2026-08-14) | Frontend (verificado local; deploy pendiente en Render) |
|---|---|---|
| `Content-Security-Policy` | ✓ (restrictiva, `frame-ancestors 'none'`, `form-action 'none'`) | ✓ (ver [`frontend-angular/public/_headers`](../../../frontend-angular/public/_headers)) |
| `X-Frame-Options` | ✓ `DENY` | ✓ `DENY` |
| `X-Content-Type-Options` | ✓ `nosniff` | ✓ `nosniff` |
| `Strict-Transport-Security` | ✓ `max-age=31536000; includeSubDomains` | ✓ (edge de Render/Cloudflare) |

El frontend quedó con CSP con `style-src 'unsafe-inline'` (requerido por
Angular, riesgo aceptado y documentado) y `connect-src` limitado al host
del backend. Detalle en [`docs/despliegue/DEPLOYMENT.md`](../../despliegue/DEPLOYMENT.md) §5.4.

## ZAP Baseline (2026-08-14, ZAP 2.17.0)

| Objetivo | High | Medium | Low | Informational | FAIL-NEW | Resultado |
|---|---|---|---|---|---|---|
| Backend prod (`https://sgb-backend-b058.onrender.com`) | 0 | 0 | 0 | 1 (`Non-Storable Content`, 403s) | 0 | **PASA** |
| Frontend local (build corregido, Angular 21.2.20, cabeceras finales) | 0 | 1 (`CSP: style-src unsafe-inline` — aceptado) | 4 (COEP/COOP/CORP, Permissions-Policy — opcionales) | 3 | 0 | **PASA** |

Reportes: [`zap/`](../zap/). El hallazgo High del frontend original
(`Vulnerable JS Library` — `@angular/core 17.3.12`, CVEs 2026) se
resolvió con el upgrade a Angular 21.2.20 (ver DEPLOYMENT.md §5.4.2); el
reporte con ese hallazgo no se archivó. El escaneo del frontend desplegado
se re-ejecutará tras el deploy (pendiente de confirmación post-merge).
