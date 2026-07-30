# Evidencia — OWASP A05:2021 Mala configuración de seguridad (Bloque C.2)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-30T19:27:40Z
- **Commit**: `447011c`
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Propósito

Bloque C.2: verificar cabeceras de seguridad HTTP en backend y
frontend — `Strict-Transport-Security`, `X-Frame-Options: DENY`,
`X-Content-Type-Options: nosniff`, `Content-Security-Policy`.

## Metodología / comando ejecutado

```bash
curl -I -s http://localhost:8080/                    # backend, raiz
curl -I -s http://localhost:8080/actuator/health      # backend, endpoint permitAll (control)
curl -I -s http://localhost:4200/                     # frontend
```

## Resultados crudos

**Backend, raíz (`/`):**
```
HTTP/1.1 403
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Date: Thu, 30 Jul 2026 19:27:25 GMT
```

**Backend, `/actuator/health` (endpoint `permitAll`, control — mismas cabeceras deben aparecer independientemente del código de estado):**
```
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/vnd.spring-boot.actuator.v3+json
Date: Thu, 30 Jul 2026 19:27:25 GMT
```

**Frontend, raíz (`/`):**
```
HTTP/1.1 200 OK
Server: nginx/1.25.5
Date: Thu, 30 Jul 2026 19:27:25 GMT
Content-Type: text/html
Content-Length: 501
Last-Modified: Tue, 21 Jul 2026 06:26:22 GMT
Connection: keep-alive
ETag: "6a5f110e-1f5"
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Accept-Ranges: bytes
```

## Análisis breve

| Cabecera | Backend | Frontend |
|---|---|---|
| `X-Content-Type-Options: nosniff` | ✅ presente | ✅ presente |
| `X-Frame-Options: DENY` | ✅ presente | ✅ presente |
| `Strict-Transport-Security` | ❌ ausente | ❌ ausente |
| `Content-Security-Policy` | ❌ ausente | ❌ ausente |

**Backend responde `403` en `/`** (no `401`): no es un hallazgo nuevo,
es el comportamiento esperado de Spring Security 6 —
`.anyRequest().authenticated()` sin credenciales cae en
`AuthorizationDeniedException`, ya manejada por
`GlobalExceptionHandler` → 403 (mismo mecanismo verificado en A01). Se
incluyó también `/actuator/health` (ruta `permitAll`) como control,
para confirmar que las cabeceras de seguridad aparecen
independientemente del código de estado — y en efecto aparecen
idénticas en ambos casos.

**`X-Content-Type-Options`/`X-Frame-Options`**: configurados
explícitamente — backend en
`SecurityConfig.filterChain()` (`.headers(headers ->
headers.contentTypeOptions(...).frameOptions(frameOptions ->
frameOptions.deny()))`), frontend en `frontend-angular/nginx.conf`
(`add_header X-Frame-Options "DENY" always; add_header
X-Content-Type-Options "nosniff" always;`).

**`Strict-Transport-Security` ausente**: consistente con A02 — Spring
Security solo emite `Strict-Transport-Security` cuando la request
llega por HTTPS (`request.isSecure() == true`); sobre HTTP plano
(este entorno) el header no se escribe, sin importar la configuración.
No es una omisión de configuración distinta a la ya documentada en
A02, es la misma causa raíz (sin TLS activo) manifestándose acá.

**`Content-Security-Policy` ausente en ambos**: esta sí es una
omisión de configuración real, independiente de TLS —
`SecurityConfig.filterChain()` no llama a
`.headers(headers -> headers.contentSecurityPolicy(...))`, y
`nginx.conf` no tiene ningún `add_header Content-Security-Policy`. No
se agrega en este prompt (instrucción explícita: reportar el hallazgo,
no corregirlo acá).

## Estado: GAP CONOCIDO

- `X-Content-Type-Options`, `X-Frame-Options`: **PASA** en ambos
  servicios.
- `Strict-Transport-Security`: **GAP CONOCIDO**, mismo origen que el
  gap de TLS de A02 — se resuelve junto con ese, no por separado.
- `Content-Security-Policy`: **GAP CONOCIDO independiente** — ningún
  CSP configurado ni en backend ni en frontend. Candidato a hallazgo
  para un prompt de corrección aparte (agregar
  `.headers(headers -> headers.contentSecurityPolicy(...))` en
  `SecurityConfig` y `add_header Content-Security-Policy ...` en
  `nginx.conf`).
