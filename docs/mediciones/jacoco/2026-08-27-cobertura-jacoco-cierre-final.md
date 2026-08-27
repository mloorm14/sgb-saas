# Evidencia — Bloque C.4: cobertura JaCoCo, medición de cierre definitiva (retracta 61,50 %)

**Este es el reporte definitivo y vigente.** Reemplaza, para efectos de la
cifra citada como "actual" en el informe, tanto la medición intermedia
(`c2d76ad`, 61,30 %, en
`2026-08-25-cobertura-jacoco-cierre-entrega-final.md`) como la medición
final previa (`825ad34`, en `2026-08-25-cobertura-jacoco-final.md`).
Ninguno de los dos archivos anteriores se modifica ni se elimina: ambos
permanecen versionados como registro histórico. Esta medición corresponde
a HEAD real de `demo/interfaces-completas` al momento de esta corrida
(`89eb6ab`) e incorpora una retractación explícita explicada abajo.

## Motivo de esta medición: retractación de la cifra 61,50 %

Durante una auditoría de rúbrica se detectó que la cifra citada en el
Capítulo 8 y en `2026-08-25-cobertura-jacoco-final.md` para el commit
`825ad34` (**61,50 %, 1481/2408 líneas**) no coincidía con el propio
artefacto XML versionado en esa misma carpeta
(`docs/mediciones/jacoco/2026-08-25-jacoco-final/jacoco.xml`), que arroja
**55,25 % (1541/2789 líneas)**.

Se investigó reconstruyendo el commit `825ad34` de forma aislada
(`git worktree add --detach`, sin tocar el árbol de trabajo principal),
**dos veces de forma independiente**:

1. Un primer intento de `mvnw clean verify` sobre `825ad34` termina en
   **BUILD FAILURE** (1 test falla: `AuthControllerTest` espera
   `SameSite=Strict`, pero la cookie real ya emite `SameSite=None` en ese
   commit -- exactamente el desfase de un carácter que el commit
   `87499f8`, inmediatamente posterior, corrige un commit después). Por
   el fallo, Maven no llega a la fase `jacoco:report`.
2. El `.exec` binario generado por los 284 tests que sí corrieron antes
   del fallo se conserva. Se ejecuta `mvnw jacoco:report` directamente
   contra ese `.exec` para obtener el XML sin re-correr los tests.

El XML resultante de este segundo método coincide **exactamente, en los
seis contadores** (`LINE`, `INSTRUCTION`, `BRANCH`, `COMPLEXITY`,
`METHOD`, `CLASS`) con el XML ya versionado en
`docs/mediciones/jacoco/2026-08-25-jacoco-final/jacoco.xml`. Es decir: el
artefacto ya versionado para `825ad34` es **correcto** y queda
re-verificado de forma independiente. Ese archivo **no se toca** en este
cambio.

**La cifra de 61,50 % (1481/2408), en cambio, no pudo reproducirse en
ninguna de las dos reconstrucciones de `825ad34`.** No se trata de un
artefacto correcto que se perdió o fue sobrescrito por la corrida de un
commit posterior (como se conjeturó inicialmente) -- es un error de
origen en el texto narrado, que aquí se **retracta explícitamente**. La
cifra correcta y triple-verificada para `825ad34` es **55,25 % (1541
líneas cubiertas de 2789)**.

## Cifra vigente de cierre: HEAD `89eb6ab`

Entre `825ad34`/`87499f8` y HEAD (`89eb6ab`) se incorporó trabajo de
funcionalidad real -- no solo cambios de documentación -- correspondiente
al módulo de auditoría (endpoint de resumen, vista de tarjetas con
drill-down, instrumentación de escritura en 6 servicios, categoría
`registro_danos`). Esto crece la base de líneas analizables de 2789 a
2880 de forma legítima. Se remide en fresco sobre HEAD para tener la
cifra de cierre real, no una proyectada.

## Cabecera de medición

- **Commit**: `89eb6abf99a71d5311133dda1a95f72b83e168a2` (`demo-merge-work`,
  sincronizado con `origin/demo/interfaces-completas`)
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS (Temurin)
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (Testcontainers, imagen fijada en el código de test):
  `postgres:16-alpine`

## Metodología / comando ejecutado

```bash
cd backend-springboot
./mvnw -B clean verify
```

Copia del reporte (nueva carpeta con fecha, no sobrescribe las históricas):

```bash
mkdir -p docs/mediciones/jacoco/2026-08-27-jacoco-cierre-final
cp backend-springboot/target/site/jacoco/jacoco.xml docs/mediciones/jacoco/2026-08-27-jacoco-cierre-final/
cp backend-springboot/target/site/jacoco/jacoco.csv docs/mediciones/jacoco/2026-08-27-jacoco-cierre-final/
cp -r backend-springboot/target/site/jacoco/. docs/mediciones/jacoco/2026-08-27-jacoco-cierre-final/html/
```

## Resultados crudos

Build: `BUILD SUCCESS` (sin flags de ignore),
`Tests run: 285, Failures: 0, Errors: 0, Skipped: 2` -- **BUILD SUCCESS limpio**.
`jacoco:report` analizó **80 clases**.

### Totales agregados (todos los contadores, las tres mediciones)

| Contador | `825ad34` (re-verificado, correcto) | **HEAD `89eb6ab` (vigente)** |
|---|---|---|
| **LINE** | 55,25 % (1541/2789) -- missed 1248 | **56,01 % (1613/2880) -- missed 1267** |
| **INSTRUCTION** | missed 6560 / covered 6333 | **missed 6670 / covered 6561** |
| **BRANCH** | missed 459 / covered 239 (34,24 %) | **missed 467 / covered 247 (34,59 %)** |
| **COMPLEXITY** | missed 512 / covered 427 (45,47 %) | **missed 518 / covered 436 (45,70 %)** |
| **METHOD** | missed 243 / covered 346 (58,74 %) | **missed 245 / covered 351 (58,89 %)** |
| **CLASS** | missed 6 / covered 74 (92,50 %) | **missed 6 / covered 74 (92,50 %)** |

**La cifra de 61,50 % citada anteriormente para `825ad34` queda retractada
por lo expuesto arriba: no corresponde a ninguna build real de ese
commit.** La cobertura de líneas sigue **por debajo del umbral de 70 %**
exigido para la Entrega Final, en ambos puntos de referencia
(55,25 % y 56,01 %) -- la misma conclusión honesta de siempre, ahora
sobre una cifra corregida y verificada tres veces de forma independiente
(dos reconstrucciones de `825ad34` + esta corrida fresca sobre HEAD).

### Desglose por paquete (HEAD `89eb6ab`, lines / branches)

| Paquete | Lines | Branches |
|---|---|---|
| `scheduling` | 57/58 (98,28 %) | 5/6 (83,33 %) |
| `security` | 109/128 (85,16 %) | 22/30 (73,33 %) |
| `service` | 1158/1830 (63,28 %) | 209/508 (41,14 %) |
| `controller` | 194/312 (62,18 %) | 5/10 (50,00 %) |
| `exception` | 20/47 (42,55 %) | 2/14 (14,29 %) |
| `chatbot.tool` | 39/227 (17,18 %) | 0/50 (0,00 %) |
| `chatbot` | 21/154 (13,64 %) | 2/40 (5,00 %) |
| `integration` | 15/124 (12,10 %) | 2/56 (3,57 %) |
| `repository`, `repository.projection` | sin líneas instrumentadas (solo interfaces) | -- |

Reporte completo navegable:
`docs/mediciones/jacoco/2026-08-27-jacoco-cierre-final/html/index.html`.
XML crudo: `docs/mediciones/jacoco/2026-08-27-jacoco-cierre-final/jacoco.xml`.

## Análisis breve

1. **La cifra de 61,50 % se retracta como error de origen.** Verificada
   dos veces de forma independiente reconstruyendo `825ad34` en un
   worktree aislado, nunca reprodujo el número 61,50 % / 1481 / 2408.
   El artefacto ya versionado (55,25 %, 1541/2789) es y siempre fue el
   correcto para ese commit.

2. **La cobertura sigue por debajo del umbral de 70 %.** Tanto en el
   punto de referencia histórico correcto (55,25 %) como en el cierre
   vigente (56,01 %). El crecimiento real de funcionalidad (módulo de
   auditoría) entre ambos puntos es proporcional entre líneas cubiertas
   y líneas totales, por lo que el porcentaje se mueve muy poco
   (+0,76 pp) pese a sumar 91 líneas analizables nuevas.

3. **No se agregan tests de relleno.** El aumento de cobertura entre
   `825ad34` y HEAD proviene de tests reales del módulo de auditoría ya
   escritos junto con su funcionalidad, no de tests añadidos solo para
   mover este número.

4. **Todos los tests pasan.** La suite completa ejecuta 285 tests con
   0 fallos, 0 errores, 2 skipped -- **BUILD SUCCESS limpio**, igual que
   en la medición de `825ad34`.

## Estado: GAP CONOCIDO -- 56,01 % líneas (cierre vigente, HEAD `89eb6ab`), **por debajo del objetivo de 70 % de la Entrega Final**. La cifra de 55,25 % (`825ad34`) queda como punto de referencia histórico correcto; la cifra de 61,50 % citada anteriormente para ese mismo commit queda retractada por error de origen, no por pérdida de artefacto.
