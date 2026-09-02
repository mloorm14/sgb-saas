# Evidencia — Bloque C.4: cobertura JaCoCo al cierre de la Entrega Final — Medición final (VIGENTE)

**Este es el reporte definitivo.** Reemplaza la medición intermedia (`c2d76ad`, 61,30 %) documentada en `2026-08-25-cobertura-jacoco-cierre-entrega-final.md`, que permanece inalterada como registro histórico del estado intermedio (antes de los fixes de Testcontainers y SameSite). Esta medición final corresponde al commit de cierre real y versiona el reporte completo correspondiente.

## Motivo de esta medición

La Entrega Final exige re-verificar la cobertura con una corrida real sobre el commit de cierre, sin fallos ni errores, y versionar el artefacto completo. La medición intermedia (`c2d76ad`, 61,30 %) se obtuvo con `-Dmaven.test.failure.ignore=true` porque 10 tests fallaban (1 SameSite, 9 ApplicationContext). Esta medición final se ejecuta **sin** ignorar fallos, tras dos fixes en la misma línea de commits:

1. **Testcontainers** añadido para las 2 clases de integración (`LibroPortadaIntegrationTest`, `PrestamoMultaProcedureIntegrationTest`) que fallaban por falta de Postgres real (commit `825ad34`).
2. **Fix de SameSite** en la aserción del test `AuthControllerTest` (Strict → None, este mismo commit, no revertido en código fuente — el cambio de `SameSite=Strict` a `None` en `AuthController.java` ya estaba en `2884126`).

Esta medición final se ejecuta con `mvnw clean verify` **sin** `-Dmaven.test.failure.ignore=true` y obtiene **BUILD SUCCESS** con 0 fallos y 0 errores. Es la cifra definitiva para el commit HEAD (`demo/interfaces-completas`).

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-08-25T22:29:40Z
- **Commit**: `825ad34a889246450cef40e88c5ae5cd49f7ffc9` (`demo/interfaces-completas`, HEAD al momento de esta corrida)
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS (Temurin)
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Metodología / comando ejecutado

La suite de tests ahora pasa completamente (0 fallos, 0 errores) tras los dos fixes citados. Para generar el reporte JaCoCo definitivo:

```bash
cd backend-springboot
./mvnw -B clean verify
```

Copia del reporte (nueva carpeta con fecha, no sobrescribe la histórica):

```bash
mkdir -p docs/mediciones/jacoco/2026-08-25-jacoco-final
cp backend-springboot/target/site/jacoco/jacoco.xml docs/mediciones/jacoco/2026-08-25-jacoco-final/
cp backend-springboot/target/site/jacoco/jacoco.csv docs/mediciones/jacoco/2026-08-25-jacoco-final/
cp -r backend-springboot/target/site/jacoco/. docs/mediciones/jacoco/2026-08-25-jacoco-final/html/
```

## Resultados crudos

Build: `BUILD SUCCESS` (sin flags de ignore),
`Tests run: 284, Failures: 0, Errors: 0, Skipped: 2` — **BUILD SUCCESS limpio**.
`jacoco:report` analizó **80 clases** (igual que la medición intermedia; los 9 tests de integración ahora pasan y contribuyen a la cobertura).

### Totales agregados (todas las clases analizadas en el scope C.4)

| Métrica | Corrida 13-ago (post-merge 8 módulos, `a2c88f8`) | **Corrida final (cierre definitivo, `825ad34`)** | Objetivo Entrega Final |
|---|---|---|---|
| **Lines** | 81,64\,% (1014/1242) | **61,50\,% (1481/2408)** | ≥70\,% **No** |
| **Branches** | 58,05\,% (137/236) | **38,98\,% (237/608)** | — |
| **Complexity** | 65,16\,% (288/442) | **48,15\,% (404/839)** | — |

**El número sube ligeramente respecto a la medición intermedia.** La base de líneas analizables se mantiene en 2408; los 9 tests de integración ahora pasan y aportan cobertura real. La cobertura de líneas sube de 61,30 % a 61,50 % (+0,20 pp), ramas de 38,16 % a 38,98 % (+0,82 pp), complejidad de 47,56 % a 48,15 % (+0,59 pp). Sigue **por debajo del umbral de 70 %** exigido para la Entrega Final. Esto contradice la cifra de 82,97 % citada en una versión anterior del informe (commit `0d1474d`) que carecía de artefacto versionado; se corrige aquí con honestidad.

### Desglose por paquete (lines / branches)

| Paquete | Lines | Branches |
|---|---|---|
| `scheduling` | 53/58 (91,38 %) | 4/4 (100,0 %) |
| `security` | 109/128 (85,16 %) | 22/30 (73,33 %) |
| `service` | 1085/1740 (62,36 %) | 106/160 (66,25 %) |
| `controller` | 194/311 (62,38 %) | 3/8 (37,5 %) |
| `exception` | 20/47 (42,55 %) | 2/14 (14,29 %) |
| `integration` | 15/124 (12,10 %) | 0/20 (0,0 %) |
| `chatbot` | 40/42 (95,24 %) | 0/0 (n/a) |
| `chatbot.tool` | 13/15 (86,67 %) | 0/0 (n/a) |

### Clases por debajo del 70 % de líneas cubiertas (peor a mejor)

| % líneas | Clase | Líneas cubiertas/total |
|---|---|---|
| **12,10 %** | `integration.GeminiClient` | 15/124 |
| **20,00 %** | `controller.PrestamoController` | 4/23 |
| **37,50 %** | `controller.FavoritoController` | 3/8 |
| **41,5 %** | `exception.GlobalExceptionHandler` | 17/41 |
| **42,55 %** | `exception` (paquete) | 20/47 |
| **42,86 %** | `controller.AutorController` | 3/7 |
| **42,86 %** | `controller.CategoriaController` | 3/7 |
| **42,86 %** | `controller.CredencialQrController` | 3/7 |
| **42,86 %** | `controller.MultaController` | 3/7 |
| **42,86 %** | `controller.ReservacionController` | 3/7 |
| **46,88 %** | `security` (clases bajas) | — |
| **50,00 %** | `controller.TestController` | 1/2 |
| **53,33 %** | `controller.LibroController` | 8/15 |
| **60,00 %** | `service.LibroService` | 76/110 |
| **62,36 %** | `service` (paquete) | 1085/1740 |
| **62,38 %** | `controller` (paquete) | 194/311 |
| **62,4 %** | `service.PrestamoService` | — |
| **62,4 %** | `service.ReservacionService` | — |

Todas las demás clases analizadas (51 de 80) están en 62,7 % o más;
17 de ellas en 100,0 %. Reporte completo navegable:
`docs/mediciones/jacoco/2026-08-25-jacoco-final/html/index.html`.
XML crudo: `docs/mediciones/jacoco/2026-08-25-jacoco-final/jacoco.xml`.

## Análisis breve

1. **La cobertura sigue por debajo del umbral.** El crecimiento de la base
   de código (1242 → 2408 líneas analizables) por los módulos nuevos
   (auditoría, chatbot, QR, notificaciones, favoritos, sugerencias,
   credenciales, portal público) diluyó la cobertura. Los paquetes
   `integration` (12,1 %, `GeminiClient`), `exception` (42,6 %),
   `controller` (62,4 %) y `service` (62,4 %) están bajo el 70 %. Solo
   `scheduling` (91,4 %), `security` (85,2 %) y los nuevos `chatbot`
   (95,2 %) lo superan.

2. **La cifra anterior (82,97 %) era incorrecta / sin evidencia.**
   El documento principal citaba 82,97 % (1208/1456) en commit
   `0d1474d` pero **no existía ningún XML/HTML versionado** para esa
   corrida — el número provenía de `target/site/jacoco/jacoco.csv` de una
   corrida local que no se versionó. La medición intermedia (`c2d76ad`,
   61,30 %) ya corrigió esa cifra; esta medición final (`825ad34`,
   61,50 %) incorpora la cobertura real de los 9 tests de integración
   habilitados por Testcontainers y del test de cookie SameSite corregido.

3. **No se agregan tests de relleno.** Por instrucción explícita, no se
   agregan tests solo para mover el número. Los tests que antes fallaban
   (1 SameSite, 9 ApplicationContext) ahora pasan tras los fixes de
   infraestructura y aserción — no son "tests de relleno", son tests reales
   que ahora se ejecutan correctamente.

4. **Todos los tests pasan.** La suite completa ejecuta 284 tests con
   0 fallos, 0 errores, 2 skipped — **BUILD SUCCESS limpio**. La cobertura
   reportada refleja la ejecución completa de la suite.

## Estado: GAP CONOCIDO — 61,50 % líneas / 38,98 % ramas / 48,15 % complejidad, **por debajo del objetivo de 70 % de la Entrega Final**. Medición final y definitiva para el commit HEAD (`demo/interfaces-completas`).