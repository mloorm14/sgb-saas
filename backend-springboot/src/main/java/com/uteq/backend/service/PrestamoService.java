package com.uteq.backend.service;

import com.uteq.backend.dto.DevolucionResponseDTO;
import com.uteq.backend.dto.LibroMasPrestadoResponseDTO;
import com.uteq.backend.dto.PrestamoActivoResponseDTO;
import com.uteq.backend.dto.PrestamoRequestDTO;
import com.uteq.backend.dto.PrestamoResponseDTO;
import com.uteq.backend.dto.RenovacionResponseDTO;
import com.uteq.backend.entity.EstadoPrestamo;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.EstadoPrestamoRepository;
import com.uteq.backend.repository.EstadoReservacionRepository;
import com.uteq.backend.repository.PrestamoProcedureRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.repository.ReservacionRepository;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.repository.projection.LibroMasPrestadoProjection;
import com.uteq.backend.repository.projection.PrestamoActivoProjection;
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
import java.util.List;
import java.util.Map;

@Service
public class PrestamoService {

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
    private static final String CLAVE_DIAS_PRESTAMO_DEFAULT = "dias_prestamo_default";
    private static final String CLAVE_MAX_RENOVACIONES_DEFAULT = "max_renovaciones_default";

    private final PrestamoRepository prestamoRepo;
    private final PrestamoProcedureRepository prestamoProcRepo;
    private final UsuarioRepository usuarioRepo;
    private final EstadoPrestamoRepository estadoPrestamoRepo;
    private final ReservacionRepository reservacionRepo;
    private final EstadoReservacionRepository estadoReservacionRepo;
    private final ConfiguracionSistemaService configuracionSistemaService;

    public PrestamoService(PrestamoRepository prestamoRepo,
                           PrestamoProcedureRepository prestamoProcRepo,
                           UsuarioRepository usuarioRepo,
                           EstadoPrestamoRepository estadoPrestamoRepo,
                           ReservacionRepository reservacionRepo,
                           EstadoReservacionRepository estadoReservacionRepo,
                           ConfiguracionSistemaService configuracionSistemaService) {
        this.prestamoRepo = prestamoRepo;
        this.prestamoProcRepo = prestamoProcRepo;
        this.usuarioRepo = usuarioRepo;
        this.estadoPrestamoRepo = estadoPrestamoRepo;
        this.reservacionRepo = reservacionRepo;
        this.estadoReservacionRepo = estadoReservacionRepo;
        this.configuracionSistemaService = configuracionSistemaService;
    }

    @Transactional
    public PrestamoResponseDTO crear(PrestamoRequestDTO dto, Authentication authentication) {
        Long bibliotecarioId = resolverIdPorCorreo(authentication.getName());
        Long prestamoId = prestamoProcRepo.spCrearPrestamo(
                dto.usuarioId(), dto.libroId(), bibliotecarioId, dto.diasPrestamo());
        Prestamo prestamo = prestamoRepo.findById(prestamoId)
                .orElseThrow(() -> new EntityNotFoundException(PRESTAMO_NO_ENCONTRADO + prestamoId));
        return toDTO(prestamo);
    }

    @Transactional
    public DevolucionResponseDTO registrarDevolucion(Long prestamoId) {
        Map<String, Object> resultado = prestamoProcRepo.spRegistrarDevolucion(prestamoId);
        return new DevolucionResponseDTO(
                (Long) resultado.get("o_prestamo_id"),
                (Boolean) resultado.get("o_hubo_multa"),
                (BigDecimal) resultado.get("o_monto_multa"));
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

    @Transactional(readOnly = true)
    public Page<PrestamoResponseDTO> listarPorUsuario(Long usuarioId, Authentication authentication, Pageable pageable) {
        validarAccesoUsuario(usuarioId, authentication);
        return prestamoRepo.findByUsuarioId(usuarioId, pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public List<PrestamoActivoResponseDTO> listarActivosPorUsuario(Long usuarioId, Authentication authentication) {
        validarAccesoUsuario(usuarioId, authentication);
        return prestamoProcRepo.fnListarPrestamosActivosPorUsuario(usuarioId).stream()
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
                p.getFechaPrestamo(),
                p.getFechaDevolucionEstimada(),
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
}