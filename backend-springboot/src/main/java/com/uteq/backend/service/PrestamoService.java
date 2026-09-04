package com.uteq.backend.service;

import com.uteq.backend.dto.DevolucionResponseDTO;
import com.uteq.backend.dto.LibroMasPrestadoDetalladoResponseDTO;
import com.uteq.backend.dto.LibroMasPrestadoResponseDTO;
import com.uteq.backend.dto.PrestamoActivoResponseDTO;
import com.uteq.backend.dto.PrestamoRequestDTO;
import com.uteq.backend.dto.PrestamoResponseDTO;
import com.uteq.backend.dto.RenovacionResponseDTO;
import com.uteq.backend.dto.ReporteCategoriasDemandadasResponseDTO;
import com.uteq.backend.dto.ReporteInventarioResponseDTO;
import com.uteq.backend.dto.ReporteMorosidadResponseDTO;
import com.uteq.backend.dto.ReporteUsoPorPeriodoResponseDTO;
import com.uteq.backend.dto.ReporteVencidosResponseDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.EstadoPrestamo;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.entity.Reservacion;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.EstadoPrestamoRepository;
import com.uteq.backend.repository.EstadoReservacionRepository;
import com.uteq.backend.repository.PrestamoProcedureRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.repository.ReservacionRepository;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.repository.projection.LibroMasPrestadoDetalladoProjection;
import com.uteq.backend.repository.projection.LibroMasPrestadoProjection;
import com.uteq.backend.repository.projection.PrestamoActivoProjection;
import com.uteq.backend.repository.projection.ReporteCategoriasDemandadasProjection;
import com.uteq.backend.repository.projection.ReporteInventarioProjection;
import com.uteq.backend.repository.projection.ReporteMorosidadProjection;
import com.uteq.backend.repository.projection.ReporteUsoPorPeriodoProjection;
import com.uteq.backend.repository.projection.ReporteVencidosProjection;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class PrestamoService {

    private static final String TABLA_PRESTAMOS = "prestamos";
    private static final String PRESTAMO_NO_ENCONTRADO = "Préstamo no encontrado con id: ";
    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ROL_LECTOR = "LECTOR";

    // ── Constantes de renovar() ─────────────────────────────
    private static final String ESTADO_DEVUELTO = "DEVUELTO";
    private static final String ESTADO_RENOVADO = "RENOVADO";
    // "Vigente" para efectos de bloquear una renovación: cualquier reserva
    // que todavía pueda terminar en un retiro (no RETIRADA/EXPIRADA/
    // CANCELADA). No existe un estado literal "ACTIVA" en estados_reservacion
    // (ver db/seed.sql) -- son estos dos los que cuentan como "en curso".
    private static final List<String> ESTADOS_RESERVA_VIGENTE = List.of("PENDIENTE", "LISTA_PARA_RETIRO");
    private static final String ESTADO_RESERVA_RETIRADA = "RETIRADA";
    private static final String CLAVE_DIAS_PRESTAMO_DEFAULT = "dias_prestamo_default";
    private static final String CLAVE_MAX_RENOVACIONES_DEFAULT = "max_renovaciones_default";

    private final PrestamoRepository prestamoRepo;
    private final PrestamoProcedureRepository prestamoProcRepo;
    private final UsuarioRepository usuarioRepo;
    private final EstadoPrestamoRepository estadoPrestamoRepo;
    private final ReservacionRepository reservacionRepo;
    private final EstadoReservacionRepository estadoReservacionRepo;
    private final ConfiguracionSistemaService configuracionSistemaService;
    private final CredencialQrService credencialQrService;
    private final NotificacionService notificacionService;
    private final BitacoraAuditoriaRepository bitacoraAuditoriaRepo;

    public PrestamoService(PrestamoRepository prestamoRepo,
                           PrestamoProcedureRepository prestamoProcRepo,
                           UsuarioRepository usuarioRepo,
                           EstadoPrestamoRepository estadoPrestamoRepo,
                           ReservacionRepository reservacionRepo,
                           EstadoReservacionRepository estadoReservacionRepo,
                           ConfiguracionSistemaService configuracionSistemaService,
                           CredencialQrService credencialQrService,
                           NotificacionService notificacionService,
                           BitacoraAuditoriaRepository bitacoraAuditoriaRepo) {
        this.prestamoRepo = prestamoRepo;
        this.prestamoProcRepo = prestamoProcRepo;
        this.usuarioRepo = usuarioRepo;
        this.estadoPrestamoRepo = estadoPrestamoRepo;
        this.reservacionRepo = reservacionRepo;
        this.estadoReservacionRepo = estadoReservacionRepo;
        this.configuracionSistemaService = configuracionSistemaService;
        this.credencialQrService = credencialQrService;
        this.notificacionService = notificacionService;
        this.bitacoraAuditoriaRepo = bitacoraAuditoriaRepo;
    }

    @Transactional
    public PrestamoResponseDTO crear(PrestamoRequestDTO dto, Authentication authentication) {
        Long usuarioId = resolverUsuarioId(dto);
        Long bibliotecarioId = resolverIdPorCorreo(authentication.getName());

        validarLimitePrestamos(usuarioId);

        // Ventanilla: si el préstamo nace de una reserva, se valida ANTES de
        // tocar stock (falla rápido, sin efectos secundarios) y se vincula
        // DESPUÉS del SP -- sp_crear_prestamo no conoce reservaciones (no
        // acepta ese parámetro) y la conversión reserva->préstamo son dos
        // UPDATEs simples sobre filas ya cargadas, sin la atomicidad multi-
        // tabla que sí justifica un procedimiento.
        Reservacion reservaOrigen = validarReservaSiAplica(dto, usuarioId);
        Long prestamoId = prestamoProcRepo.spCrearPrestamo(
                usuarioId, dto.libroId(), bibliotecarioId, dto.diasPrestamo());
        Prestamo prestamo = prestamoRepo.findById(prestamoId)
                .orElseThrow(() -> new EntityNotFoundException(PRESTAMO_NO_ENCONTRADO + prestamoId));
        if (reservaOrigen != null) {
            prestamo.setReservacionId(reservaOrigen.getId());
            prestamoRepo.save(prestamo);
            reservaOrigen.setEstadoReservacionId(idEstadoReservacion(ESTADO_RESERVA_RETIRADA));
            reservacionRepo.save(reservaOrigen);
        }
        registrarAuditoria(bibliotecarioId, prestamoId, "Creación de préstamo " + prestamoId + " para usuario " + usuarioId);
        return toDTO(prestamo);
    }

    // Valida que la reservacionId del body sea una reserva VIGENTE del mismo
    // usuario y sobre el MISMO libro del préstamo. Devuelve la entidad para
    // reutilizarla en crear() (vincular + marcar RETIRADA), o null cuando el
    // préstamo es directo (sin reservacionId).
    private Reservacion validarReservaSiAplica(PrestamoRequestDTO dto, Long usuarioId) {
        if (dto.reservacionId() == null) {
            return null;
        }
        Reservacion reservacion = reservacionRepo.findById(dto.reservacionId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Reservación no encontrada: " + dto.reservacionId()));
        if (!usuarioId.equals(reservacion.getUsuarioId())) {
            throw new IllegalArgumentException(
                    "La reservación " + dto.reservacionId() + " no pertenece al usuario del préstamo.");
        }
        if (!dto.libroId().equals(reservacion.getLibroId())) {
            throw new IllegalArgumentException(
                    "El libro del préstamo no coincide con el de la reservación "
                            + dto.reservacionId() + ".");
        }
        List<Integer> idsVigentes = ESTADOS_RESERVA_VIGENTE.stream()
                .map(nombre -> estadoReservacionRepo.findByNombre(nombre)
                        .orElseThrow(() -> new IllegalStateException(
                                "Catálogo estados_reservacion sin fila '" + nombre + "'"))
                        .getId())
                .toList();
        if (!idsVigentes.contains(reservacion.getEstadoReservacionId())) {
            throw new IllegalStateException(
                    "La reservación " + dto.reservacionId()
                            + " ya no está vigente (pendiente o lista para retiro).");
        }
        return reservacion;
    }

    // Módulo 8 (credencial QR): resuelve el usuario del préstamo por
    // credencialQrToken si vino en el body, o usa usuarioId directo si no.
    // "tieneToken == tieneUsuarioId" cubre ambos casos inválidos con una
    // sola condición: los dos presentes (true == true) Y los dos ausentes
    // (false == false) son igual de inválidos -- debe venir EXACTAMENTE uno.
    private Long resolverUsuarioId(PrestamoRequestDTO dto) {
        boolean tieneToken = dto.credencialQrToken() != null;
        boolean tieneUsuarioId = dto.usuarioId() != null;
        if (tieneToken == tieneUsuarioId) {
            throw new IllegalArgumentException(
                    "Debe enviarse exactamente uno de: usuarioId o credencialQrToken.");
        }
        if (tieneToken) {
            return credencialQrService.resolverPorToken(dto.credencialQrToken()).getId();
        }
        return dto.usuarioId();
    }

    @Transactional
    public DevolucionResponseDTO registrarDevolucion(Long prestamoId) {
        Map<String, Object> resultado = prestamoProcRepo.spRegistrarDevolucion(prestamoId);
        Boolean huboMulta = (Boolean) resultado.get("o_hubo_multa");
        BigDecimal montoMulta = (BigDecimal) resultado.get("o_monto_multa");

        // Módulo 2: multas se crean dentro de sp_registrar_devolucion (no
        // en MultaService -- ver Javadoc de MultaService, que solo lista/
        // paga/anula multas ya existentes), así que este es el único punto
        // de la aplicación donde se sabe, recién creada, que hubo una.
        if (Boolean.TRUE.equals(huboMulta)) {
            Prestamo prestamo = prestamoRepo.findById(prestamoId)
                    .orElseThrow(() -> new EntityNotFoundException(PRESTAMO_NO_ENCONTRADO + prestamoId));
            notificacionService.notificarMulta(prestamo.getUsuarioId(), prestamoId, montoMulta);
        }

        registrarAuditoria(null, prestamoId, "Devolución registrada del préstamo " + prestamoId);

        return new DevolucionResponseDTO(
                (Long) resultado.get("o_prestamo_id"), huboMulta, montoMulta);
    }

    // ── POST /{id}/renovacion ────────────────────────────────
    // Validaciones en Java (no un stored procedure nuevo, a diferencia de
    // crear()/registrarDevolucion()): a esta altura del proyecto ya existe
    // ConfiguracionSistemaService (dias_prestamo_default,
    // max_renovaciones_default) y las 4 reglas de negocio son consultas y un
    // UPDATE simple sobre una sola fila -- no requiere la atomicidad
    // multi-tabla que sí justifica un SP como sp_registrar_devolucion
    // (préstamo + multa + posible desbloqueo de usuario en una transacción).
    //
    // Orden de validación (cada una lanza una excepción distinta para que
    // el cliente pueda distinguir el motivo del rechazo):
    //   1. Préstamo no existe -> 404 (EntityNotFoundException)
    //   2. Autorización: LECTOR solo sobre su propio préstamo -> 403
    //   3. Ya devuelto -> 400 (no es "vencido" ni las otras 3 reglas
    //      explícitas del alcance original, pero renovar algo ya cerrado no
    //      tiene sentido de negocio y se rechaza igual)
    //   4. Vencido (fecha_devolucion_estimada ya pasó) -> 409
    //   5. Límite de renovaciones alcanzado -> 409
    //   6. Reserva vigente de OTRO usuario sobre el mismo libro -> 409
    @Transactional
    public RenovacionResponseDTO renovar(Long prestamoId, Authentication authentication) {
        Prestamo prestamo = prestamoRepo.findById(prestamoId)
                .orElseThrow(() -> new EntityNotFoundException(PRESTAMO_NO_ENCONTRADO + prestamoId));

        validarAccesoUsuario(prestamo.getUsuarioId(), authentication);

        if (ESTADO_DEVUELTO.equals(nombreEstadoPrestamo(prestamo.getEstadoPrestamoId()))) {
            throw new IllegalArgumentException(
                    "El préstamo " + prestamoId + " ya fue devuelto, no se puede renovar.");
        }

        if (prestamo.getFechaDevolucionEstimada().isBefore(OffsetDateTime.now())) {
            throw new PrestamoVencidoException(
                    "El préstamo " + prestamoId + " está vencido, no se puede renovar.");
        }

        int maxRenovaciones = configuracionSistemaService.obtenerValorEntero(CLAVE_MAX_RENOVACIONES_DEFAULT);
        if (prestamo.getRenovacionesRealizadas() >= maxRenovaciones) {
            throw new LimiteRenovacionesExcedidoException(
                    "El préstamo " + prestamoId + " ya alcanzó el máximo de "
                            + maxRenovaciones + " renovaciones permitidas.");
        }

        if (existeReservaVigenteDeOtroUsuario(prestamo.getLibroId(), prestamo.getUsuarioId())) {
            throw new MaterialReservadoException(
                    "El libro del préstamo " + prestamoId
                            + " tiene una reserva vigente de otro usuario.");
        }

        int diasPrestamo = configuracionSistemaService.obtenerValorEntero(CLAVE_DIAS_PRESTAMO_DEFAULT);
        prestamo.setFechaDevolucionEstimada(OffsetDateTime.now().plusDays(diasPrestamo));
        prestamo.setRenovacionesRealizadas((short) (prestamo.getRenovacionesRealizadas() + 1));
        prestamo.setEstadoPrestamoId(idEstadoPrestamo(ESTADO_RENOVADO));
        prestamoRepo.save(prestamo);

        registrarAuditoria(resolverIdPorCorreo(authentication.getName()), prestamoId, "Renovación del préstamo " + prestamoId + " (renovación " + prestamo.getRenovacionesRealizadas() + "/" + maxRenovaciones + ")");

        return new RenovacionResponseDTO(
                prestamo.getId(),
                prestamo.getFechaDevolucionEstimada(),
                prestamo.getRenovacionesRealizadas(),
                (short) (maxRenovaciones - prestamo.getRenovacionesRealizadas()));
    }

    private boolean existeReservaVigenteDeOtroUsuario(Long libroId, Long usuarioIdDuenoPrestamo) {
        List<Integer> idsEstadosVigentes = ESTADOS_RESERVA_VIGENTE.stream()
                .map(nombre -> estadoReservacionRepo.findByNombre(nombre)
                        .orElseThrow(() -> new IllegalStateException(
                                "Catálogo estados_reservacion sin fila '" + nombre + "'"))
                        .getId())
                .toList();
        return reservacionRepo.existsByLibroIdAndEstadoReservacionIdInAndUsuarioIdNot(
                libroId, idsEstadosVigentes, usuarioIdDuenoPrestamo);
    }

    // Se usa IllegalStateException para "fila de catálogo faltante": es un
    // problema de seed/configuración del sistema, no un error del cliente
    // -- mismo criterio que ReservacionService.fromDTO() con estados_reservacion.
    private String nombreEstadoPrestamo(Integer estadoId) {
        return estadoPrestamoRepo.findById(estadoId)
                .map(EstadoPrestamo::getNombre)
                .orElseThrow(() -> new IllegalStateException(
                        "estado_prestamo_id " + estadoId + " no existe en el catálogo estados_prestamo"));
    }

    private Integer idEstadoPrestamo(String nombre) {
        return estadoPrestamoRepo.findByNombre(nombre)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_prestamo sin fila '" + nombre + "'"))
                .getId();
    }

    // Ventanilla: conversión de reserva en préstamo (crear() marca la
    // reserva origen como RETIRADA para que no quede pendiente).
    private Integer idEstadoReservacion(String nombre) {
        return estadoReservacionRepo.findByNombre(nombre)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_reservacion sin fila '" + nombre + "'"))
                .getId();
    }

    @Transactional(readOnly = true)
    public Page<PrestamoResponseDTO> listarPorUsuario(Long usuarioId, Authentication authentication, Pageable pageable) {
        validarAccesoUsuario(usuarioId, authentication);
        return prestamoRepo.findByUsuarioId(usuarioId, pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public List<PrestamoActivoResponseDTO> listarActivosPorUsuario(Long usuarioId, Authentication authentication) {
        validarAccesoUsuario(usuarioId, authentication);
        return prestamoRepo.findActivosByUsuarioId(usuarioId).stream()
                .map(this::toDTO)
                .toList();
    }

    private static final int LIMITE_REPORTE_DEFAULT = 10;

    @Transactional(readOnly = true)
    public List<LibroMasPrestadoResponseDTO> reporteLibrosMasPrestados(
            Integer limite, OffsetDateTime desde, OffsetDateTime hasta) {
        // El DEFAULT 10 de fn_reporte_libros_mas_prestados solo se activa
        // cuando el parámetro se omite POR COMPLETO de la llamada SQL. La
        // @Query nativeQuery de PrestamoProcedureRepository siempre envía
        // los 3 parámetros nombrados, así que un null explícito produce
        // "LIMIT NULL" en Postgres = sin límite, no el default esperado.
        // Se aplica el default aquí para que el comportamiento.
        Integer limiteEfectivo = (limite != null) ? limite : LIMITE_REPORTE_DEFAULT;
        return prestamoProcRepo.fnReporteLibrosMasPrestados(limiteEfectivo, desde, hasta).stream()
                .map(this::toDTO)
                .toList();
    }

    // ── GET /reportes/morosidad (Módulo 7) ──────────────────
    // Mismo motivo del default aplicado en Java (no en el parámetro SQL)
    // que reporteLibrosMasPrestados: la @Query nativeQuery siempre envía
    // p_limite explícito, así que un null produce "LIMIT NULL" (sin
    // límite) en vez de activar el DEFAULT 10 de la función SQL.
    @Transactional(readOnly = true)
    public List<ReporteMorosidadResponseDTO> reporteMorosidad(Integer limite) {
        Integer limiteEfectivo = (limite != null) ? limite : LIMITE_REPORTE_DEFAULT;
        return prestamoProcRepo.fnReporteIndiceMorosidad(limiteEfectivo).stream()
                .map(this::toDTO)
                .toList();
    }

    // ── GET /reportes/uso (Módulo 7) ────────────────────────
    // Validación de p_granularidad en Java (400 vía IllegalArgumentException
    // -> GlobalExceptionHandler) en vez de dejar que un valor no reconocido
    // caiga silenciosamente al "ELSE 'day'" de fn_reporte_uso_por_periodo:
    // el fallback en SQL es defensa en profundidad, no la validación
    // primaria -- un cliente que manda "dias" (typo) debe recibir un 400
    // explicando el valor esperado, no un reporte diario silencioso.
    private static final List<String> GRANULARIDADES_VALIDAS = List.of("dia", "semana", "mes");

    @Transactional(readOnly = true)
    public List<ReporteUsoPorPeriodoResponseDTO> reporteUsoPorPeriodo(
            String granularidad, OffsetDateTime desde, OffsetDateTime hasta) {
        String granularidadEfectiva = (granularidad != null) ? granularidad.toLowerCase() : "dia";
        if (!GRANULARIDADES_VALIDAS.contains(granularidadEfectiva)) {
            throw new IllegalArgumentException(
                    "granularidad inválida: '" + granularidad
                            + "'. Valores permitidos: dia, semana, mes.");
        }
        return prestamoProcRepo.fnReporteUsoPorPeriodo(granularidadEfectiva, desde, hasta).stream()
                .map(this::toDTO)
                .toList();
    }

    // ── "Propio vs cualquiera": LECTOR solo puede pedir su propio
    // usuarioId; BIBLIOTECARIO/GERENTE no tienen restricción (mismo
    // patrón descrito en docs/reparto-entrega-3/cajas-backend/INSTRUCCIONES.md,
    // sección 1). ──
    private void validarAccesoUsuario(Long usuarioIdSolicitado, Authentication authentication) {
        boolean esLector = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(rol -> rol.equals("ROLE_" + ROL_LECTOR));
        if (!esLector) {
            return;
        }
        Long idPropio = resolverIdPorCorreo(authentication.getName());
        if (!idPropio.equals(usuarioIdSolicitado)) {
            throw new AuthorizationDeniedException(
                    "Un LECTOR solo puede consultar sus propios préstamos.");
        }
    }

    private Long resolverIdPorCorreo(String correo) {
        Usuario usuario = usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + correo));
        return usuario.getId();
    }

    private PrestamoResponseDTO toDTO(Prestamo p) {
        return new PrestamoResponseDTO(
                p.getId(),
                p.getUsuarioId(),
                p.getLibroId(),
                p.getBibliotecarioId(),
                p.getReservacionId(),
                p.getFechaPrestamo(),
                p.getFechaDevolucionEstimada(),
                p.getFechaDevolucionReal(),
                p.getRenovacionesRealizadas(),
                p.getEstadoPrestamoId());
    }

    private PrestamoActivoResponseDTO toDTO(PrestamoActivoProjection p) {
        return new PrestamoActivoResponseDTO(
                p.getPrestamoId(),
                p.getLibroTitulo(),
                p.getLibroIsbn(),
                p.getFechaPrestamo() != null ? p.getFechaPrestamo().atOffset(ZoneOffset.UTC) : null,
                p.getFechaDevolucionEstimada() != null ? p.getFechaDevolucionEstimada().atOffset(ZoneOffset.UTC) : null,
                p.getDiasRestantes(),
                p.getEstadoNombre());
    }

    private LibroMasPrestadoResponseDTO toDTO(LibroMasPrestadoProjection p) {
        return new LibroMasPrestadoResponseDTO(
                p.getLibroId(),
                p.getTitulo(),
                p.getIsbn(),
                p.getTotalPrestamos());
    }

    @Transactional(readOnly = true)
    public List<LibroMasPrestadoDetalladoResponseDTO> reporteLibrosMasPrestadosDetallado(
            Integer limite, OffsetDateTime desde, OffsetDateTime hasta, Integer categoriaId) {
        Integer limiteEfectivo = (limite != null) ? limite : LIMITE_REPORTE_DEFAULT;
        return prestamoProcRepo.fnReporteLibrosMasPrestadosDetallado(limiteEfectivo, desde, hasta, categoriaId).stream()
                .map(p -> new LibroMasPrestadoDetalladoResponseDTO(
                        p.getLibroId(),
                        p.getTitulo(),
                        p.getIsbn(),
                        p.getAutorNombre(),
                        p.getCategoriaNombre(),
                        p.getTotalPrestamos(),
                        p.getPorcentaje()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReporteInventarioResponseDTO> reporteInventario(
            Integer categoriaId, String estadoStock, String busqueda) {
        return prestamoProcRepo.fnReporteInventario(categoriaId, estadoStock, busqueda).stream()
                .map(p -> new ReporteInventarioResponseDTO(
                        p.getLibroId(),
                        p.getTitulo(),
                        p.getIsbn(),
                        p.getAutorNombre(),
                        p.getCategoriaNombre(),
                        p.getStockTotal(),
                        p.getStockDisponible(),
                        p.getEstadoDisponibilidad()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ReporteInventarioResponseDTO> reporteInventarioPaginado(
            Integer categoriaId, String estadoStock, String busqueda, Pageable pageable) {
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        String sortProperty = pageable.getSort().isSorted()
                ? pageable.getSort().iterator().next().getProperty()
                : null;
        List<ReporteInventarioProjection> projections =
                prestamoProcRepo.fnReporteInventarioPaginado(categoriaId, estadoStock, busqueda, limit, offset);
        long total = prestamoProcRepo.countReporteInventario(categoriaId, estadoStock, busqueda);
        List<ReporteInventarioResponseDTO> content = projections.stream()
                .map(p -> new ReporteInventarioResponseDTO(
                        p.getLibroId(), p.getTitulo(), p.getIsbn(), p.getAutorNombre(),
                        p.getCategoriaNombre(), p.getStockTotal(), p.getStockDisponible(),
                        p.getEstadoDisponibilidad()))
                .toList();
        // Sorting is applied at DB level via ORDER BY in fn_reporte_inventario; for custom sort we rely on DB order
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public List<ReporteVencidosResponseDTO> reportePrestamosVencidos(Integer diasAtrasoMin, String busqueda) {
        return prestamoProcRepo.fnReportePrestamosVencidos(diasAtrasoMin, busqueda).stream()
                .map(p -> new ReporteVencidosResponseDTO(
                        p.getPrestamoId(),
                        p.getUsuarioNombre(),
                        p.getUsuarioCorreo(),
                        p.getLibroTitulo(),
                        p.getLibroIsbn(),
                        p.getFechaDevolucionEstimada() != null ? p.getFechaDevolucionEstimada().atOffset(ZoneOffset.UTC) : null,
                        p.getDiasAtraso(),
                        p.getMontoMultaEstimada()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReporteCategoriasDemandadasResponseDTO> reporteCategoriasDemandadas(
            Integer limite, OffsetDateTime desde, OffsetDateTime hasta) {
        Integer limiteEfectivo = (limite != null) ? limite : LIMITE_REPORTE_DEFAULT;
        return prestamoProcRepo.fnReporteCategoriasDemandadas(limiteEfectivo, desde, hasta).stream()
                .map(p -> new ReporteCategoriasDemandadasResponseDTO(
                        p.getCategoriaId(),
                        p.getCategoriaNombre(),
                        p.getTotalPrestamos(),
                        p.getPorcentaje()))
                .toList();
    }

    private ReporteMorosidadResponseDTO toDTO(ReporteMorosidadProjection p) {
        return new ReporteMorosidadResponseDTO(
                p.getUsuarioId(),
                p.getNombre(),
                p.getApellido(),
                p.getCorreo(),
                p.getMontoTotalAdeudado(),
                p.getCantidadMultasPendientes(),
                p.getDiasAtrasoPromedio());
    }

    private ReporteUsoPorPeriodoResponseDTO toDTO(ReporteUsoPorPeriodoProjection p) {
        return new ReporteUsoPorPeriodoResponseDTO(
                p.getPeriodo() != null ? p.getPeriodo().atOffset(ZoneOffset.UTC) : null,
                p.getTotalPrestamos(),
                p.getTotalDevoluciones());
    }

    private void registrarAuditoria(Long ejecutorId, Long registroId, String detalles) {
        BitacoraAuditoria evento = BitacoraAuditoria.builder()
                .usuarioId(ejecutorId)
                .tipoOperacion("UPDATE")
                .tablaAfectada(TABLA_PRESTAMOS)
                .registroId(registroId)
                .detalles(detalles)
                .fechaHora(OffsetDateTime.now())
                .build();
        bitacoraAuditoriaRepo.save(evento);
    }

    private void validarLimitePrestamos(Long usuarioId) {
        int maxPrestamos = configuracionSistemaService.obtenerValorEntero("max_prestamos_usuario");
        List<PrestamoActivoProjection> activos = prestamoProcRepo.fnListarPrestamosActivosPorUsuario(usuarioId);
        if (activos.size() >= maxPrestamos) {
            throw new LimitePrestamosExcedidoException(
                    "El usuario ya tiene " + activos.size() + " préstamos activos. El máximo permitido es " + maxPrestamos + ".");
        }
    }
}