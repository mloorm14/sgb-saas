# Evidencia — Bloque C.4: cobertura JaCoCo tras tests reales de seguridad/auth

**Registro histórico — NO vigente.** Reemplazado por
[`2026-08-13-cobertura-jacoco-post-merge-8-modulos.md`](2026-08-13-cobertura-jacoco-post-merge-8-modulos.md)
tras el merge a `main` de las 8 ramas de módulos nuevos de Cajas (config,
préstamos avanzado, notificaciones, QR, favoritos, admin, seguridad,
chatbot), que cambió la base de código analizable de 501 a 1242 líneas.
Para el número de cobertura actual (81,64\,% líneas), ver ese archivo —
este se conserva sin editar como evidencia de cuál era el estado antes de
ese merge.

**Este fue el reporte vigente entre el 2026-07-30 y el 2026-08-13.**
Reemplazó el número reportado en
`2026-07-30-cobertura-jacoco-dominio-servicios.md` (53.69%), que se
conserva como registro histórico del hallazgo que motivó este trabajo —
no lo edites para "corregir" el número viejo. `docs/mediciones/jacoco/report.xml`,
`report.csv` y `html/` ya están sobrescritos con la corrida vigente
actual (son artefactos generados, no un log append-only; siempre
reflejan la última corrida real de `mvnw clean verify`).

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-31T01:03:32Z
- **Commit**: `50ccecc` (este archivo y el reporte se agregan en el commit
  inmediatamente posterior)
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Propósito

Bloque C.4. La corrida anterior (53.69% líneas) identificó 5 clases de
autenticación/autorización con cobertura casi nula dentro del proceso de
test, todas mockeadas en los tests existentes en vez de ejercitadas de
verdad: `JwtService`, `JwtAuthFilter`, `UserDetailsServiceImpl`,
`AuthController`, `GlobalExceptionHandler`. Se agregaron 4 clases de test
nuevas (una por clase real, más un quinto commit cerrando gaps
específicos de `GlobalExceptionHandler`) que ejercitan la implementación
real de cada una, sin mockear la clase bajo prueba. Ningún test se agregó
para inflar el número — cada uno verifica un comportamiento real y
específico (ver commits `b110a49`, `d6d5ff3`, `24c39d3`, `30b03b9`,
`50ccecc`).

## Metodología / comando ejecutado

```bash
cd backend-springboot
./mvnw clean verify
```

Copia del reporte (sobrescribe el anterior):

```bash
rm -rf docs/mediciones/jacoco/html
cp backend-springboot/target/site/jacoco/jacoco.xml docs/mediciones/jacoco/report.xml
cp backend-springboot/target/site/jacoco/jacoco.csv docs/mediciones/jacoco/report.csv
cp -r backend-springboot/target/site/jacoco/. docs/mediciones/jacoco/html/
```

## Resultados crudos

Build: `BUILD SUCCESS`, `Tests run: 79, Failures: 0, Errors: 0, Skipped: 0`
(48 preexistentes + 31 nuevos: 8 `JwtServiceTest` + 5 `JwtAuthFilterTest` +
5 `UserDetailsServiceImplTest` + 13 `AuthControllerTest`). **Los 48 tests
preexistentes siguen en verde, ninguno se rompió.**

### Totales agregados

| Métrica | Corrida anterior | Corrida actual | Objetivo entrega |
|---|---|---|---|
| **Lines** | 53.69% (269/501) | **75.85% (380/501)** | ≥60% ✅ (supera incluso el 70% de la Final) |
| **Branches** | 37.25% (38/102) | **49.02% (50/102)** | — |
| **Complexity** | 41.05% (78/190) | **58.42% (111/190)** | — |

### Desglose por paquete (lines / branches)

| Paquete | Corrida anterior | Corrida actual |
|---|---|---|
| `security` | 18.9% / 36.4% | **95.6% / 68.2%** |
| `controller` | 27.3% / 0.0% | **69.7% / 75.0%** |
| `exception` | 8.8% / 0.0% | **50.0% / 14.3%** |
| `service` | 73.7% / 48.4% | 73.7% / 48.4% (sin cambios, no era el foco) |
| `scheduling` | 100.0% | 100.0% (sin cambios) |

### Clases objetivo de esta ronda (antes → después, % líneas)

| Clase | Antes | Después |
|---|---|---|
| `security.JwtService` | 4.8% (2/42) | **95.2% (40/42)** |
| `security.JwtAuthFilter` | 18.2% (4/22) | **100.0% (22/22)** |
| `security.UserDetailsServiceImpl` | 7.1% (1/14) | **100.0% (14/14)** |
| `controller.AuthController` | 0.0% (0/28) | **100.0% (28/28)** |
| `exception.GlobalExceptionHandler` | 8.8% (3/34) | **50.0% (17/34)** |

### Clases restantes por debajo de 60% (todavía sin tests dedicados)

| % líneas | Clase | Nota |
|---|---|---|
| 25.0% | `controller.PrestamoController` | Fuera de alcance de esta ronda (foco fue auth/seguridad). |
| 42.9% | `controller.MultaController` | Igual. |
| 42.9% | `controller.ReservacionController` | Igual. |
| 50.0% | `controller.TestController` | Endpoint de debug sin valor de negocio real — candidato a eliminar en vez de testear. |
| 50.0% | `exception.GlobalExceptionHandler` | Ver análisis abajo — gap residual conocido, no cerrado a propósito. |

Reporte completo navegable: `docs/mediciones/jacoco/html/index.html`.

## Análisis breve

**Objetivo cumplido con margen: 75.85% de líneas, sobre el 60% exigido
para esta entrega y ya por encima del 70% fijado para la Final.** El
salto de 53.69% → 75.85% viene enteramente de las 5 clases identificadas
como hallazgo en la corrida anterior — el resto del código (`service/`,
`scheduling/`) no se tocó porque ya estaba sobre el objetivo.

`JwtAuthFilter` y `UserDetailsServiceImpl` llegaron a 100% de líneas: son
clases pequeñas y con pocas ramas, así que los casos de test escritos
(con header/sin header, token válido/inválido/en blacklist, estado
ACTIVO/BLOQUEADO_POR_MULTA/INACTIVO/PENDIENTE_VERIFICACION) terminaron
cubriendo cada línea ejecutable. `AuthController` igual: los 13 tests
cubren los 4 endpoints en éxito y en cada rama de error mapeada por
`GlobalExceptionHandler`.

**`GlobalExceptionHandler` quedó en 50% (17/34), no más alto, a
propósito.** Subió de 8.8% a 50% como efecto colateral de TAREA 1-4 más 3
casos puntuales agregados en TAREA 5 (`handleLocked`, `handleDisabled`,
`handleValidation`). Las líneas que siguen sin cubrir son:
`handleNotFound` (`EntityNotFoundException`, no aplica a ningún flujo de
`AuthController`), y `handleGenerica` (el catch-all de último recurso —
cubrirlo requeriría forzar una excepción no mapeada, que por diseño no
debería ocurrir en un flujo real). `handleStoredProcedureError` ya tenía
cobertura parcial previa vía `PrestamoMultaProcedureIntegrationTest` (los
casos `LB422`/`LB409`), no se tocó en esta ronda. No se agregaron tests
artificiales para forzar estas líneas — no son parte del alcance de
"auth/seguridad" de esta tarea y forzarlas sería exactamente el tipo de
test de relleno que se pidió evitar.

**Pendiente real para la Entrega Final** (no cubierto por esta ronda,
fuera de su alcance explícito):
- `PrestamoController`/`MultaController`/`ReservacionController` (25-43%
  líneas): mismo patrón que tenía `AuthController` antes de esta ronda —
  sin `@WebMvcTest` dedicado. `LibroController` (80%) y ahora
  `AuthController` (100%) ya establecen el patrón a replicar.
- `TestController`: endpoint de debug (`GET /api/test/protegido`, solo
  confirma que el JWT es válido) sin valor de negocio — más que testearlo,
  vale evaluar si debería eliminarse antes de la entrega final.
- Cobertura de `branches` (49.02%) y `complexity` (58.42%) siguen más
  bajas que `lines` — esperable, ya que varios de los tests nuevos cubren
  el camino feliz y el de error más común de cada método, no
  necesariamente cada combinación de condiciones internas.

## Estado: PASA (objetivo de 60% líneas superado: 75.85%)

Las 5 clases identificadas en la corrida anterior como el hallazgo
principal (`JwtService`, `JwtAuthFilter`, `UserDetailsServiceImpl`,
`AuthController`, `GlobalExceptionHandler`) pasaron de cobertura casi
nula a 50-100% con tests que ejercitan la implementación real, no mocks.
79/79 tests en verde (48 preexistentes intactos + 31 nuevos). Gap
residual documentado y honesto en `PrestamoController`/`MultaController`/
`ReservacionController` (fuera de alcance de esta ronda) y en las 2
ramas de `GlobalExceptionHandler` que no aplican a ningún flujo real de
`AuthController` — queda para la Entrega Final, no maquillado aquí.
