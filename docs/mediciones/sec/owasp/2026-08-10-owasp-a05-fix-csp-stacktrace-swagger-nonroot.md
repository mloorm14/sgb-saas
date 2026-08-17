# Evidencia — OWASP A05:2021 Mala configuración de seguridad — GAP CERRADO (parcial) (Módulo 10.2)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-08-10T00:00:00Z
- **Rama**: `feature/seguridad-transporte`
- **Entorno**: revisión de código/configuración local (sin Docker
  disponible en este entorno de edición para reconstruir y levantar el
  stack real — ver nota de verificación pendiente al final, mismo
  criterio de honestidad que el resto de `docs/mediciones/sec/`).

## Referencia al gap original

Este archivo **no reemplaza ni edita**
[`2026-07-30-owasp-a05-mala-configuracion-seguridad.md`](2026-07-30-owasp-a05-mala-configuracion-seguridad.md).
Documenta la corrección de los puntos del checklist del Módulo 10.2 del
roadmap que dependen del **backend** (esta rama es solo backend, no toca
`frontend-angular/nginx.conf`).

## Puntos del checklist cerrados en esta rama

### 1. `Content-Security-Policy` ausente (gap independiente de TLS)

El hallazgo original marcaba esto como "omisión de configuración real,
independiente de TLS": `SecurityConfig.filterChain()` no llamaba a
`.headers(headers -> headers.contentSecurityPolicy(...))`.

**Cambio**: `SecurityConfig.java` ahora declara

`java
.contentSecurityPolicy(csp -> csp.policyDirectives(
        "default-src 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'"
))
`

Política restrictiva porque el backend no sirve HTML propio salvo Swagger
UI (que además se deshabilita en el perfil `prod`, ver punto 3).

*Nota:* solo se cierra el lado backend. El `nginx.conf` del frontend
sigue sin `Content-Security-Policy` — fuera de alcance de esta rama
(backend-only), queda como gap remanente del lado frontend.

### 2. Stacktraces detallados en producción

**Cambio**: `application.yml`, perfil `prod` (nuevo, `spring.config.activate.on-profile: prod`):

```yaml
server:
  error:
    include-stacktrace: never
    include-message: never
    include-binding-errors: never
```

Esto es una segunda capa de defensa: la capa de dominio ya devuelve
`ProblemDetail` genérico para cualquier excepción no mapeada
(`GlobalExceptionHandler.handleGenerica`, sin exponer el mensaje interno
ni el stacktrace), pero esa capa solo cubre lo que pasa por el
`DispatcherServlet`. Este ajuste cubre explícitamente también el path
`/error` por defecto de Spring Boot para cualquier error que ocurra fuera
de ese flujo (p.ej. a nivel de filtro/contenedor de servlets).

### 3. Swagger UI expuesto sin restricción

El hallazgo original y el Módulo 11.2 del roadmap coinciden en este
punto. **Cambio**: mismo perfil `prod` en `application.yml`:

```yaml
springdoc:
  swagger-ui:
    enabled: false
  api-docs:
    enabled: false
```

Fuera del perfil `prod` (dev/test/default), Swagger sigue accesible en
`/swagger-ui.html` y `/api/docs` como hoy — necesario para que el equipo
siga probando endpoints a mano en desarrollo (mismo criterio que ya
advertía la Sección 0.4 del roadmap sobre no mergear seguridad demasiado
temprano y romper Swagger para el resto del equipo).

### 4. Imagen Docker del backend corriendo como root

**Cambio**: `backend-springboot/Dockerfile`, antes del `ENTRYPOINT`:

```dockerfile
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
```

## Metodología / verificación realizada

- Revisión estática de `SecurityConfig.java` (compila: los tipos de
  `HeadersConfigurer.ContentSecurityPolicyConfig` usados ya vienen con
  `spring-boot-starter-security`, sin dependencia nueva en `pom.xml`).
- Validación de sintaxis YAML de `application.yml` con dos documentos
  (`default` + perfil `prod`) vía `yaml.safe_load_all`, confirmando que
  ambos parsean y que las claves nuevas (`server.error.*`,
  `springdoc.swagger-ui.enabled`, `springdoc.api-docs.enabled`) quedan
  correctamente anidadas bajo el segundo documento.
- Revisión manual del `Dockerfile`: el `USER spring:spring` queda después
  de todos los `COPY`/`RUN` que necesitan privilegios (instalar `curl`,
  copiar el jar), por lo que no rompe el build multi-stage.

## Verificación pendiente (requiere Docker, fuera de este entorno de edición)

Antes de mergear a `develop`, correr en un entorno con Docker disponible:

```bash
docker compose build backend
docker compose up -d backend
docker exec sgb_backend whoami                 # esperado: spring (no root)
curl -I -s http://localhost:8080/actuator/health | grep -i content-security-policy
SPRING_PROFILES_ACTIVE=prod docker compose up -d backend
curl -s http://localhost:8080/swagger-ui.html  # esperado: 404 con el perfil prod activo
```

No se incluye la salida de estos comandos en este archivo porque no se
ejecutaron realmente en este entorno (sin Docker) — incluir una salida
fabricada violaría el mismo criterio de honestidad que ya declaró
`2026-07-30-owasp-a02-fallo-criptografico.md`. Queda como paso explícito
para quien integre esta rama.

## Estado

- `Content-Security-Policy` (backend): **CERRADO**.
- Stacktraces en `/error` (perfil `prod`): **CERRADO**.
- Swagger UI restringido en `prod`: **CERRADO**.
- Contenedor backend sin root: **CERRADO** (pendiente confirmación en
  build real, ver sección anterior).
- `Content-Security-Policy` en `nginx.conf` (frontend): **fuera de
  alcance de esta rama**, sigue como gap remanente.
- `Strict-Transport-Security`: ver
  `2026-08-10-owasp-a02-fix-tls-transporte.md` (mismo origen que el gap
  de TLS, tratado por separado).
