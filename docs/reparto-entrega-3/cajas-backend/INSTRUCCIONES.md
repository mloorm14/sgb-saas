# Instrucciones — Módulo de Préstamos/Devoluciones/Reservaciones/Multas (Backend)

Para: **Irvin Cajas**. Autocontenido — todo lo que necesitás saber está
acá o referenciado con ruta exacta. Verificado contra el código real del
repositorio antes de escribirse (no son supuestos).

## 0. Punto de partida — qué YA existe (no lo reimplementes)

### Entidades JPA (ya escritas, compilan, mapean 1:1 sin joins)

- `backend-springboot/src/main/java/com/uteq/backend/entity/Prestamo.java`
  — columnas planas (`usuarioId`, `libroId`, `bibliotecarioId`,
  `reservacionId` nullable, `fechaPrestamo`, `fechaDevolucionEstimada`,
  `fechaDevolucionReal` nullable, `renovacionesRealizadas`,
  `estadoPrestamoId`). Ya trae `@NamedStoredProcedureQuery` para
  `sp_registrar_devolucion`.
- `.../entity/Reservacion.java` — `usuarioId`, `libroId`,
  `estadoReservacionId`, `fechaReserva`, `fechaLimiteRetiro`.
- `.../entity/Multa.java` — `prestamoId`, `monto`, `estadoMultaId`,
  `fechaGenerada`, `fechaPagada` nullable, `observaciones`. Ya trae
  `@NamedStoredProcedureQuery` para `sp_pagar_multa` y `sp_anular_multa`.

**Ninguna de las 3 tiene relación `@ManyToOne` a sus catálogos de
estado** (a diferencia de `Libro.estado`) — es deliberado (ver Javadoc de
`Prestamo`), para que los repositorios CRUD no necesiten joins. Vas a
necesitar los **IDs** de estado, no las entidades.

### Catálogos de estado — ⚠️ gap real, tenés que crearlos

`estados_prestamo`, `estados_multa`, `estados_reservacion` existen en
`db/schema.sql` (mismo patrón id+nombre que `estados_libro`/
`estados_usuario`), pero **no tienen entidad JPA ni repositorio
todavía**. Para los flujos que van 100% por stored procedure
(crear préstamo, devolución, pago, anulación, expiración) no los
necesitás — el SP resuelve el estado internamente. Pero
**`ReservacionService.crear()` es CRUD puro (no hay SP para crear
reservación)**, así que ahí sí necesitás resolver el id de un nombre de
estado. Creá `EstadoReservacion.java` + `EstadoReservacionRepository.java`
copiando exactamente el patrón de
`backend-springboot/src/main/java/com/uteq/backend/entity/EstadoLibro.java`
y `.../repository/EstadoLibroRepository.java` (id Integer, nombre
String, `findByNombre`). No hace falta `EstadoPrestamo`/`EstadoMulta` si
no vas a leer/escribir esos catálogos fuera de los SPs.

Valores ya sembrados (`db/seed.sql`), para que sepas qué nombres existen:

| Catálogo | Valores |
|---|---|
| `estados_prestamo` | `ACTIVO`, `RENOVADO`, `DEVUELTO`, `VENCIDO` |
| `estados_multa` | `PENDIENTE`, `PAGADA`, `ANULADA` |
| `estados_reservacion` | `PENDIENTE`, `LISTA_PARA_RETIRO`, `RETIRADA`, `EXPIRADA`, `CANCELADA` |

### Repositorios (ya existen, compilan)

- **CRUD elemental** (una sola tabla, sin joins):
  `PrestamoRepository` (`findByUsuarioId(Pageable)`,
  `findByEstadoPrestamoId(Pageable)`), `ReservacionRepository` (vacío,
  solo `JpaRepository`), `MultaRepository` (vacío, solo `JpaRepository`).
  Agregales métodos derivados si los necesitás (ej.
  `MultaRepository.findByPrestamoId(...)`) — no hay nada que te lo impida.
- **"Solo procedimientos"** (no extienden `JpaRepository`, ver Javadoc de
  cada uno para el porqué de cada mecanismo):
  - `PrestamoProcedureRepository`: `spCrearPrestamo(usuarioId, libroId,
    bibliotecarioId, diasPrestamo)` → `Long` (id del préstamo);
    `spRegistrarDevolucion(prestamoId)` → `Map<String,Object>` con keys
    `o_prestamo_id`, `o_hubo_multa`, `o_monto_multa`;
    `fnListarPrestamosActivosPorUsuario(usuarioId)` →
    `List<PrestamoActivoProjection>`;
    `fnReporteLibrosMasPrestados(limite, desde, hasta)` →
    `List<LibroMasPrestadoProjection>` (`desde`/`hasta` son
    `OffsetDateTime`, aceptan `null`).
  - `MultaProcedureRepository`: `spPagarMulta(multaId)` →
    `Map<String,Object>` (keys `o_multa_id`, `o_usuario_desbloqueado`);
    `spAnularMulta(multaId, motivo, rolEjecutor)` → mismo shape de Map.
  - `ReservacionProcedureRepository`: `spExpirarReservacionesVencidas()`
    → `Integer` (filas afectadas); hay una sobrecarga con parámetro
    `OffsetDateTime ahora` para tests.

**Nunca reimplementes en Java la lógica que ya vive en estos SPs** (ya
resuelven validación cruzada, atomicidad y generación de multas — ver
`docs/basedatos/CATALOGO-SP.md` para el detalle de cada uno). Tu trabajo
es invocarlos desde el service, no duplicar su lógica.

### Lo que NO existe (tu trabajo empieza acá)

Ningún `Service` ni `Controller` para este dominio. `PrestamoService`,
`ReservacionService`, `MultaService`, `PrestamoController`,
`ReservacionController`, `MultaController` — los 6 son nuevos.

## 1. Patrón arquitectónico a seguir (mismo que `LibroController`/`LibroService`)

Referencia exacta: `backend-springboot/src/main/java/com/uteq/backend/controller/LibroController.java`
y `.../service/LibroService.java`. Puntos concretos:

- Constructor injection explícito (no `@Autowired` en campo) — o
  `@RequiredArgsConstructor` de Lombok con campos `private final`, ambos
  patrones ya conviven en el repo (`LibroController` usa constructor
  manual, `AuthController` usa `@RequiredArgsConstructor`) — cualquiera
  de los dos sirve.
- `@Transactional` (o `@Transactional(readOnly = true)` para lecturas)
  en cada método público del service.
- El service traduce entidad ↔ DTO con métodos privados `toDTO`/`fromDTO`
  — nunca expongas la entidad JPA directamente en una respuesta HTTP.
- El controller solo orquesta: valida `@Valid`, llama al service, arma
  el `ResponseEntity` con el código correcto (`201` al crear, `204` al
  eliminar/acciones sin cuerpo de respuesta, `200` para el resto).
- `@PreAuthorize` a nivel de método en el controller, con los mismos
  nombres de rol ya usados (`hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE')`
  — sin prefijo `ROLE_` en el string, `UserDetailsServiceImpl` ya lo
  antepone).

### Cómo obtener el usuario autenticado (esto SÍ es nuevo — no hay ejemplo previo)

Ningún controller existente hoy necesita saber "quién soy" (Libros no
filtra por usuario, Auth usa el DTO del body). Para "LECTOR ve lo
propio" vas a necesitar el `usuario_id` del que hizo la request. El
principal que deja `JwtAuthFilter` en el `SecurityContext` es un
`org.springframework.security.core.userdetails.User` de Spring Security
estándar — **su `getUsername()` es el correo, no el id numérico**. No
existe un `UserDetails` custom con el id ya adentro. Patrón a usar (no
toques `JwtAuthFilter`/`UserDetailsServiceImpl`, es infraestructura
compartida — resolvé el id en tu propio service):

```java
// En el controller, recibí Authentication como parámetro de Spring MVC:
public ResponseEntity<...> listarPropios(Authentication authentication, ...) {
    return ResponseEntity.ok(prestamoService.listarPorUsuarioAutenticado(
            authentication.getName(), pageable));
}

// En el service, resolvé correo -> Usuario -> id con el repositorio que ya existe:
@Transactional(readOnly = true)
public Page<PrestamoResponseDTO> listarPorUsuarioAutenticado(String correo, Pageable pageable) {
    Usuario usuario = usuarioRepository.findByCorreo(correo)
            .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + correo));
    return prestamoRepository.findByUsuarioId(usuario.getId(), pageable).map(this::toDTO);
}
```

`UsuarioRepository` ya existe (`findByCorreo`) — es el mismo que usa
`UserDetailsServiceImpl`, inyectalo en tu service igual que cualquier
otro repositorio.

Para los endpoints donde BIBLIOTECARIO/GERENTE pueden consultar
**cualquier** usuario por id (no solo el propio), validá en el service:
si el rol del `Authentication` es `LECTOR`, el `usuarioId` pedido debe
coincidir con el propio (comparar contra el id resuelto de
`authentication.getName()`); si es `BIBLIOTECARIO`/`GERENTE`, no hay
restricción. Lanzá `AuthorizationDeniedException` (ya manejada por
`GlobalExceptionHandler` → 403) si un LECTOR pide el id de otro usuario.

## 2. Servicios

### `PrestamoService`

- `crear(PrestamoRequestDTO)` → invoca `spCrearPrestamo(...)`, devuelve
  el préstamo recién creado (buscalo por el id que retorna el SP y
  convertilo a DTO, o construí el DTO con los datos de entrada + el id
  retornado, tu elección).
- `registrarDevolucion(Long prestamoId)` → invoca `spRegistrarDevolucion`,
  devuelve un DTO con `prestamoId`, `hubaMulta` (boolean),
  `montoMulta` (nullable) leídos del `Map<String,Object>` resultante.
- `listarPorUsuario(Long usuarioId, Authentication auth, Pageable)` →
  aplica la regla de "propio vs cualquiera" descrita arriba, usa
  `PrestamoRepository.findByUsuarioId`.
- `listarActivosPorUsuario(Long usuarioId, Authentication auth)` → misma
  regla de acceso, usa `fnListarPrestamosActivosPorUsuario` (proyección,
  no `Page` — es una función que ya retorna solo los activos, no pagines
  algo que no lo necesita).
- `reporteLibrosMasPrestados(Integer limite, OffsetDateTime desde,
  OffsetDateTime hasta)` → usa `fnReporteLibrosMasPrestados` directo, sin
  reglas de acceso por usuario (es un reporte agregado, no datos de una
  persona).

### `ReservacionService`

- `crear(ReservacionRequestDTO, Authentication auth)` → **CRUD puro**
  (no hay SP): resolvé `estadoReservacionId` inicial con
  `EstadoReservacionRepository.findByNombre("PENDIENTE")` (ver sección 0),
  seteá `fechaReserva = OffsetDateTime.now()`, calculá
  `fechaLimiteRetiro` según la regla de negocio que definas (ej. +3 días
  — no hay un valor ya definido en `configuracion_sistema` para esto,
  revisá `db/seed.sql` sección `configuracion_sistema` antes de
  inventar un número; si no existe la clave que necesitás, es una
  decisión tuya documentarla en el ADR o pedirle confirmación a Marlon
  antes de hardcodear un valor de negocio). Si el `Authentication` es
  LECTOR, el `usuarioId` de la reservación debe ser el propio (mismo
  patrón de resolución correo→id de la sección 1); BIBLIOTECARIO/GERENTE
  pueden reservar en nombre de otro usuario si el DTO lo permite.
- `listarPorUsuario(...)` → mismo patrón de "propio vs cualquiera".
- **No implementes cancelación/expiración manual acá** — la expiración
  masiva vive en el job `@Scheduled` (sección 4), no en un endpoint.

### `MultaService`

- `listarPorUsuario(...)` → mismo patrón de acceso.
- `pagar(Long multaId)` → invoca `spPagarMulta`, devuelve DTO con
  `multaId`, `usuarioDesbloqueado` (boolean) desde el Map.
- `anular(Long multaId, String motivo, Authentication auth)` →
  **⚠️ importante de seguridad**: `p_rol_ejecutor` que le pasás al SP
  **tiene que resolverse del rol REAL del usuario autenticado**
  (`auth.getAuthorities()`, tomá el rol `GERENTE`/`ADMIN` de ahí — el
  primero que matchee, ya que un usuario puede tener más de un rol),
  **nunca de un campo que venga en el body del request**. El SP mismo
  valida `p_rol_ejecutor` (rechaza con `LB422` si no es `GERENTE`/`ADMIN`,
  ver `db/procs/sp_anular_multa.sql` líneas 56-58) como defensa en
  profundidad, pero eso no reemplaza el `@PreAuthorize` del controller
  — si aceptaras el rol desde el body, un `BIBLIOTECARIO` autenticado
  podría enviar `"rolEjecutor": "GERENTE"` en el JSON y colar la
  anulación. No lo aceptes como campo del DTO de request.
  - Nota ya documentada en el propio SP (línea 34-37 de
    `sp_anular_multa.sql`): `bitacora_auditoria.usuario_id` queda en
    `NULL` porque la función no recibe el id de quien ejecuta, solo su
    rol — es una limitación conocida del SP, no la resuelvas por tu
    cuenta cambiando la firma del procedimiento sin avisar (ver sección
    5 sobre no cambiar mecanismos de invocación sin aviso).

## 3. Traducción de SQLSTATE (LB404/LB409/LB422) — ⚠️ gap real en `GlobalExceptionHandler`

Revisá `backend-springboot/src/main/java/com/uteq/backend/exception/GlobalExceptionHandler.java`
tal como está hoy: maneja `EntityNotFoundException`, `IllegalArgumentException`,
excepciones de Spring Security, y un catch-all genérico a 500. **No hay
ningún handler que traduzca los SQLSTATE personalizados
(`LB404`/`LB409`/`LB422`, documentados en
`docs/basedatos/CATALOGO-SP.md`) que tus 5 procedimientos con efectos
secundarios lanzan vía `RAISE EXCEPTION ... USING ERRCODE = 'LBxxx'`.**
Hoy, sin tu cambio, cualquier `RAISE EXCEPTION` de un SP cae al
catch-all genérico → 500, ocultando el error real de negocio (ej. "el
préstamo ya fue devuelto" se vería como un 500 genérico, no un 409).

Cuando Hibernate/el driver JDBC de Postgres propaga una excepción con un
SQLSTATE que no reconoce (los `LBxxx` son custom, no están en el mapeo
estándar de Spring), termina envuelta en algo como
`org.springframework.dao.InvalidDataAccessResourceUsageException` o
`org.springframework.jdbc.UncategorizedSQLException`, con la causa raíz
(`java.sql.SQLException`/`org.postgresql.util.PSQLException`) accesible
vía `getCause()` y su `getSQLState()`.

Necesitás agregar un handler nuevo. Patrón sugerido (verificalo en vivo
contra los 3 casos LB404/LB409/LB422 antes de darlo por bueno — el tipo
exacto de excepción envuelta puede variar según cuál invocación
(`@Procedure` vs `@Query nativeQuery`) la dispare):

```java
@ExceptionHandler({
        org.springframework.dao.InvalidDataAccessResourceUsageException.class,
        org.springframework.jdbc.UncategorizedSQLException.class,
        org.springframework.dao.DataAccessException.class
})
public ProblemDetail handleStoredProcedureError(Exception ex) {
    Throwable causa = ex;
    while (causa != null && !(causa instanceof java.sql.SQLException)) {
        causa = causa.getCause();
    }
    if (causa instanceof java.sql.SQLException sqlEx) {
        String sqlState = sqlEx.getSQLState();
        HttpStatus status = switch (sqlState) {
            case "LB404" -> HttpStatus.NOT_FOUND;
            case "LB409" -> HttpStatus.CONFLICT;
            case "LB422" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> null;
        };
        if (status != null) {
            return ProblemDetail.forStatusAndDetail(status, sqlEx.getMessage());
        }
    }
    log.error("Error no controlado en procedimiento almacenado", ex);
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
}
```

Verificá con `curl` en vivo (mismo estilo que
`docs/mediciones/sec/2026-07-21-cookie-refresh-token.md`) que los 3
SQLSTATE realmente producen 404/409/422 y no un 500 — documentá esa
evidencia en `docs/mediciones/` siguiendo `docs/mediciones/TEMPLATE.md`
si querés dejar constancia (no es obligatorio para tu entrega, pero es
el patrón que ya usa el resto del proyecto para verificaciones de este
tipo).

## 4. Controllers REST

Rutas base: `/api/v1/prestamos`, `/api/v1/reservaciones`, `/api/v1/multas`.

| Método | Ruta | Rol | Acción |
|---|---|---|---|
| `POST` | `/api/v1/prestamos` | `BIBLIOTECARIO`, `GERENTE` | Crear préstamo (`spCrearPrestamo`) |
| `POST` | `/api/v1/prestamos/{id}/devolucion` | `BIBLIOTECARIO`, `GERENTE` | Registrar devolución (`spRegistrarDevolucion`) |
| `GET` | `/api/v1/prestamos/usuario/{usuarioId}` | `LECTOR` (solo propio), `BIBLIOTECARIO`, `GERENTE` | Listar préstamos paginado |
| `GET` | `/api/v1/prestamos/usuario/{usuarioId}/activos` | `LECTOR` (solo propio), `BIBLIOTECARIO`, `GERENTE` | `fnListarPrestamosActivosPorUsuario` |
| `GET` | `/api/v1/prestamos/reportes/libros-mas-prestados` | `BIBLIOTECARIO`, `GERENTE` | `fnReporteLibrosMasPrestados` (query params `limite`, `desde`, `hasta`, todos opcionales) |
| `POST` | `/api/v1/reservaciones` | `LECTOR`, `BIBLIOTECARIO`, `GERENTE` | Crear reservación (CRUD) |
| `GET` | `/api/v1/reservaciones/usuario/{usuarioId}` | `LECTOR` (solo propio), `BIBLIOTECARIO`, `GERENTE` | Listar reservaciones paginado |
| `GET` | `/api/v1/multas/usuario/{usuarioId}` | `LECTOR` (solo propio), `BIBLIOTECARIO`, `GERENTE` | Listar multas paginado |
| `POST` | `/api/v1/multas/{id}/pago` | `BIBLIOTECARIO`, `GERENTE` | Pagar multa (`spPagarMulta`) |
| `POST` | `/api/v1/multas/{id}/anulacion` | `GERENTE`, `ADMIN` | Anular multa, body `{"motivo": "..."}` (`spAnularMulta`) |

Todos los DTOs de request/response como `record` (ver
`LibroRequestDTO`/`LibroResponseDTO` como referencia exacta de estilo,
incluidas anotaciones de validación `jakarta.validation.constraints.*`
en el request). Todos los errores vía `ProblemDetail` — no agregues
`try/catch` en el controller, dejá que `GlobalExceptionHandler` los
traduzca (ya cubre `EntityNotFoundException`, `IllegalArgumentException`,
`AuthorizationDeniedException`, validación de `@Valid`, y con tu cambio
de la sección 3, los `LBxxx`).

## 5. Job `@Scheduled` para expirar reservaciones vencidas

Nuevo, en `service/` o un paquete `scheduling/` nuevo si preferís
separarlo (tu elección, no hay precedente en el repo todavía):

```java
@Component
@RequiredArgsConstructor
public class ReservacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservacionScheduler.class);

    private final ReservacionProcedureRepository reservacionProcedureRepository;

    // Cada 15 minutos -- ajustá el valor si el equipo decide otra
    // frecuencia; no hay un requisito de la guía que fije un número
    // exacto, documentá la razón del valor elegido en el commit.
    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void expirarReservacionesVencidas() {
        Integer filasActualizadas = reservacionProcedureRepository.spExpirarReservacionesVencidas();
        log.info("Job de expiración de reservaciones: {} filas actualizadas", filasActualizadas);
    }
}
```

Necesitás agregar `@EnableScheduling` en `BackendApplication.java` (no
está habilitado todavía — verificalo antes de asumir que el job corre
solo).

## 6. Tests

### Unitarios de servicio — corrección de patrón

**Aviso**: si alguien te dijo que el proyecto usa `@MockitoBean` para
estos tests, es impreciso — verificá `LibroServiceTest.java` antes de
asumirlo. El patrón real es JUnit 5 + Mockito puro, **sin contexto de
Spring**:

```java
@ExtendWith(MockitoExtension.class)
class PrestamoServiceTest {
    @Mock PrestamoRepository prestamoRepo;
    @Mock PrestamoProcedureRepository prestamoProcRepo;
    @Mock UsuarioRepository usuarioRepo;
    @InjectMocks PrestamoService prestamoService;

    @Test
    void crear_conDatosValidos_invocaProcedimiento() {
        given(prestamoProcRepo.spCrearPrestamo(1L, 2L, 3L, 7)).willReturn(99L);
        // ...
    }
}
```

`@MockitoBean` es una anotación de `spring-boot-test` para tests que SÍ
levantan contexto de Spring (`@SpringBootTest`/`@WebMvcTest`) — no es lo
que este proyecto usa para tests de servicio. Seguí el patrón real de
`LibroServiceTest`, no el que te hayan descrito de oídas.

Cubrí al menos: creación exitosa, devolución sin atraso, devolución con
atraso (`hubaMulta = true`), intento de devolución de un préstamo ya
devuelto (verificá que tu handler de la sección 3 lo traduzca a 409 —
podés simular esto con un mock que lance la excepción envuelta
correspondiente), acceso denegado cuando un LECTOR pide el id de otro
usuario.

### Integración real contra Postgres — los 3 procedimientos multi-OUT nunca verificados en runtime

`docs/basedatos/CATALOGO-SP.md` (sección "Pendiente") ya documenta esto
como una condición explícita para dar por cerrada tu integración:
`sp_registrar_devolucion`, `sp_pagar_multa`, `sp_anular_multa` usan
`@NamedStoredProcedureQuery` + `Map<String,Object>` para sus múltiples
parámetros OUT — **compilan pero nunca se ejecutaron contra una base de
datos real**. Es un área frágil conocida de Hibernate/pgjdbc.

**No hay infraestructura de test de integración en el proyecto
todavía** (verificado: `backend-springboot/pom.xml` no tiene
`testcontainers` ni ninguna dependencia equivalente; no hay
`@SpringBootTest` con perfil de integración en `src/test/`). Dos
caminos, elegí el que prefieras y documentá cuál usaste:

1. **Contra el stack Docker Compose ya levantado** (más simple, cero
   dependencias nuevas): `application.yml` por defecto ya apunta a
   `localhost:5432/sgb_db` — con `docker compose up -d postgres redis`
   corriendo, un `@SpringBootTest` normal (sin perfil especial) se
   conecta directo a esa base real. Es el mismo criterio que ya usa el
   equipo para las evidencias de `docs/mediciones/sec/` (verificación
   manual contra el stack real), solo que automatizado en un test
   JUnit.
2. **Testcontainers** (`org.testcontainers:postgresql`,
   `org.springframework.boot:spring-boot-testcontainers`): más portable
   a CI, pero es una dependencia nueva que tendrías que agregar a
   `pom.xml` — no asumas que ya está, no está.

Cualquiera de los dos: el test debe invocar los 3 procedimientos de
punta a punta contra una base real (no mocks) y verificar que el `Map`
resultante tiene las keys esperadas con los tipos correctos.

**Si alguno falla en runtime pese a compilar** (el escenario que
`CATALOGO-SP.md` ya advierte como posible): documentá el error exacto
(stack trace completo, no un resumen) en un archivo bajo
`docs/mediciones/` siguiendo `docs/mediciones/TEMPLATE.md`, y **avisá
antes de cambiar el mecanismo de invocación por tu cuenta** (ej. migrar
de `@NamedStoredProcedureQuery` a `@Query(nativeQuery = true)` con
`CALL`, u otra alternativa). No es tu culpa si falla — es exactamente el
riesgo ya documentado — pero el cambio de mecanismo afecta una decisión
ya registrada en `docs/adr/adr-013-acceso-datos-orm-sp.md`, así que
requiere alinear con el equipo antes de aplicarlo.

## 7. Verificación final antes de dar por cerrado

1. `./mvnw clean verify` en verde (desde `backend-springboot/`).
2. Prueba en vivo del flujo completo (mismo estilo `curl` que
   `docs/mediciones/sec/`, no hace falta que sea Postman):
   crear préstamo → verificar `libros.stock_disponible` decrementado →
   registrar devolución con fecha simulada de atraso → verificar multa
   generada (`estado_multa_id` = PENDIENTE) → verificar usuario
   bloqueado (`estados_usuario` = BLOQUEADO_POR_MULTA, intentar login y
   confirmar 423 `LockedException` ya manejado por
   `GlobalExceptionHandler`) → pagar multa → verificar usuario
   desbloqueado (login exitoso de nuevo).

## 8. Git

- Rama: `feature/prestamos-backend`, desde `main` actualizado
  (`git pull origin main` antes de ramificar).
- Conventional Commits (`feat(backend): ...`, `fix(backend): ...`,
  `test(backend): ...`), igual que el resto del historial del proyecto.
- **PR hacia `main` cuando esté listo — nunca push directo a `main`**
  (solo Marlon tiene bypass del branch protection).

## 9. Tu parte de A.3 — `docs/requisitos/`

Esta carpeta **no existe todavía** en el repositorio (verificado). No
hay un ejemplo real de otro integrante para replicar — la plantilla de
abajo es la que tenés que seguir, ya redactada en el formato exacto que
exige la guía: historia de usuario en formato **Connextra** + criterios
de aceptación en **Gherkin**, y caso de uso en formato **Cockburn**.

Creá `docs/requisitos/historias-usuario.md` y
`docs/requisitos/casos-de-uso.md` (o un archivo por historia/caso si
preferís más granularidad — tu elección, mientras el formato interno sea
este). Repetí esta misma estructura para cada flujo que construyas
(al menos: crear préstamo, registrar devolución, crear reservación,
pagar multa, anular multa — 5 historias + 5 casos de uso como mínimo).

### Plantilla — historia de usuario (Connextra + Gherkin)

```markdown
## HU-01: Registrar un préstamo

**Como** bibliotecario,
**quiero** registrar el préstamo de un libro a un lector,
**para** llevar control de qué ejemplares están fuera de la biblioteca
y cuándo deben devolverse.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Registro de préstamos

  Escenario: Préstamo exitoso con stock disponible
    Dado que el libro "Clean Code" tiene stock disponible mayor a 0
    Y el usuario "juan@uteq.edu.ec" tiene estado ACTIVO
    Cuando el bibliotecario registra un préstamo de "Clean Code" para "juan@uteq.edu.ec"
    Entonces el préstamo se crea con estado ACTIVO
    Y el stock disponible del libro se decrementa en 1

  Escenario: Intento de préstamo sin stock disponible
    Dado que el libro "Clean Code" tiene stock disponible igual a 0
    Cuando el bibliotecario intenta registrar un préstamo de "Clean Code"
    Entonces la operación se rechaza con un error 422
    Y el mensaje indica que no hay stock disponible

  Escenario: Intento de préstamo para un usuario bloqueado
    Dado que el usuario "maria@uteq.edu.ec" tiene estado BLOQUEADO_POR_MULTA
    Cuando el bibliotecario intenta registrar un préstamo para "maria@uteq.edu.ec"
    Entonces la operación se rechaza con un error 422
    Y el mensaje indica que el usuario tiene multas pendientes
```
```

### Plantilla — caso de uso (formato Cockburn)

```markdown
## CU-01: Registrar préstamo

- **Actor principal**: Bibliotecario
- **Interesados y sus intereses**:
  - Bibliotecario: quiere registrar el préstamo rápido, sin pasos manuales.
  - Lector: quiere llevarse el libro solo si no tiene impedimentos (multas, stock).
  - Gerente: quiere que el stock del catálogo siempre sea confiable.
- **Precondiciones**: el bibliotecario tiene sesión iniciada con rol
  BIBLIOTECARIO o GERENTE; el libro y el usuario existen en el sistema.
- **Garantía de éxito (postcondición)**: existe un registro nuevo en
  `prestamos` con estado ACTIVO; `libros.stock_disponible` del libro
  prestado se decrementó en 1; ambas escrituras ocurrieron en la misma
  transacción atómica (`sp_crear_prestamo`).
- **Disparador**: el bibliotecario selecciona "Nuevo préstamo" en la
  interfaz e ingresa el usuario y el libro.

### Escenario principal (flujo básico)

1. El bibliotecario ingresa el correo del lector y el ISBN/título del libro.
2. El sistema valida que el usuario existe y su estado es ACTIVO.
3. El sistema valida que el libro existe y tiene stock disponible > 0.
4. El sistema registra el préstamo (`sp_crear_prestamo`), decrementando
   el stock del libro en la misma transacción.
5. El sistema confirma al bibliotecario el préstamo creado con su fecha
   de devolución estimada.

### Extensiones (flujos alternativos)

- **3a.** El libro no tiene stock disponible: el sistema rechaza la
  operación con error 422 ("sin stock disponible") y no crea ningún
  registro.
- **2a.** El usuario tiene estado BLOQUEADO_POR_MULTA: el sistema
  rechaza la operación con error 422 ("usuario con multas pendientes")
  y no crea ningún registro.
- **1a.** El usuario o el libro no existen: el sistema rechaza con error
  404.
```

Replicá esta misma estructura (historia Connextra+Gherkin, caso de uso
Cockburn) para el resto de tus flujos. No hace falta que sean
extensísimos — cubrí el escenario principal y 2-3 alternativas reales
por flujo (los `LBxxx` de cada SP en `docs/basedatos/CATALOGO-SP.md` ya
te dicen cuáles son las alternativas reales a documentar, no inventes
otras).
