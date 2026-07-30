## HU-AUTH-03: Cerrar sesión

**Como** usuario con sesión iniciada,
**quiero** cerrar sesión de forma explícita,
**para** que mi `accessToken` deje de servir de inmediato, aunque
técnicamente no haya expirado todavía, y así reducir el riesgo si el
dispositivo queda desatendido o el token fue comprometido.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Cierre de sesión

  Escenario: Logout exitoso invalida el accessToken de inmediato
    Dado que el usuario tiene sesión iniciada con un accessToken vigente
    Cuando cierra sesión
    Entonces el sistema responde 204
    Y ese accessToken queda en una lista negra (Redis) hasta su expiración natural
    Y cualquier request posterior con ese mismo token es rechazado como no autorizado
    Y la cookie refreshToken se limpia (deja de enviarse en requests futuros)

  Escenario: El evento de logout queda registrado
    Dado que el usuario cierra sesión
    Entonces el evento LOGOUT queda registrado con su correo, IP y fecha/hora
    (ver HU-AUTH-06 para el detalle de qué y dónde se registra)
```
