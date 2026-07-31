# Evidencia — Bloque C.4: cobertura JaCoCo del dominio/servicios de la API

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-31T00:44:31Z
- **Commit**: `6ce1727` (pom.xml con jacoco-maven-plugin y el reporte en sí se
  agregan en el commit inmediatamente posterior a esta corrida)
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Propósito

Bloque C.4 de la guía: reporte de cobertura (lines/branches/complexity)
sobre el dominio/servicios de la API backend, con objetivo ≥60% para esta
entrega (≥70% en la Final). Esta es la primera corrida real con
`jacoco-maven-plugin` configurado en `backend-springboot/pom.xml`.

## Configuración aplicada

`jacoco-maven-plugin` 0.8.12 (compatible con bytecode Java 21), bindeado a
`prepare-agent` (instrumentación en test) y `report` en la fase `verify`.

Exclusiones del análisis (documentadas también como comentario en el
`pom.xml`, mismo criterio en ambos lugares):

| Paquete/clase excluida | Motivo |
|---|---|
| `com.uteq.backend.dto.**` | 16 `record` planos, solo transporte de datos, sin lógica propia. |
| `com.uteq.backend.entity.**` | Entidades JPA con `@Data` (getters/setters generados por Lombok), sin métodos de negocio propios. |
| `com.uteq.backend.config.**` | `RedisConfig`/`SecurityConfig`: definición de beans (wiring de framework). Probarlas de verdad requeriría un contexto Spring completo — ya cubierto indirectamente por los tests de arranque, no por cobertura de línea/rama aislada. |
| `com.uteq.backend.BackendApplication` | Punto de entrada (`main`), sin lógica. |

No se excluyó nada del paquete `exception`, `controller`, `security` ni
`service` — son justo los paquetes que este bloque debe medir.

## Metodología / comando ejecutado

```bash
cd backend-springboot
./mvnw clean verify
```

Copia del reporte a `docs/mediciones/jacoco/` (paso manual, no automatizado
en el `pom.xml` — criterio propio, ver nota al final):

```bash
cp backend-springboot/target/site/jacoco/jacoco.xml docs/mediciones/jacoco/report.xml
cp backend-springboot/target/site/jacoco/jacoco.csv docs/mediciones/jacoco/report.csv
cp -r backend-springboot/target/site/jacoco/. docs/mediciones/jacoco/html/
```

## Resultados crudos

Build: `BUILD SUCCESS`, `Tests run: 48, Failures: 0, Errors: 0, Skipped: 0`.
`jacoco:report` analizó 20 clases (tras exclusiones).

### Totales agregados (todas las clases analizadas)

| Métrica | Cubierto | Total | % |
|---|---|---|---|
| **Lines** | 269 | 501 | **53.69%** |
| **Branches** | 38 | 102 | **37.25%** |
| **Complexity** | 78 | 190 | **41.05%** |

### Desglose por paquete (lines / branches)

| Paquete | Lines | Branches |
|---|---|---|
| `service` | 224/304 (73.7%) | 30/62 (48.4%) |
| `scheduling` | 7/7 (100.0%) | 0/0 (n/a) |
| `controller` | 18/66 (27.3%) | 0/4 (0.0%) |
| `security` | 17/90 (18.9%) | 8/22 (36.4%) |
| `exception` | 3/34 (8.8%) | 0/14 (0.0%) |

### Clases ordenadas por % de líneas cubiertas (peor a mejor)

| % líneas | Clase | Líneas cubiertas/total |
|---|---|---|
| **0.0%** | `controller.AuthController` | 0/28 |
| **4.8%** | `security.JwtService` | 2/42 |
| **7.1%** | `security.UserDetailsServiceImpl` | 1/14 |
| **8.8%** | `exception.GlobalExceptionHandler` | 3/34 |
| **18.2%** | `security.JwtAuthFilter` | 4/22 |
| 25.0% | `controller.PrestamoController` | 3/12 |
| 42.9% | `controller.MultaController` | 3/7 |
| 42.9% | `controller.ReservacionController` | 3/7 |
| 50.0% | `controller.TestController` | 1/2 |
| 66.1% | `service.PrestamoService` | 41/62 |
| 67.5% | `service.AuthService` | 52/77 |
| 72.7% | `service.MultaService` | 32/44 |
| 75.7% | `service.LibroService` | 56/74 |
| 80.0% | `controller.LibroController` | 8/10 |
| 83.3% | `security.LoginRateLimiter` | 10/12 |
| 90.2% | `service.ReservacionService` | 37/41 |
| 100.0% | `scheduling.ReservacionScheduler` | 7/7 |
| 100.0% | `service.CorreoYaRegistradoException` | 2/2 |
| 100.0% | `service.LoginRateLimitExcedidoException` | 2/2 |
| 100.0% | `service.RefreshTokenInvalidoException` | 2/2 |

Reporte completo navegable: `docs/mediciones/jacoco/html/index.html`.
XML crudo (para tooling/CI futuro): `docs/mediciones/jacoco/report.xml`.

## Análisis breve

**53.69% de líneas — por debajo del objetivo de 60% para esta entrega.**
Número real, sin ajustar exclusiones para maquillarlo (las exclusiones
aplicadas son las mismas documentadas arriba, decididas ANTES de ver el
resultado, por su propio mérito de "no aportan lógica medible" — no se
excluyó nada después de ver que bajaba el número).

La causa no es dispersión pareja: es concentrada. `service/` (donde vive
casi toda la lógica de negocio real: `LibroService`, `PrestamoService`,
`MultaService`, `ReservacionService`, `AuthService`) ya está en 73.7%,
sobre el objetivo. El promedio baja por tres paquetes con cobertura casi
nula que si tienen tests de nivel superior (Mockito unitarios o `curl` en
vivo documentados en `docs/mediciones/sec/`) pero **ningún test JUnit
ejecuta su código real dentro del proceso de Maven** — por eso JaCoCo,
que solo instrumenta bytecode que efectivamente corre durante `mvnw
verify`, los ve en 0-20%:

- **`AuthController` (0/28 líneas)**: no existe ningún `@WebMvcTest` para
  este controller (a diferencia de `LibroControllerSecurityTest`, que sí
  cubre `LibroController`). Su comportamiento está verificado en vivo
  contra Docker real (`docs/mediciones/sec/2026-07-30-refresh-token-invalido-fix-401.md`
  y otros), pero eso no pasa por el proceso Maven, así que JaCoCo no lo
  ve.
- **`JwtService` (2/42 líneas) y `UserDetailsServiceImpl` (1/14 líneas)**:
  en `AuthServiceTest` y `LibroControllerSecurityTest` ambos se mockean
  con `@Mock`/`@MockitoBean` — exactamente lo que hace que un test unitario
  sea rápido y aislado, pero también significa que la implementación REAL
  de generación/validación de JWT y de carga de `UserDetails` (incluida la
  lógica de cuenta bloqueada/deshabilitada por multa, ver
  `GlobalExceptionHandler.handleLocked`/`handleDisabled`) nunca se
  ejecuta dentro de un test.
- **`GlobalExceptionHandler` (3/34 líneas)**: por diseño, un
  `@RestControllerAdvice` solo se invoca cuando una request real pasa por
  el `DispatcherServlet` de Spring MVC. Los tests unitarios de servicio
  (`AuthServiceTest`, etc.) verifican que se *lanza* la excepción
  correcta, pero nunca llegan a este handler — solo lo hacen los 4 tests
  de `LibroControllerSecurityTest` (que sí pasan por el filtro de Spring
  Security) y las verificaciones en vivo por `curl`.
- **`JwtAuthFilter` (4/22 líneas)**: `LibroControllerSecurityTest` importa
  el filtro real (no mockeado, ver la lección de la TAREA 1 anterior),
  pero como ninguno de esos 4 tests envía un header `Authorization` real
  (usan `@WithMockUser`, que inyecta el `SecurityContext` directamente sin
  pasar por el filtro), solo se ejecuta la rama temprana "sin header" —
  las ramas de validar/extraer un token real nunca corren.
- **`PrestamoController`/`MultaController`/`ReservacionController`
  (25-43%)**: mismo patrón que `AuthController` mismo patrón — no tienen
  su propio `@WebMvcTest` como `LibroController`; el poco código cubierto
  viene de rutas compartidas indirectas, no de tests dedicados a estos
  controllers.

**Esto NO se resuelve agregando tests de relleno.** Por instrucción
explícita, no se agregan tests solo para mover el número — la lista de
arriba (`AuthController`, `JwtService`, `UserDetailsServiceImpl`,
`GlobalExceptionHandler`, `JwtAuthFilter`) es el hallazgo real: clases
centrales de autenticación/autorización con cobertura de línea
prácticamente nula dentro del proceso de test, aunque su comportamiento
esté verificado en vivo por otros medios. Queda pendiente de confirmación
si se agregan tests dedicados (ej. un `@WebMvcTest` para `AuthController`
análogo a `LibroControllerSecurityTest`, y tests unitarios reales — sin
mockear `JwtService` — para `JwtService`/`JwtAuthFilter`) antes de volver
a correr esta medición.

**Limitación de esta medición**: JaCoCo mide únicamente ejecución dentro
del proceso `mvnw verify` (tests unitarios + `PrestamoMultaProcedureIntegrationTest`).
No incorpora, ni podría, las verificaciones en vivo contra Docker
documentadas en `docs/mediciones/sec/` — esas prueban comportamiento HTTP
end-to-end pero corren en un proceso Java completamente distinto
(el contenedor `backend`), fuera del alcance de la instrumentación de
JaCoCo en este build.

**Nota sobre el paso de copia manual**: se optó por copiar el reporte a
`docs/mediciones/jacoco/` con comandos manuales en vez de un plugin
adicional de Maven (`maven-resources-plugin`/`antrun`) atado a la fase
`verify`, para no acoplar el build a una ruta de documentación —
criterio de simplicidad, no una limitación técnica. Si esto se vuelve
parte de CI, conviene revisar entonces si automatizarlo vale la pena.

## Estado: GAP CONOCIDO (por debajo del objetivo, causa identificada)

53.69% líneas / 37.25% branches / 41.05% complexity — bajo el 60%
objetivo de esta entrega. Gap concentrado en 5 clases de
autenticación/autorización con cobertura casi nula dentro del proceso de
test (`AuthController`, `JwtService`, `UserDetailsServiceImpl`,
`GlobalExceptionHandler`, `JwtAuthFilter`), no distribuido parejo. El
paquete `service/` (lógica de negocio central) ya supera el objetivo con
73.7%. No se agregaron tests de relleno para maquillar el número —
pendiente de confirmación para escribir tests reales sobre las clases
listadas.
