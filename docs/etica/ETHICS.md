# Ética de datos — SGB-SaaS

Declaraciones exigidas por el Bloque F de la guía de la Tercera Entrega
sobre procedencia de datos, tratamiento de datos personales,
consentimiento informado y ausencia de PII en el repositorio.

## (i) Fuentes de datos y su licencia

### Corrección de premisa (importante para la trazabilidad de este documento)

Antes de redactar esta sección se verificó el contenido real de
`db/seed.sql` (no se asumió). El resultado corrige una premisa que se
tenía por cierta al iniciar esta tarea: **los libros de ejemplo NO son
ficticios**. Los 5 registros de la tabla `libros` en el seed son libros
reales, publicados, con ISBN real:

| ISBN | Título | Autor | Editorial |
|---|---|---|---|
| 9780132350884 | Clean Code | Robert C. Martin | Prentice Hall |
| 9780134757599 | Refactoring: Improving the Design of Existing Code | Martin Fowler | Addison-Wesley |
| 9780307474728 | Cien años de soledad | Gabriel García Márquez | Debolsillo |
| 9788499926223 | Sapiens: De animales a dioses | Yuval Noah Harari | Debate |
| 9788401352836 | La casa de los espíritus | Isabel Allende | Plaza & Janés |

Declarar esto como "datos sintéticos/ficticios" habría sido inexacto y,
en un documento de ética de datos, contraproducente — el propio objetivo
de esta sección es no dejar dudas de procedencia, así que se documenta
lo que realmente hay, no una versión simplificada que no resistiría una
auditoría.

### Qué es real y qué es original en estos registros

- **Metadatos bibliográficos** (ISBN, título, autor, editorial, año de
  publicación): son **hechos públicos verificables** sobre libros
  publicados — un ISBN, un título y el nombre de una editorial no son
  contenido de autor protegido por derechos de propiedad intelectual en
  sí mismos (son datos de catalogación, del mismo tipo que aparecería en
  el catálogo público de cualquier biblioteca física). Se ingresaron
  manualmente por el equipo como ejemplos de dominio reconocible para
  facilitar la revisión funcional del catálogo, no fueron generados por
  una IA ni inventados.
- **El campo `resumen`** de cada libro es una descripción original de una
  sola línea, redactada por el equipo para este proyecto — no es una
  reseña ni sinopsis copiada de una contraportada, editorial, o servicio
  de terceros (ver el texto exacto en `db/seed.sql`, líneas 123–157).
- **El contenido íntegro de las obras** (el texto completo de los libros)
  **no está incluido en este repositorio bajo ninguna forma** — el
  sistema modela metadatos de catálogo y disponibilidad de stock físico,
  no almacena ni distribuye el contenido de ninguna obra.
- **Ninguna integración con un catálogo bibliográfico de terceros con
  licencia** (ej. Google Books API, WorldCat, ISBNdb) existe en el código
  del backend ni del frontend — verificado por búsqueda en el código
  fuente durante la auditoría de C4 (ver
  `docs/arquitectura/workspace.dsl`, sección "Diferencias vs. Entrega
  1A"). Estos metadatos se transcribieron manualmente, no se extrajeron
  mediante scraping ni consumo de una API licenciada de terceros.

### El usuario administrador de ejemplo

`db/seed.sql` también inserta un usuario `admin@sgb-saas.local` con un
hash BCrypt de una contraseña de desarrollo documentada en el propio
`README.md` ("Credenciales de desarrollo"). No corresponde a ninguna
persona real — es una cuenta de servicio de ejemplo para poder operar el
sistema recién levantado. Ver también la sección (ii) para la política
general de datos personales.

## (ii) Tratamiento de datos personales

El sistema, en su uso previsto (una biblioteca institucional real),
almacenará datos de personas reales: `nombre`, `apellido`, `correo` de
lectores y personal (tabla `usuarios`, `db/schema.sql`).

- **Contraseñas**: nunca se almacena la contraseña en texto plano. El
  campo `password_hash` guarda únicamente el hash BCrypt (costo 12,
  `BCryptPasswordEncoder(12)` en `SecurityConfig`), un algoritmo de
  hashing de una sola vía diseñado específicamente para credenciales
  (salt integrado, costo computacional ajustable).
- **Verificación explícita de no-exposición vía API** (parte de esta
  misma tarea, no asumida): se auditó el código fuente completo del
  backend buscando cualquier ruta por la que `password_hash` pudiera
  filtrarse en una respuesta HTTP.
  - El campo se llama `passwordHash` en la entidad `Usuario`
    (`backend-springboot/src/main/java/com/uteq/backend/entity/Usuario.java`)
    y tiene `@JsonIgnore` aplicado directamente sobre él — una capa de
    protección incluso ante una serialización accidental futura de la
    entidad completa.
  - El único DTO de respuesta vinculado a `Usuario`
    (`UsuarioResponseDTO`, usado por `POST /api/auth/registro`) es un
    `record` que expone únicamente `id`, `nombre`, `correo`, `roles` —
    no tiene el campo del hash en su definición, no es cuestión de una
    anotación que lo oculte, estructuralmente no existe en ese tipo.
  - Ningún `@RestController` del proyecto (`AuthController`,
    `LibroController`, `TestController` — no existe un
    `UsuarioController`) retorna la entidad `Usuario` directamente; todos
    devuelven DTOs específicos o tipos primitivos.
  - **Veredicto: NO se encontró ningún endpoint ni DTO que exponga
    `password_hash`.** Ver el mensaje de cierre de esta tarea para el
    detalle completo reportado al equipo.
- **Datos de prueba en desarrollo**: el único usuario en `db/seed.sql` es
  la cuenta `admin@sgb-saas.local` descrita en (i) — no es una persona
  real, es una cuenta de servicio de ejemplo. Ningún dato de prueba usado
  en desarrollo hasta la fecha corresponde a una persona real.
- **Minimización de datos**: el esquema no recoge campos personales más
  allá de los estrictamente necesarios para identificar al usuario y
  operar el sistema de préstamos (nombre, apellido, correo) — no se
  almacenan datos sensibles (ej. datos de salud, biométricos) que no
  tienen relación con la operación de una biblioteca.

## (iii) Consentimiento informado para pruebas de usabilidad (SUS, Bloque C.3)

Las pruebas de System Usability Scale (SUS) del Bloque C.3 **se ejecutaron
con N=15 participantes** (códigos `P01` a `P15`) entre el 2026-08-28 y
2026-08-30. Cada participante firmó la plantilla de consentimiento
informado antes de iniciar la sesión de onboarding.

**Protección de datos personales (PII):** Por cumplimiento estricto de
normativas de protección de datos personales, los 15 consentimientos
firmados (física o digitalmente) **no se conservan en este repositorio
público**. Fueron eliminados del rastreo de Git mediante `git rm` y no
existen en ninguna rama accesible públicamente. La única copia de estos
documentos se almacena en un medio privado con acceso institucional
restringido:

- **Almacenamiento privado (Google Drive institucional):**
  <https://drive.google.com/drive/folders/1uoGc6gQ7AJuVEayv09OSm84bS0WGB15y?usp=sharing>
- **Permisos:** Acceso restringido exclusivamente a cuentas con dominio
  institucional `@uteq.edu.ec` (Docente director y Tribunal evaluador).
- **Justificación de anonimización:** El repositorio del proyecto es
  público (GitHub) y contiene código fuente, documentación y evidencia
  técnica. Almacenar consentimientos firmados con nombres reales en un
  repositorio público expondría PII (nombre completo, firma) en
  violación del principio de minimización de datos y del consentimiento
  informado otorgado por los participantes, quienes fueron informados de
  que sus datos identificables no se publicarían.

La plantilla en blanco (sin datos personales) se mantiene versionada en
[`docs/etica/consentimientos/plantilla.md`](consentimientos/plantilla.md)
como referencia pública del protocolo utilizado.

## (iv) Ausencia de datos identificables en el repositorio público

- **`docs/mediciones/`** solo contendrá evidencia técnica: timings
  (ej. `TIME:0.169557` en `docs/mediciones/sec/2026-07-21-cache-libros-ttl.md`),
  hashes (ej. `TTL`/keys de Redis), códigos de estado HTTP, y — para las
  futuras corridas de SUS del Bloque C.3 — **puntuaciones agregadas y
  respuestas identificadas solo por código de participante** (`P01`,
  `P02`, ... ver plantilla de TAREA 2), nunca nombre, correo ni ningún
  otro dato que permita identificar a una persona real. Los 2 archivos
  existentes hoy en `docs/mediciones/sec/` ya cumplen este criterio —
  fueron generados contra el usuario de desarrollo `admin@sgb-saas.local`
  (no una persona real), como se documenta en cada uno de esos archivos.
- **Los consentimientos firmados de participantes reales de SUS nunca se
  committean a este repositorio.** `docs/etica/consentimientos/` contiene
  únicamente la plantilla en blanco (versionada, pública); los
  formularios firmados (físicos o digitales) se archivan fuera del
  control de versiones, en un medio que el equipo gestione de forma
  separada — ver la nota de almacenamiento al final de la plantilla.
- Este mismo criterio aplica a cualquier evidencia futura del Bloque C
  (k6, Lighthouse, JaCoCo): son mediciones técnicas sobre el sistema, no
  sobre personas, y no está previsto que requieran ningún dato personal.

## Referencias

- `db/seed.sql` (datos de ejemplo, líneas 95–162)
- `backend-springboot/src/main/java/com/uteq/backend/entity/Usuario.java`
- `backend-springboot/src/main/java/com/uteq/backend/dto/UsuarioResponseDTO.java`
- `backend-springboot/src/main/java/com/uteq/backend/config/SecurityConfig.java`
- [`docs/etica/consentimientos/plantilla.md`](consentimientos/plantilla.md)
- `docs/mediciones/README.md`, `docs/mediciones/sec/`
