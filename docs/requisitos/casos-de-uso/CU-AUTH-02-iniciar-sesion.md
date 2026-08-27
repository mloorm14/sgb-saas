## CU-AUTH-02: Iniciar sesión

- **Actor principal**: Usuario registrado (cualquier rol).
- **Interesados y sus intereses**:
  - Usuario: quiere entrar rápido si sus credenciales son correctas, y
    un mensaje claro si algo se lo impide (contraseña, multas, cuenta
    inactiva).
  - Responsable de seguridad: quiere que un ataque de fuerza bruta no
    pueda probar contraseñas sin límite (ver HU-AUTH-05) y que cada
    intento quede auditado (ver HU-AUTH-06).
- **Precondiciones**: el usuario existe en `usuarios`; no tiene
  agotado el cupo de intentos fallidos para su combinación correo+IP.
- **Garantía de éxito (postcondición)**: se emite un `accessToken`
  (JWT, en el cuerpo de la respuesta, vigencia corta) y un
  `refreshToken` (cookie `HttpOnly+Secure+SameSite=Strict`,
  `path=/api/auth`, ver `adr-012-cookies-jwt.md`); se registra el
  evento `LOGIN_OK`.
- **Disparador**: el usuario envía correo y contraseña
  (`POST /api/auth/login`).

### Escenario principal (flujo básico)

1. El usuario ingresa correo y contraseña.
2. El sistema verifica que la combinación correo+IP no tenga el cupo de
   intentos fallidos agotado (`LoginRateLimiter.estaBloqueado`).
3. El sistema autentica las credenciales contra `AuthenticationManager`
   (compara el hash BCrypt).
4. El sistema resetea el contador de intentos fallidos de esa
   combinación correo+IP.
5. El sistema genera `accessToken` + `refreshToken`, registra
   `LOGIN_OK` (logs + `bitacora_auditoria`) y responde con el
   `accessToken` en el cuerpo y el `refreshToken` en una cookie.

### Extensiones (flujos alternativos)

- **2a.** El cupo de intentos fallidos ya está agotado: el sistema
  rechaza con error 429 **sin llegar a intentar la autenticación**
  (ver HU-AUTH-05/CU-AUTH-05 para el detalle del mecanismo).
- **3a.** Contraseña incorrecta: el sistema rechaza con error 401,
  incrementa el contador de fallos de esa combinación correo+IP, y
  registra `LOGIN_FAIL`.
- **3b.** El usuario tiene estado `BLOQUEADO_POR_MULTA`: el sistema
  rechaza con error 423 (`LockedException`), sin importar si la
  contraseña era correcta.
- **3c.** El usuario tiene estado `INACTIVO` o
  `PENDIENTE_VERIFICACION`: el sistema rechaza con error 403
  (`DisabledException`).
