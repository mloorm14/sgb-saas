## CU-AUTH-04: Refrescar el accessToken

- **Actor principal**: Frontend actuando en nombre del usuario (el
  interceptor `jwt.interceptor.ts`, no una acción manual del usuario).
- **Interesados y sus intereses**:
  - Usuario: quiere que su sesión se mantenga activa sin fricciones
    mientras siga usando el sistema.
  - Responsable de seguridad: quiere que el `refreshToken` nunca sea
    accesible desde JavaScript (por eso viaja en cookie `HttpOnly`, ver
    `adr-007-cookies-jwt.md`) y que el `accessToken` siga siendo de
    vida corta.
- **Precondiciones**: existe una cookie `refreshToken` en el navegador
  (`HttpOnly+Secure+SameSite=Strict`, `path=/api/auth`).
- **Garantía de éxito (postcondición)**: se emite un `accessToken`
  nuevo sin pedir credenciales; la cookie `refreshToken` se reemite con
  el mismo valor y TTL renovado.
- **Disparador**: el interceptor del frontend recibe un `401` en una
  request fuera de `/auth/` y, antes de desloguear, intenta
  `POST /api/auth/refresh` (`withCredentials: true`, sin body).

### Escenario principal (flujo básico)

1. El interceptor detecta un `401` en una request que no es de
   `/auth/`.
2. Llama a `POST /api/auth/refresh`; el navegador adjunta la cookie
   `refreshToken` automáticamente (no hay body).
3. El sistema valida el `refreshToken` de la cookie.
4. El sistema resuelve el usuario a partir del correo codificado en el
   `refreshToken`.
5. El sistema emite un `accessToken` nuevo y responde `200` con él en
   el cuerpo y la cookie `refreshToken` reemitida.
6. El interceptor guarda el `accessToken` nuevo en memoria
   (`AuthService` de Angular) y reintenta la request original una
   sola vez.

### Extensiones (flujos alternativos)

- **1a.** No hay cookie `refreshToken` en la request: el sistema
  rechaza con error 400 (`IllegalArgumentException`, "Falta la cookie
  refreshToken").
- **3a.** El `refreshToken` es inválido o ya expiró: el sistema lo
  rechaza — **limitación conocida**: hoy esto lanza una
  `RuntimeException` genérica que no tiene un `@ExceptionHandler`
  dedicado en `GlobalExceptionHandler`, así que cae en el handler
  genérico y responde `500` en vez de un `401`/`403` semánticamente
  más correcto. El interceptor del frontend igual interpreta cualquier
  fallo del refresh como "no se pudo renovar" y desloguea al usuario,
  así que el comportamiento observable para el usuario es correcto,
  pero el código de estado HTTP no es el ideal — candidato a mejora
  futura (agregar un `@ExceptionHandler` específico para refresh
  inválido), no corregido como parte de este relevamiento de
  requisitos.
