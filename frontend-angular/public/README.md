# Cabeceras de seguridad del frontend — nota sobre `_headers`

> **Importante (2026-08-14):** este archivo `_headers` es la **referencia**
> de cuáles cabeceras de seguridad debe servir el frontend, pero **no tiene
> efecto real** en la configuración actual de despliegue.

Los **Static Sites de Render no soportan el archivo `_headers`** en formato
Netlify: Render lo sirve como contenido estático plano (se puede descargar
en `/_headers`) y no lo interpreta como configuración. La configuración
**real** de las cabeceras del frontend en producción vive en el **Dashboard
de Render** (Servicio `biblora-sgb` → pestaña **Headers**), con las reglas
aplicadas bajo el patrón `/*`.

La política realmente cargada en el Dashboard (verificada con
`curl -I https://biblora-sgb.onrender.com/` el 2026-08-14) es:

| Cabecera | Valor en producción (Dashboard) |
|---|---|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; script-src-attr 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' https://sgb-backend-b058.onrender.com; frame-ancestors 'none'; base-uri 'self'; form-action 'none'; object-src 'none'` |
| `X-Frame-Options` | `DENY` |
| `X-Content-Type-Options` | `nosniff` |
| `Strict-Transport-Security` | aplicada automáticamente por el edge de Render (`max-age=315360000; includeSubdomains; preload`) — no se configura manualmente |

Notas:

- El 2026-08-14 la CSP del Dashboard fue **endurecida** agregando
  `script-src-attr 'none'` y `form-action 'none'` (5 formularios de la app
  verificados: todos usan `(ngSubmit)` + `HttpClient`, ninguno depende de
  submit nativo — `form-action 'none'` no rompe ningún flujo). Esto cerró
  el ítem `Failure to Define Directive with No Fallback` del plugin 10055
  de ZAP (reporte `-v2`: 0 High, 1 Medium restante `style-src
  'unsafe-inline'`, aceptado). Detalle completo en
  [`docs/despliegue/DEPLOYMENT.md`](../../docs/despliegue/DEPLOYMENT.md)
  §5.4.1.
- Si el servicio se recrea o se migra a otro proveedor, esta configuración
  se pierde (no vive en el repositorio): reproducirla desde la tabla de
  arriba (misma tabla copiada en `DEPLOYMENT.md`).
- Si algún día el frontend se sirve desde Netlify o Cloudflare Pages, este
  archivo `_headers` sí sería interpretado por esos proveedores.