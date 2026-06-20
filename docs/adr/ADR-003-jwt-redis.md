# ADR-003: Uso de Redis para blacklist de tokens JWT

## Estado
Aceptado

## Contexto
El sistema usa autenticación stateless basada en JWT (RFC 7519). Por diseño, 
el servidor no almacena información de sesión: cada token es autocontenido 
y válido hasta su expiración natural (1 hora para el accessToken). Esto 
genera un problema de seguridad: si un usuario hace logout, o si un token 
es comprometido (robado), el servidor no tiene forma nativa de invalidarlo 
antes de que expire por sí solo.

## Decisión
Se incorpora Redis 7 como almacén en memoria de una lista negra (blacklist) 
de identificadores de token revocados. Cada JWT incluye un claim `jti` 
(JWT ID, un UUID único). Al hacer logout, el `JwtService` extrae el `jti` 
y la fecha de expiración del token, y `AuthService.logout()` calcula el 
tiempo restante hasta la expiración natural. Ese valor se usa como TTL 
(Time To Live) al guardar la clave `blacklist:<jti>` en Redis.

En cada solicitud protegida, `JwtAuthFilter` valida primero la firma y 
expiración del token con `JwtService.validateToken()`, y luego consulta 
`redisTemplate.hasKey("blacklist:" + jti)`. Si la clave existe, la 
solicitud es rechazada aunque el token sea criptográficamente válido.

## Alternativas consideradas
- **No invalidar tokens (aceptar el riesgo):** descartado por motivos de 
  seguridad — un token robado seguiría siendo válido hasta su expiración, 
  sin posibilidad de revocación inmediata.
- **Lista negra en PostgreSQL:** descartado por rendimiento. Consultar la 
  blacklist en cada solicitud protegida añadiría una consulta SQL adicional 
  por request; Redis, al ser un almacén en memoria con operaciones O(1), 
  introduce una latencia mínima (sub-milisegundo) frente a una tabla 
  relacional con índices.
- **Tokens de muy corta duración sin blacklist:** descartado porque reduce 
  la ventana de riesgo pero no la elimina, y degrada la experiencia de 
  usuario al forzar refrescos de token más frecuentes.

## Consecuencias
- **Positivas:** revocación inmediata de tokens comprometidos o cerrados 
  por el usuario; el TTL automático de Redis limpia las claves expiradas 
  sin necesidad de un proceso de limpieza adicional; bajo overhead de 
  latencia por la naturaleza en memoria de Redis.
- **Negativas:** se introduce una dependencia de infraestructura adicional 
  (Redis) que debe estar disponible para que el sistema de autenticación 
  funcione correctamente; si Redis cae, `JwtAuthFilter` queda sin forma de 
  verificar revocaciones (mitigación futura: fail-open vs fail-closed es 
  una decisión pendiente para producción).

## Referencias
- RFC 7519 (JSON Web Token)
- OWASP Top 10:2021, A07 (Identification and Authentication Failures)