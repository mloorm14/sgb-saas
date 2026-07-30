# Evidencia — OWASP A03:2021 Inyección (Bloque C.2)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-30T19:26:18Z
- **Commit**: `69820d5`
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Propósito

Bloque C.2: enviar el payload clásico `' OR '1'='1` (y una variante con
`DROP TABLE`) contra campos de texto libre, y confirmar que ningún caso
produce un 500, una fuga de datos, ni ejecución de SQL inesperada.

## Nota de premisa — corrección respecto a lo esperado por la guía

La guía anticipa una respuesta **422**. En este proyecto específico,
las violaciones de `@Valid`/Bean Validation (formato de campo inválido)
las traduce `GlobalExceptionHandler.handleValidation()` a **400 Bad
Request**, no 422 — el 422 en este código está reservado para
violaciones de **reglas de negocio** señalizadas por SQLSTATE `LB422`
desde los procedimientos almacenados (ver
`docs/basedatos/CATALOGO-SP.md`), un caso distinto al de "el campo
`correo` no tiene formato de email". Se documenta el código real
observado (400) en vez de forzar que la evidencia diga 422 para
coincidir con la expectativa genérica de la guía — lo que importa para
A03 (nunca 500, nunca comportamiento anómalo, respuesta controlada
`ProblemDetail`) se cumple igual con 400 que con 422.

## Metodología / comando ejecutado

**Caso 1** — payload en el campo `correo` de `/api/auth/login` (el
caso exacto que sugiere la guía):

```bash
curl --include -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d "{\"correo\":\"' OR '1'='1\",\"password\":\"cualquiera\"}"
```

**Caso 2** — el campo `correo` de login tiene `@Email`, que rechaza el
payload por *formato* antes de que llegue a ninguna consulta — no es
una prueba fuerte de resistencia a inyección en sí, solo de validación
de formato. Para probar el mecanismo real (parámetros nombrados/JPA
parametrizado, no la validación de formato), se repite el payload en
`nombre`/`apellido` de `/api/auth/registro` — campos de texto libre sin
restricción de formato, que si el proyecto concatenara SQL a mano
serían el vector real:

```bash
curl --include -s -X POST http://localhost:8080/api/auth/registro -H "Content-Type: application/json" \
  -d "{\"nombre\":\"' OR '1'='1\",\"apellido\":\"'; DROP TABLE usuarios; --\",\"correo\":\"usuarioC.owasp@sgb-saas.local\",\"password\":\"ClaveSegura123!\"}"
```

Verificación posterior de integridad: login de un usuario creado
*antes* de este payload, para confirmar que la tabla `usuarios` sigue
intacta.

## Resultados crudos

**Caso 1 — login con payload en `correo`:**
```
HTTP/1.1 400
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json
Transfer-Encoding: chunked
Date: Thu, 30 Jul 2026 19:25:47 GMT
Connection: close

{"detail":"Datos inválidos","instance":"/api/auth/login","status":400,"title":"Bad Request","errores":{"correo":"must be a well-formed email address"}}
```

**Caso 2 — registro con payload (incluye intento de `DROP TABLE`) en `nombre`/`apellido`:**
```
HTTP/1.1 201
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked
Date: Thu, 30 Jul 2026 19:26:00 GMT

{"id":5,"nombre":"' OR '1'='1","correo":"usuarioC.owasp@sgb-saas.local","roles":["LECTOR"]}
```

**Verificación de integridad** (login de un usuario registrado antes del payload):
```
HTTP_STATUS:200
```

## Análisis breve

**Caso 1**: el payload nunca llega a ninguna consulta SQL — `@Email`
lo rechaza en la capa de validación del DTO (`400`, `ProblemDetail`).
Ningún endpoint de este proyecto expone un parámetro de búsqueda de
texto libre directamente a query SQL (`LibroRepository` no tiene un
método de búsqueda por título expuesto vía `@RequestParam` en ningún
controller — se verificó revisando `LibroController`/`LibroRepository`
antes de escribir esta evidencia); todos los métodos de acceso a datos
usan métodos derivados de Spring Data JPA (parametrizados
automáticamente) o los 7 procedimientos de `db/procs/` invocados con
parámetros nombrados (`@Procedure`/`@Query(nativeQuery)` con `:p_...`,
nunca concatenación — ver `docs/adr/adr-013-acceso-datos-orm-sp.md` y
el requisito A.2.3 de la guía). Esta es la razón de fondo por la que
A03 no tiene un endpoint "vulnerable" que probar: la arquitectura no
tiene ninguna ruta de SQL dinámico/concatenado expuesta al cliente, no
es que no se haya buscado lo suficiente.

**Caso 2**: el payload (`' OR '1'='1` y `'; DROP TABLE usuarios; --`)
se guardó **literalmente como texto** en las columnas `nombre`/
`apellido` — el `201 Created` devuelve el string sin ninguna
transformación, y la sentencia `DROP TABLE` **no se ejecutó**: el
login posterior de un usuario preexistente devuelve `200`, prueba
directa de que la tabla `usuarios` sigue intacta. Esto confirma en
vivo (no solo por inspección de código) que el `INSERT` generado por
Hibernate a partir de `UsuarioRepository.save()` usa parámetros
bindeados (`PreparedStatement`), no concatenación de strings.

## Estado: PASA
