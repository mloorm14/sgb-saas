# Evidencia — Bloque C.4: cobertura JaCoCo tras el merge de las 8 ramas de módulos nuevos (VIGENTE)

**Este es el reporte vigente.** Reemplaza el número citado hasta ahora
(75,85\,% líneas, corrida del 2026-07-30,
`2026-07-30-cobertura-jacoco-clases-seguridad-vigente.md`), que se
conserva sin editar como registro histórico de cuál era el estado antes
de este merge — no se borra, es evidencia. `docs/mediciones/jacoco/report.xml`,
`report.csv` y `html/` ya están sobrescritos con esta corrida (son
artefactos generados, no un log append-only; siempre reflejan la última
corrida real de `mvnw clean verify`, mismo criterio que la corrida
anterior).

## Motivo de esta medición

El reporte del 30 de julio se generó **antes** de mergear a `main` las 8
ramas de trabajo de Cajas (config, préstamos avanzado, notificaciones,
QR, favoritos, admin, seguridad, chatbot). Con 8 módulos nuevos de
dominio agregados, era razonable esperar que el número de cobertura
citado hasta ahora ya no fuera representativo del estado real del
código — esta medición verifica eso directamente en vez de asumir que el
número viejo seguía vigente.

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-08-13T19:21:51Z
- **Commit**: `a2c88f8` (`main`, HEAD al momento de esta corrida)
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS (Temurin)
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Metodología / comando ejecutado

```bash
docker compose up -d --build
cd backend-springboot
./mvnw -B clean verify
```

Copia del reporte (sobrescribe el anterior, mismo criterio que la corrida
del 30 de julio):

```bash
rm -rf docs/mediciones/jacoco/html
cp backend-springboot/target/site/jacoco/jacoco.xml docs/mediciones/jacoco/report.xml
cp backend-springboot/target/site/jacoco/jacoco.csv docs/mediciones/jacoco/report.csv
cp -r backend-springboot/target/site/jacoco/. docs/mediciones/jacoco/html/
```

## Resultados crudos

Build: `BUILD SUCCESS`, `Tests run: 203, Failures: 0, Errors: 0, Skipped: 2`
(frente a 79 tests en la corrida del 30 de julio — 124 tests nuevos,
coherente con 8 módulos de dominio agregados). `jacoco:report` analizó
**50 clases** (frente a 20 clases el 30 de julio).

### Totales agregados (todas las clases analizadas)

| Métrica | Corrida 30-jul (post-fix auth) | **Corrida vigente (post-merge 8 módulos)** | Objetivo Entrega Final |
|---|---|---|---|
| **Lines** | 75,85\,% (380/501) | **81,64\,% (1014/1242)** | ≥70\,% ✅ (sigue superándolo) |
| **Branches** | 49,02\,% (50/102) | **58,05\,% (137/236)** | — |
| **Complexity** | 58,42\,% (111/190) | **65,16\,% (288/442)** | — |

**El número subió, no bajó.** La base de líneas analizables casi se
triplicó (501 → 1242, por los 8 módulos nuevos), y aun así el porcentaje
de cobertura de líneas subió 5,79 puntos porcentuales (75,85\,% →
81,64\,%) en vez de diluirse. Esto no estaba garantizado de antemano —
el propósito explícito de esta tarea era verificar el número real en vez
de asumir que se mantenía, y el resultado real confirma que sigue por
encima del umbral de 70\,% exigido, con margen.

### Desglose por paquete (lines / branches)

| Paquete | Lines | Branches |
|---|---|---|
| `scheduling` | 46/46 (100,0\,%) | 4/4 (100,0\,%) |
| `security` | 94/98 (95,9\,%) | 22/30 (73,3\,%) |
| `service` | 750/843 (89,0\,%) | 106/160 (66,2\,%) |
| `controller` | 94/149 (63,1\,%) | 3/8 (37,5\,%) |
| `exception` | 17/41 (41,5\,%) | 2/14 (14,3\,%) |
| `integration` | 13/65 (20,0\,%) | 0/20 (0,0\,%) |

### Clases por debajo del 70\,% de líneas cubiertas (peor a mejor)

| % líneas | Clase | Líneas cubiertas/total |
|---|---|---|
| **17,4\,%** | `controller.PrestamoController` | 4/23 |
| **20,0\,%** | `integration.GeminiClient` | 13/65 |
| **37,5\,%** | `controller.FavoritoController` | 3/8 |
| **41,5\,%** | `exception.GlobalExceptionHandler` | 17/41 |
| **42,9\,%** | `controller.AutorController` | 3/7 |
| **42,9\,%** | `controller.CategoriaController` | 3/7 |
| **42,9\,%** | `controller.CredencialQrController` | 3/7 |
| **42,9\,%** | `controller.MultaController` | 3/7 |
| **42,9\,%** | `controller.ReservacionController` | 3/7 |
| 50,0\,% | `controller.TestController` | 1/2 |
| 53,3\,% | `controller.LibroController` | 8/15 |
| 69,1\,% | `service.LibroService` | 76/110 |

Todas las demás clases analizadas (38 de 50) están en 72,7\,% o más;
17 de ellas en 100,0\,%. Reporte completo navegable:
`docs/mediciones/jacoco/html/index.html`. XML crudo:
`docs/mediciones/jacoco/report.xml`.

## Análisis breve

1. **La cobertura de líneas subió pese al crecimiento de la base de
   código.** El patrón de "cobertura casi nula en clases de
   seguridad/auth" que motivó el trabajo del 30 de julio no se repite en
   los módulos nuevos: `security` está en 95,9\,%, `service` en 89,0\,%
   (`AuthService`, `ChatbotRateLimiter`, `EmailService`, `UsuarioAdminService`,
   entre otros de los módulos nuevos de Cajas, en 100,0\,%).

2. **El paquete `controller` (63,1\,%) y en especial `integration`
   (20,0\,%, una sola clase: `GeminiClient`) son ahora los más bajos.**
   No se investiga la causa exacta en esta tarea (fuera de su alcance:
   solo se pidió el número real y el archivo de evidencia, no un nuevo
   ciclo de tests dirigidos como el del 30 de julio) — queda como
   candidato para un trabajo futuro análogo al de las clases de
   seguridad, si el equipo decide priorizarlo. `GeminiClient` en
   particular es esperable que tenga cobertura baja dentro del proceso
   de test: es el cliente HTTP hacia la API externa de Gemini
   (REQ-F-028), y un test unitario típico lo mockea en vez de ejercitar
   sus ramas reales de manejo de errores de red.

3. **No se agregó ni se quitó ningún test con el propósito de mover este
   número.** Los 203 tests son los que ya existían en `main` al momento
   de esta corrida (incluidos los de las 8 ramas ya mergeadas); esta
   tarea solo ejecutó `mvnw clean verify` y extrajo el resultado real del
   reporte generado.

## Estado: VIGENTE — 81,64\,% líneas / 58,05\,% ramas / 65,16\,% complejidad, por encima del objetivo de 70\,% de la Entrega Final.
