# ADR-007: Cookies HttpOnly para JWT — refreshToken migrado, accessToken pendiente

## Title

Migración del refreshToken a cookie HttpOnly+Secure+SameSite=Strict;
el accessToken permanece en el body/Authorization header hasta coordinar
con el frontend.

## Context

La guía de la Tercera Entrega (A.1) exige que la autenticación opere bajo
cookie HttpOnly+Secure+SameSite=Strict con JWT. Antes de este cambio, el
estado real del código (verificado con lectura directa de
`AuthController`/`AuthService`/`TokenResponseDTO`, no asumido) era:

- **accessToken**: se devuelve en el cuerpo JSON de `/api/auth/login` y
  `/api/auth/refresh`. El frontend Angular (`auth.service.ts`) lo guarda
  **en memoria** (nunca en `localStorage`, ya documentado como buena
  práctica en el propio código) y `jwt.interceptor.ts` lo adjunta
  manualmente como header `Authorization: Bearer <token>` en cada request
  saliente.
- **refreshToken**: también se devolvía en el cuerpo JSON, en texto plano,
  sin ningún mecanismo de cookie. **Nota de corrección**: esto contradice
  lo que indicaban las notas de sesiones anteriores del proyecto
  ("el refreshToken en cookie HttpOnly ya está implementado en el backend
  pero el frontend no lo gestiona") — al revisar el código real no existía
  infraestructura de cookies en absoluto, ni en `AuthController` ni en
  `AuthService`. Más aún, el frontend actual (`auth.service.ts`) ni
  siquiera almacena el `refreshToken` que recibe: no hay lógica de refresh
  automático implementada todavía (en un 401 fuera de `/auth/`, el
  interceptor simplemente cierra sesión). Este ADR documenta el estado real
  encontrado y la decisión tomada a partir de él, no de la suposición
  previa.

Dado que el `refreshToken` es un secreto de vida larga (7 días,
`security.jwt.refresh-expiration-ms`) y no era leído por ningún código
JavaScript existente, migrarlo a cookie es una corrección de bajo riesgo:
nada del frontend depende de tenerlo en el cuerpo de la respuesta.

El `accessToken`, en cambio, sí es leído activamente por
`jwt.interceptor.ts` en **cada** request HTTP saliente hacia el backend
para construir el header `Authorization`. Migrarlo a cookie HttpOnly
implicaría, como mínimo:

1. Que el navegador adjunte la cookie automáticamente en cada request
   (correcto para peticiones al mismo origen/CORS con credentials), lo que
   vuelve innecesario — y en conflicto — el header `Authorization` manual
   que hoy construye `jwt.interceptor.ts`.
2. Que `JwtAuthFilter` (backend) lea el token desde la cookie en vez del
   header `Authorization`, o soporte ambos con una prioridad definida.
3. Que `AuthService.isLoggedIn()` dejará de poder inspeccionar el valor del
   token en memoria (una cookie HttpOnly no es legible desde JS por
   diseño), por lo que esa lógica tendría que reconstruirse de otra forma
   (ej. un endpoint `/api/auth/me` o un flag de sesión separado).

Ese es un cambio de contrato de API que toca **todas** las rutas
protegidas, no solo las de autenticación, y afecta directamente el trabajo
de Panama (frontend). Hacerlo unilateralmente mientras el equipo no está
disponible para coordinar corre el riesgo de romper silenciosamente el
flujo de sesión ya construido.

## Decision

Se migra **solo el refreshToken** a una cookie `HttpOnly`, `Secure`,
`SameSite=Strict`, con `path=/api/auth` (para que el navegador no la
adjunte en llamadas de negocio como `/api/v1/libros`, solo en
login/refresh/logout). El campo `refreshToken` se retira del cuerpo JSON
de `TokenResponseDTO` (anotado `@JsonIgnore`, se conserva en el record
Java solo para que `AuthController` pueda leer el valor real y construir
el `Set-Cookie`). `/api/auth/refresh` deja de aceptar el token por body
(`RefreshRequestDTO` eliminado, ya no tenía otro uso) y lo lee de la cookie
vía `@CookieValue`. `/api/auth/logout` limpia la cookie (`maxAge=0`) además
de invalidar el accessToken en la blacklist de Redis.

El **accessToken NO se migra todavía**. Sigue viajando en el cuerpo JSON y
gestionado en memoria por el frontend exactamente como hoy. Queda como
tarea pendiente, explícitamente NO iniciada sin coordinación previa con
Panama, dado el punto 1-3 de la sección Context.

## Alternativas consideradas

- **Migrar ambos tokens ahora:** descartado. El accessToken tiene blast
  radius sobre todas las rutas protegidas y sobre la lógica de sesión del
  frontend (`isLoggedIn()`, interceptor, manejo de 401); hacerlo sin poder
  validar con Panama en vivo es forzar una integración a ciegas.
- **No tocar nada y solo documentar el gap:** descartado para el
  refreshToken — es el hallazgo más grave del A.1 (secreto de larga
  duración totalmente desprotegido) y la corrección no rompe nada existente
  (confirmado por grep: el frontend no usa `response.refreshToken` en
  ningún punto). No hacerlo habría sido la opción cómoda, no la correcta.
- **Dual-write del accessToken (cookie HttpOnly + seguir devolviéndolo en
  el body para que el interceptor lo use igual que hoy):** descartado
  porque no reduce superficie de ataque real (si sigue en el body, sigue
  siendo legible por JS/XSS igual que ahora) y solo añade complejidad sin
  beneficio de seguridad.

## Status

Aceptado y **parcialmente implementado**: refreshToken en cookie
HttpOnly+Secure+SameSite=Strict, verificado en vivo contra el stack Docker
(ver `docs/mediciones/sec/`). Migración del accessToken: **pendiente**,
bloqueada intencionalmente hasta coordinar con Panama los cambios
correspondientes en `jwt.interceptor.ts`, `auth.service.ts` y, del lado
backend, `JwtAuthFilter`.


> **Actualización 2026-08-24 (commit 2884126, autor MoisesPanama):** el
> atributo `SameSite` de la cookie del refreshToken se cambió de `Strict` a
> `None`. Motivo: el frontend (`biblora-sgb.onrender.com`) y el backend
> (`sgb-backend-b058.onrender.com`) se despliegan en subdominios distintos
> de Render (orígenes diferentes). Con `SameSite=Strict`, el navegador
> bloquea silenciosamente el envío de la cookie en peticiones cross-origin,
> rompiendo el flujo de *silent refresh* del `accessToken`. Se mantiene
> `Secure=true` y `HttpOnly=true` (la cookie sigue siendo inaccesible a
> JavaScript y solo viaja por HTTPS). El `path=/api/auth` ya documentado
> en la Decisión limita el alcance de la cookie a los endpoints de
> autenticación (`/api/auth/*`), mitigando parcialmente la superficie de
> exposición CSRF que reintroduce `SameSite=None`. La decisión original
> (documentada más arriba) se conserva como registro histórico; este cambio
> es una evolución necesaria para que el *silent refresh* funcione en
> producción con los orígenes reales de Render.

## Consequences

**Positivas:**

- El refreshToken (secreto de 7 días) ya no es legible por JavaScript ni
  queda expuesto en el cuerpo de la respuesta HTTP — una fuga por XSS ya no
  puede exfiltrarlo directamente.
- El cambio es aditivo/no disruptivo para el frontend actual: nada dejó de
  funcionar porque nada dependía del valor anterior en el body.
- Cierra el hallazgo más severo para la auditoría OWASP A02 (Bloque C.2) sin
  esperar a coordinar el cambio más grande del accessToken.

**Negativas:**

- El accessToken sigue siendo, hasta que se migre, el eslabón débil frente
  a XSS: aunque se guarda solo en memoria (no en `localStorage`), sigue
  siendo legible por cualquier script que corra en la página durante la
  sesión activa.
- Queda una migración de mayor alcance pendiente y sin fecha, que requiere
  trabajo coordinado en ambos lados (backend `JwtAuthFilter` + frontend
  `jwt.interceptor.ts`/`auth.service.ts`).
- `/api/auth/refresh` ya no acepta el refresh token por body: cualquier
  cliente que lo invocara manualmente (ej. Postman/curl de pruebas
  anteriores) debe actualizarse para enviar la cookie en vez del JSON.

## Referencias

- OWASP Top 10:2021, A02 (Cryptographic Failures) y A07 (Identification and
  Authentication Failures)
- [[ADR-003-jwt-redis]] (blacklist de accessToken vía Redis, mecanismo
  complementario e independiente de este cambio)
- `docs/mediciones/sec/` (evidencia cruda de `curl --include` con el
  `Set-Cookie` verificado en vivo)
