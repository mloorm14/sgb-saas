## CU-AUTH-05: Bloquear temporalmente los intentos de login por correo+IP

- **Actor principal**: El sistema mismo, actuando como defensa
  automática (no hay una acción manual de un usuario humano en este
  caso de uso).
- **Interesados y sus intereses**:
  - Usuario legítimo: no quiere quedar bloqueado porque un atacante
    intentó entrar con su correo desde otro origen.
  - Responsable de seguridad: quiere que un ataque de fuerza bruta
    contra una contraseña se vuelva impráctico sin necesidad de
    CAPTCHA ni intervención manual.
- **Precondiciones**: ninguna — el contador se crea en el primer
  intento fallido de cada combinación correo+IP.
- **Garantía de éxito (postcondición)**: tras `max-attempts` intentos
  fallidos (configurable, `app.security.login.max-attempts`, default
  **5**) dentro de una ventana de `rate-limit-window-seconds`
  (configurable, default **900s / 15 min**), cualquier intento
  adicional de esa combinación correo+IP se rechaza con `429` sin
  llegar a validar la contraseña.
- **Disparador**: cada llamada a `POST /api/auth/login`.

### Escenario principal (flujo básico)

1. Antes de intentar autenticar, el sistema consulta en Redis el
   contador de la clave `login-attempts:{correo}:{ip}`.
2. Si el contador es menor al máximo configurado, el sistema procede
   con la autenticación normal (ver CU-AUTH-02).
3. Si la autenticación falla (`BadCredentialsException`), el sistema
   incrementa el contador (`INCR` atómico de Redis).
4. Si es el primer fallo de esa combinación (contador pasa de 0 a 1),
   el sistema fija el TTL de la clave a la ventana configurada — los
   fallos siguientes **no** vuelven a extender ese TTL, así la ventana
   es fija desde el primer fallo, no rodante por cada intento.
5. Si la autenticación tiene éxito, el sistema borra el contador de esa
   combinación (reseteo).

### Extensiones (flujos alternativos)

- **2a.** El contador ya alcanzó el máximo: el sistema responde `429`
  (`LoginRateLimitExcedidoException` → `ProblemDetail`) con los
  segundos restantes hasta que expire la ventana, **sin** llamar a
  `AuthenticationManager.authenticate()` — ni siquiera se evalúa si la
  contraseña habría sido correcta.

### Nota de diseño — por qué correo+IP y no solo correo

Si la clave fuera solo el correo, cualquiera podría bloquear la cuenta
de otra persona fallando el login a propósito con esa correo, sin
siquiera conocer la contraseña real (denegación de servicio dirigida).
Usando correo+IP, un atacante que falla contra el correo de la víctima
solo agota **su propio** cupo en **su propia** IP — la víctima
conserva su cupo intacto mientras inicie sesión desde su propio origen.

**Limitación aceptada y documentada**: un atacante con múltiples IPs
(o una red de bots) puede seguir intentando fuerza bruta rotando de
IP cada 5 intentos. Mitigar eso (reputación de IP, CAPTCHA
progresivo) queda fuera del alcance de este requisito.

**Evidencia empírica**: verificado en vivo contra el stack real —
`docs/mediciones/sec/owasp/2026-07-30-owasp-a07-fix-rate-limiting-login.md`.
