# ADR-015: Estrategia de TLS en tránsito

## Estado

Aceptado

## Contexto

REQ-NF-012 (Bloque C.2, checklist OWASP A05/A02) exige TLS 1.2+ en
tránsito. Como ya quedó documentado explícitamente en
`docs/mediciones/sec/2026-07-30-owasp-a02-fallo-criptografico.md`, el
stack de desarrollo/evaluación actual (`docker-compose.yml`) corre sobre
HTTP plano: `backend` expone `8080` y `frontend` expone `4200` sin ningún
proxy TLS ni certificado. Ese archivo también deja escrito que un entorno
**públicamente accesible con TLS real** es un requisito de la Entrega
Final, no de esta Tercera Entrega — por lo tanto, activar TLS "de verdad"
(certificados, dominio) está fuera del alcance de esta rama.

Lo que sí corresponde a esta rama (Módulo 10.1 del roadmap) es dejar
**decidida y documentada** la estrategia, y dejar el backend **preparado**
para que, cuando el certificado llegue, no haga falta tocar código: el
mismo criterio que ya se usó con el atributo `Secure` de la cookie de
refresh token (declarado en código desde antes de tener TLS activo, ver
ADR-007 / el propio hallazgo de A02).

Existen dos formas habituales de resolver dónde termina TLS en un stack
como este:

1. **TLS termina en el propio backend Spring Boot** (`server.ssl.*`,
   keystore cargado por la aplicación).
2. **TLS termina en un proxy/balanceador delante del stack**
   (`frontend-angular/nginx.conf` en desarrollo, o un load balancer /
   proxy dedicado en producción), y el tráfico interno
   proxy→backend/frontend sigue en HTTP plano dentro de la red interna de
   Docker/el clúster.

## Decisión

Se elige la opción 2: **TLS termina en el proxy**, no en el backend
Spring Boot.

Razones:
- Es el patrón ya usado por el propio `docker-compose.yml`: `frontend`
  ya es un contenedor Nginx que sirve sobre HTTP hoy y es el candidato
  natural para terminar TLS mañana (agregar `ssl_protocols TLSv1.2
  TLSv1.3;` + certificado en `nginx.conf`), sin duplicar esa
  responsabilidad dentro del `backend`.
- Gestionar certificados (renovación, Let's Encrypt) es responsabilidad
  de infraestructura/despliegue, no del código de dominio del backend —
  meterlo en `server.ssl.*` acopla la aplicación a un detalle de
  despliegue que cambia según el entorno (autofirmado en desarrollo,
  Let's Encrypt o el del proveedor cloud en producción).
- Coincide con `adr-012-estrategia-despliegue.md`, que ya trata a Nginx
  como el punto de entrada HTTP del stack.

Por lo tanto: **no se activa `server.ssl.enabled` en el backend**. En su
lugar, el backend se prepara para vivir detrás de un proxy que sí termina
TLS:

- `server.forward-headers-strategy: framework` en `application.yml`: le
  dice a Spring (vía `ForwardedHeaderFilter`) que confíe en las cabeceras
  `X-Forwarded-Proto`/`X-Forwarded-For`/`X-Forwarded-Host` que un proxy
  TLS-terminating antepone. Sin esto, `request.isSecure()` siempre da
  `false` aunque el usuario final sí esté en HTTPS, y por diseño de
  Spring Security el header `Strict-Transport-Security` (que la app ya
  emite por defecto, ver `HstsHeaderWriter`) **nunca se escribe** — el
  mismo mecanismo ya identificado como causa raíz del gap de HSTS en
  `2026-07-30-owasp-a05-mala-configuracion-seguridad.md`. Con el filtro
  activo, el día que el proxy real mande `X-Forwarded-Proto: https`, HSTS
  se activa sin tocar código de nuevo.

## Consecuencias

- El backend **no** necesita keystore ni `server.ssl.*` en ningún
  perfil; esa responsabilidad queda fuera de este repo salvo por el
  `nginx.conf` del frontend (fuera del alcance backend de esta rama).
- Cuando se agregue el certificado real (Entrega Final), el único cambio
  esperado es en la capa de proxy/despliegue — el backend ya queda listo
  para reconocerlo vía `X-Forwarded-Proto` y activar HSTS sin releases
  adicionales.
- Sigue existiendo el mismo gap "honesto" documentado en A02: sin TLS
  real activo hoy, no hay forma verificable de mostrar HSTS funcionando
  end-to-end en este entorno — se deja como gap conocido, no simulado
  (ver `docs/mediciones/sec/2026-08-10-owasp-a02-fix-tls-transporte.md`).
- Si en el futuro se decide desplegar el backend directamente expuesto
  (sin proxy delante), esta decisión debe revisitarse explícitamente: sin
  proxy, `forward-headers-strategy` en `framework` sin validar el origen
  de esas cabeceras sería, en sí mismo, un riesgo de spoofing de
  `X-Forwarded-*` — aceptable acá porque el único que puede llegar al
  backend es el proxy interno de la red de Docker/clúster, nunca el
  cliente final directo.
