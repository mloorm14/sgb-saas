# Evidencia — OWASP A07:2021 Fallos de identificación y autenticación (Bloque C.2)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-30T19:28:50Z
- **Commit**: `fc9dce8`
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Propósito

Bloque C.2: enviar 6 intentos fallidos consecutivos de login desde el
mismo origen contra el mismo usuario, y verificar si existe algún
mecanismo de rate limiting/bloqueo (se espera `429` desde el sexto
intento, según la guía).

## Verificación previa en código (antes de correr la prueba)

`grep` sobre `backend-springboot/src` y `pom.xml` por cualquier
mecanismo de rate limiting (`RateLimit`, `bucket4j`, `throttl*`): sin
resultados. No hay ninguna librería ni filtro de limitación de tasa en
el proyecto — se documenta esto antes de correr la prueba para dejar
explícito que el resultado esperado, dado el estado real del código,
es que **no** aparezca ningún `429`.

## Metodología / comando ejecutado

```bash
for i in 1 2 3 4 5 6; do
  curl --include -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
    -d '{"correo":"usuarioA.owasp@sgb-saas.local","password":"ClaveIncorrada'"$i"'"}'
done
```

6 intentos consecutivos, mismo usuario (`usuarioA.owasp@sgb-saas.local`,
creado en la evidencia de A01), mismo origen (`localhost`, sin
cambiar de IP/cliente entre intentos), password incorrecta distinta en
cada intento (para descartar cualquier caché de resultado por
password idéntica repetida).

## Resultados crudos

Los 6 intentos, sin editar (idénticos salvo el timestamp):

```
--- intento 1 ---
HTTP/1.1 401
...
{"detail":"Credenciales inválidas","instance":"/api/auth/login","status":401,"title":"Unauthorized"}
--- intento 2 ---
HTTP/1.1 401
...
{"detail":"Credenciales inválidas","instance":"/api/auth/login","status":401,"title":"Unauthorized"}
--- intento 3 ---
HTTP/1.1 401
...
{"detail":"Credenciales inválidas","instance":"/api/auth/login","status":401,"title":"Unauthorized"}
--- intento 4 ---
HTTP/1.1 401
...
{"detail":"Credenciales inválidas","instance":"/api/auth/login","status":401,"title":"Unauthorized"}
--- intento 5 ---
HTTP/1.1 401
...
{"detail":"Credenciales inválidas","instance":"/api/auth/login","status":401,"title":"Unauthorized"}
--- intento 6 ---
HTTP/1.1 401
...
{"detail":"Credenciales inválidas","instance":"/api/auth/login","status":401,"title":"Unauthorized"}
```

(Cabeceras completas idénticas en los 6 casos:
`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
`Content-Type: application/problem+json`, sin ningún header
`Retry-After`, `X-RateLimit-*` ni similar en ninguno de los 6.)

## Análisis breve

**Resultado real: los 6 intentos devuelven `401` de forma idéntica —
no hay ningún `429`, ni en el sexto intento ni en ninguno posterior.**
No se simula el resultado esperado por la guía; se documenta lo que el
sistema realmente hace hoy.

Causa raíz confirmada en código: no existe ningún mecanismo de
limitación de intentos de login — ni a nivel de aplicación (sin
`bucket4j`/filtro de rate limiting propio), ni a nivel de
infraestructura (sin proxy/gateway delante que lo aplique en este
entorno de desarrollo). `BadCredentialsException` se maneja igual la
primera vez que la sexta (`GlobalExceptionHandler.handleBadCredentials`),
sin ningún contador ni estado acumulado por usuario/IP.

Esto es un **vector real de fuerza bruta de contraseñas**: un
atacante puede probar credenciales sin límite contra cualquier cuenta
conocida (el correo es público/adivinable en muchos casos). El
mecanismo de `BLOQUEADO_POR_MULTA` (`estados_usuario`, ya verificado
en sesiones anteriores) es una protección de negocio no relacionada —
no se activa por intentos fallidos de login, solo por multas
pendientes.

## Estado: GAP CONOCIDO

No implementado. Candidato real para un prompt de corrección
independiente — opciones típicas: contador de intentos fallidos por
`correo`+IP en Redis (ya presente en el stack, usado hoy solo para la
blacklist de JWT) con bloqueo temporal tras N intentos, o un filtro de
rate limiting genérico (`bucket4j-spring-boot-starter` u equivalente)
delante de `/api/auth/login`. No se implementa en este archivo de
evidencia — es diagnóstico, no corrección.
