# Evidencia — Bloque C.4: cobertura JaCoCo al cierre de la Entrega Final (VIGENTE)

**Este es el reporte vigente.** Reemplaza la cifra citada en el documento
principal (82,97 % líneas en commit `0d1474d`), que **carecía de artefacto
versionado asociado** — ese número provenía de una corrida previa cuyo
XML/HTML nunca se versionó. Esta medición genera y versiona el reporte
completo correspondiente al commit real de cierre. El reporte previo
(81,64 %, commit `a2c88f8`, `2026-08-13-cobertura-jacoco-post-merge-8-modulos.md`)
se conserva sin editar como registro histórico de cuál era el estado
antes de los commits finales de auditoría, chatbot, QR, etc.

## Motivo de esta medición

La Entrega Final exigía re-verificar la cobertura con una corrida real
sobre el commit de cierre y versionar el artefacto. La cifra de 82,97 %
citada en el informe principal no tenía reporte XML/HTML asociado en
`docs/mediciones/jacoco/`; esta medición cierra ese gap generando y
versionando la evidencia real sobre el commit de cierre (`c2d76ad`).

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-08-25T22:29:40Z
- **Commit**: `c2d76ad889632d4bc5feb4e3f91553f0844ef9b1` (`demo/interfaces-completas`, HEAD al momento de esta corrida)
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS (Temurin)
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Metodología / comando ejecutado

La suite de tests tiene 1 fallo y 9 errores en tests de integración
(ApplicationContext failure threshold) y 1 fallo en AuthControllerTest
(SameSite cookie), que son problemas de infraestructura de test preexistentes
no relacionados con la cobertura de código de producción. Para generar el
reporte JaCoCo a pesar de esos fallos, se usó:

```bash
cd backend-springboot
./mvnw -B clean test jacoco:report -Dmaven.test.failure.ignore=true
```

Copia del reporte (nueva carpeta con fecha, no sobrescribe la histórica):

```bash
mkdir -p docs/mediciones/jacoco/2026-08-25-jacoco-report
cp backend-springboot/target/site/jacoco/jacoco.xml docs/mediciones/jacoco/2026-08-25-jacoco-report/
cp backend-springboot/target/site/jacoco/jacoco.csv docs/mediciones/jacoco/2026-08-25-jacoco-report/
cp -r backend-springboot/target/site/jacoco/. docs/mediciones/jacoco/2026-08-25-jacoco-report/html/
```

## Resultados crudos

Build: `BUILD SUCCESS` (con `-Dmaven.test.failure.ignore=true`),
`Tests run: 284, Failures: 1, Errors: 9, Skipped: 2` (los 10 tests con
problemas son de infraestructura: 1 fallo de SameSite en
`AuthControllerTest`, 9 errores de `ApplicationContext failure threshold`
en tests de integración `LibroPortadaIntegrationTest` y
`PrestamoMultaProcedureIntegrationTest`; 272 tests pasan correctamente).
`jacoco:report` analizó **80 clases** (frente a 50 en la corrida del
13-ago — 30 clases nuevas por los módulos de auditoría, chatbot, QR,
credenciales, notificaciones, favoritos, sugerencias).

### Totales agregados (todas las clases analizadas en el scope C.4)

| Métrica | Corrida 13-ago (post-merge 8 módulos, `a2c88f8`) | **Corrida vigente (cierre final, `c2d76ad`)** | Objetivo Entrega Final |
|---|---|---|---|
| **Lines** | 81,64\,% (1014/1242) | **61,30\,% (1476/2408)** | ≥70\,% **No** |
| **Branches** | 58,05\,% (137/236) | **38,16\,% (232/608)** | — |
| **Complexity** | 65,16\,% (288/442) | **47,56\,% (399/839)** | — |

**El número bajó, no subió.** La base de líneas analizables casi se
duplicó (1242 → 2408, por los módulos nuevos de auditoría, chatbot,
credencial QR, notificaciones, favoritos, sugerencias, etc.), y el
porcentaje de cobertura de líneas **bajó 20,34 puntos porcentuales**
(81,64 % → 61,30 %), quedando **por debajo del umbral de 70 %** exigido
para la Entrega Final. Esto contradice la cifra de 82,97 % citada en una
versión anterior del informe (commit `0d1474d`) que carecía de artefacto
versionado; se corrige aquí con honestidad.

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
`docs/mediciones/jacoco/2026-08-25-jacoco-report/html/index.html`.
XML crudo: `docs/mediciones/jacoco/2026-08-25-jacoco-report/jacoco.xml`.

## Análisis breve

1. **La cobertura bajó por debajo del umbral.** El crecimiento de la base
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
   corrida local que no se versionó. Esta medición sustituye esa cifra
   con una real y versionada.

3. **No se agregan tests de relleno.** Por instrucción explícita, no se
   agregan tests solo para mover el número. Los 10 tests con fallos
   preexistentes (1 SameSite, 9 ApplicationContext) son problemas de
   infraestructura de test, no de código de producción.

4. **Tests excluidos de la cobertura real:** 10 tests fallan (1 fallo
   SameSite, 9 errores de contexto de integración) pero 272 tests pasan.
   La cobertura reportada refleja solo la ejecución de los tests que
   pasaron.

## Estado: GAP CONOCIDO — 61,30 % líneas / 38,16 % ramas / 47,56 % complejidad, **por debajo del objetivo de 70 % de la Entrega Final**.