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

    // ── Constantes de renovar() ──
    private static final String ESTADO_DEVUELTO = "DEVUELTO";
    private static final String ESTADO_RENOVADO = "RENOVADO";
    // "Vigente": reserva que aún puede terminar en retiro (no RETIRADA/EXPIRADA/CANCELADA).
    private static final List<String> ESTADOS_RESERVA_VIGENTE = List.of("PENDIENTE", "LISTA_PARA_RETIRO");
    private static final String ESTADO_RESERVA_RETIRADA = "RETIRADA";
    private static final String CLAVE_DIAS_PRESTAMO_DEFAULT = "dias_prestamo_default";
    private static final String CLAVE_MAX_RENOVACIONES_DEFAULT = "max_renovaciones_default";
    private static final String CATALOGO_ESTADOS_RESERVA = "Catálogo estados_reservacion sin fila '";
    private static final String PRESTAMO_MSG = "El préstamo ";

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

        // Ventanilla: si nace de una reserva, se valida ANTES de tocar stock y se vincula DESPUÉS del SP.
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

    // Valida que la reservacionId sea una reserva VIGENTE del mismo usuario y libro; o null si es directo.
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
                                CATALOGO_ESTADOS_RESERVA + nombre + "'"))
                        .getId())
                .toList();
        if (!idsVigentes.contains(reservacion.getEstadoReservacionId())) {
            throw new IllegalStateException(
                    "La reservación " + dto.reservacionId()
                            + " ya no está vigente (pendiente o lista para retiro).");
        }
        return reservacion;
    }

    // Resuelve el usuario por credencialQrToken o usuarioId directo; debe venir EXACTAMENTE uno.
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

        // Las multas se crean dentro de sp_registrar_devolucion: este es el único punto donde se sabe que hubo una.
        if (Boolean.TRUE.equals(huboMulta)) {
            Prestamo prestamo = prestamoRepo.findById(prestamoId)
                    .orElseThrow(() -> new EntityNotFoundException(PRESTAMO_NO_ENCONTRADO + prestamoId));
            notificacionService.notificarMulta(prestamo.getUsuarioId(), prestamoId, montoMulta);
        }

        registrarAuditoria(null, prestamoId, "Devolución registrada del préstamo " + prestamoId);

        return new DevolucionResponseDTO(
                (Long) resultado.get("o_prestamo_id"), huboMulta, montoMulta);
    }

    // ── POST /{id}/renovacion ──
    // Validaciones en Java (consultas + UPDATE simple): cada rechazo lanza una excepción distinta.
    // Orden: 1. no existe → 404 / 2. LECTOR ajeno → 403 / 3. devuelto → 400
    //   4. vencido → 409 / 5. límite de renovaciones → 409 / 6. reserva vigente de otro → 409
    @Transactional
    public RenovacionResponseDTO renovar(Long prestamoId, Authentication authentication) {
        Prestamo prestamo = prestamoRepo.findById(prestamoId)
                .orElseThrow(() -> new EntityNotFoundException(PRESTAMO_NO_ENCONTRADO + prestamoId));

        validarAccesoUsuario(prestamo.getUsuarioId(), authentication);

        if (ESTADO_DEVUELTO.equals(nombreEstadoPrestamo(prestamo.getEstadoPrestamoId()))) {
            throw new IllegalArgumentException(
                    PRESTAMO_MSG + prestamoId + " ya fue devuelto, no se puede renovar.");
        }

        if (prestamo.getFechaDevolucionEstimada().isBefore(OffsetDateTime.now())) {
            throw new PrestamoVencidoException(
                    PRESTAMO_MSG + prestamoId + " está vencido, no se puede renovar.");
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
                                CATALOGO_ESTADOS_RESERVA + nombre + "'"))
                        .getId())
                .toList();
        return reservacionRepo.existsByLibroIdAndEstadoReservacionIdInAndUsuarioIdNot(
                libroId, idsEstadosVigentes, usuarioIdDuenoPrestamo);
    }

    // Fila de catálogo faltante = problema de seed/configuración, no error del cliente.
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

    // Conversión de reserva en préstamo: la reserva origen queda RETIRADA.
    private Integer idEstadoReservacion(String nombre) {
        return estadoReservacionRepo.findByNombre(nombre)
                .orElseThrow(() -> new IllegalStateException(
                        CATALOGO_ESTADOS_RESERVA + nombre + "'"))
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
        // El default 10 se aplica en Java: la @Query siempre envía p_limite explícito y null daría LIMIT NULL.
        Integer limiteEfectivo = (limite != null) ? limite : LIMITE_REPORTE_DEFAULT;
        return prestamoProcRepo.fnReporteLibrosMasPrestados(limiteEfectivo, desde, hasta).stream()
                .map(this::toDTO)
                .toList();
    }

    // ── GET /reportes/morosidad ──
    // Default 10 aplicado en Java por el mismo motivo que reporteLibrosMasPrestados.
    @Transactional(readOnly = true)
    public List<ReporteMorosidadResponseDTO> reporteMorosidad(Integer limite) {
        Integer limiteEfectivo = (limite != null) ? limite : LIMITE_REPORTE_DEFAULT;
        return prestamoProcRepo.fnReporteIndiceMorosidad(limiteEfectivo).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ReporteMorosidadResponseDTO> reporteMorosidadPaginado(Integer limite, Pageable pageable) {
        Integer limiteEfectivo = (limite != null) ? limite : LIMITE_REPORTE_DEFAULT;
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<ReporteMorosidadProjection> projections = prestamoProcRepo.fnReporteIndiceMorosidadPaginado(limiteEfectivo, limit, offset);
        long total = prestamoProcRepo.countReporteIndiceMorosidad(limiteEfectivo);
        List<ReporteMorosidadResponseDTO> content = projections.stream().map(this::toDTO).toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    // ── GET /reportes/uso ──
    // granularidad inválida → 400; el fallback en SQL es solo defensa en profundidad.
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

    @Transactional(readOnly = true)
    public Page<ReporteUsoPorPeriodoResponseDTO> reporteUsoPorPeriodoPaginado(
            String granularidad, OffsetDateTime desde, OffsetDateTime hasta, Pageable pageable) {
        String granularidadEfectiva = (granularidad != null) ? granularidad.toLowerCase() : "dia";
        if (!GRANULARIDADES_VALIDAS.contains(granularidadEfectiva)) {
            throw new IllegalArgumentException("granularidad inválida: '" + granularidad + "'. Valores permitidos: dia, semana, mes.");
        }
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<ReporteUsoPorPeriodoProjection> projections = prestamoProcRepo.fnReporteUsoPorPeriodoPaginado(granularidadEfectiva, desde, hasta, limit, offset);
        long total = prestamoProcRepo.countReporteUsoPorPeriodo(granularidadEfectiva, desde, hasta);
        List<ReporteUsoPorPeriodoResponseDTO> content = projections.stream().map(this::toDTO).toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    // ── Propio vs cualquiera ──
    // LECTOR solo su propio usuarioId; BIBLIOTECARIO/GERENTE sin restricción.
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
                        p.getLibroId(), p.getTitulo(), p.getIsbn(), p.getAutorNombre(), p.getCategoriaNombre(), p.getTotalPrestamos(), p.getPorcentaje()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<LibroMasPrestadoDetalladoResponseDTO> reporteLibrosMasPrestadosDetalladoPaginado(
            Integer limite, OffsetDateTime desde, OffsetDateTime hasta, Integer categoriaId, Pageable pageable) {
        Integer limiteEfectivo = (limite != null) ? limite : LIMITE_REPORTE_DEFAULT;
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<LibroMasPrestadoDetalladoProjection> projections = prestamoProcRepo.fnReporteLibrosDetalladoPaginado(limiteEfectivo, desde, hasta, categoriaId, limit, offset);
        long total = prestamoProcRepo.countReporteLibrosDetallado(limiteEfectivo, desde, hasta, categoriaId);
        List<LibroMasPrestadoDetalladoResponseDTO> content = projections.stream()
                .map(p -> new LibroMasPrestadoDetalladoResponseDTO(p.getLibroId(), p.getTitulo(), p.getIsbn(), p.getAutorNombre(), p.getCategoriaNombre(), p.getTotalPrestamos(), p.getPorcentaje()))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public List<ReporteInventarioResponseDTO> reporteInventario(
            Integer categoriaId, String estadoStock, String busqueda) {
        return prestamoProcRepo.fnReporteInventario(categoriaId, estadoStock, busqueda,
                        null, null, null, null, null, null, null, null, null, null, null).stream()
                .map(p -> new ReporteInventarioResponseDTO(
                        p.getLibroId(), p.getTitulo(), p.getIsbn(), p.getAutorNombre(), p.getCategoriaNombre(),
                        p.getStockTotal(), p.getStockDisponible(), p.getEstadoDisponibilidad(),
                        p.getEditorialNombre(), p.getProveedorNombre(), p.getIdiomaNombre(), p.getEstadoLibroNombre(),
                        p.getAnioPublicacion(), p.getUbicacionFisica()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ReporteInventarioResponseDTO> reporteInventarioPaginado(
            Integer categoriaId, String estadoStock, String busqueda, Pageable pageable) {
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<ReporteInventarioProjection> projections =
                prestamoProcRepo.fnReporteInventarioPaginado(categoriaId, estadoStock, busqueda,
                        null, null, null, null, null, null, null, null, null, null, null, limit, offset);
        long total = prestamoProcRepo.countReporteInventario(categoriaId, estadoStock, busqueda,
                        null, null, null, null, null, null, null, null, null, null, null);
        List<ReporteInventarioResponseDTO> content = projections.stream()
                .map(p -> new ReporteInventarioResponseDTO(
                        p.getLibroId(), p.getTitulo(), p.getIsbn(), p.getAutorNombre(), p.getCategoriaNombre(),
                        p.getStockTotal(), p.getStockDisponible(), p.getEstadoDisponibilidad(),
                        p.getEditorialNombre(), p.getProveedorNombre(), p.getIdiomaNombre(), p.getEstadoLibroNombre(),
                        p.getAnioPublicacion(), p.getUbicacionFisica()))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    // Sobrecarga con 8 filtros gerenciales.
    @Transactional(readOnly = true)
    public List<ReporteInventarioResponseDTO> reporteInventario(
            Integer categoriaId, String estadoStock, String busqueda,
            Integer editorialId, Integer proveedorId, Integer estadoLibroId, Integer idiomaId,
            Short anioDesde, Short anioHasta, Short stockTotalMin, Short stockTotalMax,
            Short stockDispMin, Short stockDispMax, String ubicacion) {
        return prestamoProcRepo.fnReporteInventario(categoriaId, estadoStock, busqueda,
                        editorialId, proveedorId, estadoLibroId, idiomaId, anioDesde, anioHasta,
                        stockTotalMin, stockTotalMax, stockDispMin, stockDispMax, ubicacion).stream()
                .map(p -> new ReporteInventarioResponseDTO(
                        p.getLibroId(), p.getTitulo(), p.getIsbn(), p.getAutorNombre(), p.getCategoriaNombre(),
                        p.getStockTotal(), p.getStockDisponible(), p.getEstadoDisponibilidad(),
                        p.getEditorialNombre(), p.getProveedorNombre(), p.getIdiomaNombre(), p.getEstadoLibroNombre(),
                        p.getAnioPublicacion(), p.getUbicacionFisica()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ReporteInventarioResponseDTO> reporteInventarioPaginado(
            Integer categoriaId, String estadoStock, String busqueda,
            Integer editorialId, Integer proveedorId, Integer estadoLibroId, Integer idiomaId,
            Short anioDesde, Short anioHasta, Short stockTotalMin, Short stockTotalMax,
            Short stockDispMin, Short stockDispMax, String ubicacion, Pageable pageable) {
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<ReporteInventarioProjection> projections =
                prestamoProcRepo.fnReporteInventarioPaginado(categoriaId, estadoStock, busqueda,
                        editorialId, proveedorId, estadoLibroId, idiomaId, anioDesde, anioHasta,
                        stockTotalMin, stockTotalMax, stockDispMin, stockDispMax, ubicacion, limit, offset);
        long total = prestamoProcRepo.countReporteInventario(categoriaId, estadoStock, busqueda,
                        editorialId, proveedorId, estadoLibroId, idiomaId, anioDesde, anioHasta,
                        stockTotalMin, stockTotalMax, stockDispMin, stockDispMax, ubicacion);
        List<ReporteInventarioResponseDTO> content = projections.stream()
                .map(p -> new ReporteInventarioResponseDTO(
                        p.getLibroId(), p.getTitulo(), p.getIsbn(), p.getAutorNombre(), p.getCategoriaNombre(),
                        p.getStockTotal(), p.getStockDisponible(), p.getEstadoDisponibilidad(),
                        p.getEditorialNombre(), p.getProveedorNombre(), p.getIdiomaNombre(), p.getEstadoLibroNombre(),
                        p.getAnioPublicacion(), p.getUbicacionFisica()))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public List<ReporteVencidosResponseDTO> reportePrestamosVencidos(Integer diasAtrasoMin, String busqueda) {
        return prestamoProcRepo.fnReportePrestamosVencidos(diasAtrasoMin, busqueda).stream()
                .map(p -> new ReporteVencidosResponseDTO(
                        p.getPrestamoId(), p.getUsuarioNombre(), p.getUsuarioCorreo(), p.getLibroTitulo(), p.getLibroIsbn(),
                        p.getFechaDevolucionEstimada() != null ? p.getFechaDevolucionEstimada().atOffset(ZoneOffset.UTC) : null,
                        p.getDiasAtraso(), p.getMontoMultaEstimada()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ReporteVencidosResponseDTO> reportePrestamosVencidosPaginado(Integer diasAtrasoMin, String busqueda, Pageable pageable) {
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<ReporteVencidosProjection> projections = prestamoProcRepo.fnReportePrestamosVencidosPaginado(diasAtrasoMin, busqueda, limit, offset);
        long total = prestamoProcRepo.countReportePrestamosVencidos(diasAtrasoMin, busqueda);
        List<ReporteVencidosResponseDTO> content = projections.stream()
                .map(p -> new ReporteVencidosResponseDTO(p.getPrestamoId(), p.getUsuarioNombre(), p.getUsuarioCorreo(), p.getLibroTitulo(), p.getLibroIsbn(),
                        p.getFechaDevolucionEstimada() != null ? p.getFechaDevolucionEstimada().atOffset(ZoneOffset.UTC) : null, p.getDiasAtraso(), p.getMontoMultaEstimada()))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public List<ReporteCategoriasDemandadasResponseDTO> reporteCategoriasDemandadas(
            Integer limite, OffsetDateTime desde, OffsetDateTime hasta) {
        Integer limiteEfectivo = (limite != null) ? limite : LIMITE_REPORTE_DEFAULT;
        return prestamoProcRepo.fnReporteCategoriasDemandadas(limiteEfectivo, desde, hasta).stream()
                .map(p -> new ReporteCategoriasDemandadasResponseDTO(p.getCategoriaId(), p.getCategoriaNombre(), p.getTotalPrestamos(), p.getPorcentaje()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ReporteCategoriasDemandadasResponseDTO> reporteCategoriasDemandadasPaginado(
            Integer limite, OffsetDateTime desde, OffsetDateTime hasta, Pageable pageable) {
        Integer limiteEfectivo = (limite != null) ? limite : LIMITE_REPORTE_DEFAULT;
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<ReporteCategoriasDemandadasProjection> projections = prestamoProcRepo.fnReporteCategoriasDemandadasPaginado(limiteEfectivo, desde, hasta, limit, offset);
        long total = prestamoProcRepo.countReporteCategoriasDemandadas(limiteEfectivo, desde, hasta);
        List<ReporteCategoriasDemandadasResponseDTO> content = projections.stream()
                .map(p -> new ReporteCategoriasDemandadasResponseDTO(p.getCategoriaId(), p.getCategoriaNombre(), p.getTotalPrestamos(), p.getPorcentaje()))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
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