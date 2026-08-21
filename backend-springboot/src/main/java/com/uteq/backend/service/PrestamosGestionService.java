package com.uteq.backend.service;

import com.uteq.backend.dto.HistorialPrestamoDTO;
import com.uteq.backend.dto.ReservaActivaDTO;
import com.uteq.backend.dto.UsuarioPrestamosGestionDTO;
import com.uteq.backend.dto.UsuarioSugerenciaDTO;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.entity.Reservacion;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.EstadoMultaRepository;
import com.uteq.backend.repository.EstadoPrestamoRepository;
import com.uteq.backend.repository.EstadoReservacionRepository;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.repository.ReservacionRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lecturas de la ventanilla de préstamos del bibliotecario (módulo
 * "Préstamos" del sidebar): encontrar al usuario por correo y armar todo lo
 * que la pantalla necesita en una pasada -- tarjeta de identificación,
 * reserva vigente (Caso A) e historial reciente.
 *
 * Reutiliza el modelo existente sin duplicar nada (ver Javadoc de los DTOs):
 * - correo           -> usuarios.correo (identidad de login, UNIQUE; misma
 *                       columna que resuelve findByCorreo en todo el sistema)
 * - cédula (informativa en la tarjeta) -> usuarios.identificacion_usuario
 * - tipo de usuario -> roles del usuario
 * - estado de cuenta-> estados_usuario.nombre
 * - multas pendientes -> agregado sobre multas x prestamos (no es columna)
 * - días de préstamo sugeridos -> configuracion_sistema 'dias_prestamo_default'
 *
 * Sobre el bloqueo del Caso C: la REGLA de que no se puede prestar a un
 * usuario con multas ya vive en sp_crear_prestamo (rechaza
 * BLOQUEADO_POR_MULTA) y el sistema mantiene la invariante "multas
 * pendientes > 0 <=> usuario BLOQUEADO_POR_MULTA" de forma atómica
 * (sp_registrar_devolucion bloquea al generar la multa; sp_pagar_multa /
 * sp_anular_multa solo desbloquean cuando ya no queda ninguna PENDIENTE).
 * Acá solo se CALCULA el monto para pintar la alerta y el motivo exacto --
 * no se duplica la validación de creación, que sigue centralizada en el SP
 * que invoca PrestamoService.crear().
 */
@Service
public class PrestamosGestionService {

    private static final String USUARIO_NO_ENCONTRADO =
            "No se encontró ningún usuario con este correo";
    private static final String SIN_RESERVA_VIGENTE = "El usuario no tiene reservas vigentes";
    private static final String ESTADO_MULTA_PENDIENTE = "PENDIENTE";

    // "Vigente" = todavía puede terminar en retiro (mismo criterio que
    // PrestamoService.ESTADOS_RESERVA_VIGENTE; no existe un estado literal
    // "ACTIVA" en estados_reservacion).
    private static final List<String> ESTADOS_RESERVA_VIGENTE =
            List.of("PENDIENTE", "LISTA_PARA_RETIRO");

    private static final String CLAVE_DIAS_PRESTAMO_DEFAULT = "dias_prestamo_default";

    // Tope del historial reciente: línea de tiempo acotada, no un listado
    // paginado (para el historial completo ya existe GET /prestamos/usuario/{id}).
    private static final int LIMITE_HISTORIAL = 20;

    private final UsuarioRepository usuarioRepo;
    private final ReservacionRepository reservacionRepo;
    private final EstadoReservacionRepository estadoReservacionRepo;
    private final LibroRepository libroRepo;
    private final PrestamoRepository prestamoRepo;
    private final EstadoPrestamoRepository estadoPrestamoRepo;
    private final MultaRepository multaRepo;
    private final EstadoMultaRepository estadoMultaRepo;
    private final ConfiguracionSistemaService configuracionSistemaService;

    public PrestamosGestionService(UsuarioRepository usuarioRepo,
                                   ReservacionRepository reservacionRepo,
                                   EstadoReservacionRepository estadoReservacionRepo,
                                   LibroRepository libroRepo,
                                   PrestamoRepository prestamoRepo,
                                   EstadoPrestamoRepository estadoPrestamoRepo,
                                   MultaRepository multaRepo,
                                   EstadoMultaRepository estadoMultaRepo,
                                   ConfiguracionSistemaService configuracionSistemaService) {
        this.usuarioRepo = usuarioRepo;
        this.reservacionRepo = reservacionRepo;
        this.estadoReservacionRepo = estadoReservacionRepo;
        this.libroRepo = libroRepo;
        this.prestamoRepo = prestamoRepo;
        this.estadoPrestamoRepo = estadoPrestamoRepo;
        this.multaRepo = multaRepo;
        this.estadoMultaRepo = estadoMultaRepo;
        this.configuracionSistemaService = configuracionSistemaService;
    }

    // ── GET /gestion/buscar-usuario?correo= ──────────────────
    @Transactional(readOnly = true)
    public UsuarioPrestamosGestionDTO buscarPorCorreo(String correo) {
        Usuario usuario = usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO));

        Integer idMultaPendiente = idEstadoMulta(ESTADO_MULTA_PENDIENTE);
        BigDecimal montoPendiente = multaRepo.sumMontoByUsuarioIdAndEstadoMultaId(
                usuario.getId(), idMultaPendiente);
        long cantidadPendientes = multaRepo.countByUsuarioIdAndEstadoMultaId(
                usuario.getId(), idMultaPendiente);

        return new UsuarioPrestamosGestionDTO(
                usuario.getId(),
                (usuario.getNombre() + " " + usuario.getApellido()).trim(),
                usuario.getIdentificacionUsuario(),
                usuario.getCorreo(),
                usuario.getRoles().stream()
                        .map(rol -> rol.getNombre())
                        .sorted()
                        .toList(),
                usuario.getEstado().getNombre(),
                montoPendiente,
                cantidadPendientes,
                diasPrestamoSugerido());
    }

    // ── GET /gestion/sugerencias-usuarios?correo= ───────────
    // Autocompletado predictivo: retorna hasta 3 usuarios cuyo correo
    // contenga el texto ingresado (case-insensitive).
    @Transactional(readOnly = true)
    public List<UsuarioSugerenciaDTO> sugerenciasUsuarios(String correo) {
        if (correo == null || correo.trim().length() < 2) {
            return List.of();
        }
        return usuarioRepo.findTop3ByCorreoContainingIgnoreCaseOrderByNombreAsc(correo.trim())
                .stream()
                .map(u -> new UsuarioSugerenciaDTO(
                        u.getId(),
                        (u.getNombre() + " " + u.getApellido()).trim(),
                        u.getCorreo(),
                        u.getEstado().getNombre()))
                .toList();
    }

    // ── GET /gestion/reserva-activa?usuarioId= ───────────────
    // 404 (EntityNotFoundException) si no hay reserva vigente: el frontend
    // interpreta ese 404 como "Caso B: préstamo directo".
    @Transactional(readOnly = true)
    public ReservaActivaDTO reservaActiva(Long usuarioId) {
        List<Integer> idsVigentes = ESTADOS_RESERVA_VIGENTE.stream()
                .map(this::idEstadoReservacion)
                .toList();

        Reservacion reservacion = reservacionRepo
                .findFirstByUsuarioIdAndEstadoReservacionIdInOrderByFechaReservaDesc(usuarioId, idsVigentes)
                .orElseThrow(() -> new EntityNotFoundException(SIN_RESERVA_VIGENTE));

        Libro libro = libroRepo.findById(reservacion.getLibroId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "El libro de la reservación " + reservacion.getId() + " no existe"));

        return new ReservaActivaDTO(
                reservacion.getId(),
                libro.getId(),
                libro.getTitulo(),
                libro.getAutores().stream()
                        .map(autor -> autor.getNombre())
                        .sorted()
                        .toList(),
                libro.getIsbn(),
                reservacion.getFechaReserva(),
                reservacion.getFechaLimiteRetiro(),
                diasPrestamoSugerido(),
                libro.getAnioPublicacion(),
                libro.getStockDisponible(),
                libro.getStockTotal(),
                libro.getUbicacionFisica(),
                libro.getCategorias().stream()
                        .map(cat -> cat.getNombre())
                        .sorted()
                        .toList(),
                libro.getPortadaImagen() != null || (libro.getPortadaUrl() != null && !libro.getPortadaUrl().isBlank()));
    }

    // ── GET /gestion/historial?usuarioId= ────────────────────
    // Tres consultas en total (préstamos, libros+estados por lote, multas
    // agrupadas), nunca una por fila. Lista vacía si el usuario no tiene
    // préstamos: el frontend muestra "Este usuario no tiene préstamos
    // registrados".
    @Transactional(readOnly = true)
    public List<HistorialPrestamoDTO> historial(Long usuarioId) {
        List<Prestamo> prestamos = prestamoRepo.findByUsuarioIdOrderByIdDesc(usuarioId);
        if (prestamos.isEmpty()) {
            return List.of();
        }
        if (prestamos.size() > LIMITE_HISTORIAL) {
            prestamos = prestamos.subList(0, LIMITE_HISTORIAL);
        }

        Map<Long, String> titulosPorLibro = libroRepo.findAllById(
                        prestamos.stream().map(Prestamo::getLibroId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Libro::getId, Libro::getTitulo));

        Map<Integer, String> nombresPorEstado = estadoPrestamoRepo.findAllById(
                        prestamos.stream().map(Prestamo::getEstadoPrestamoId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e.getNombre()));

        Integer idMultaPendiente = idEstadoMulta(ESTADO_MULTA_PENDIENTE);
        Map<Long, BigDecimal> pendientesPorPrestamo = new HashMap<>();
        for (var fila : multaRepo.findPendientesAgrupadasPorPrestamo(usuarioId, idMultaPendiente)) {
            pendientesPorPrestamo.put(fila.getPrestamoId(), fila.getTotalPendiente());
        }

        return prestamos.stream()
                .map(p -> toHistorialDTO(p, titulosPorLibro, nombresPorEstado, pendientesPorPrestamo))
                .toList();
    }

    private HistorialPrestamoDTO toHistorialDTO(
            Prestamo p,
            Map<Long, String> titulosPorLibro,
            Map<Integer, String> nombresPorEstado,
            Map<Long, BigDecimal> pendientesPorPrestamo) {
        BigDecimal montoPendiente = pendientesPorPrestamo.get(p.getId());
        return new HistorialPrestamoDTO(
                p.getId(),
                titulosPorLibro.getOrDefault(p.getLibroId(), "Libro #" + p.getLibroId()),
                p.getFechaPrestamo(),
                p.getFechaDevolucionEstimada(),
                p.getFechaDevolucionReal(),
                nombresPorEstado.getOrDefault(p.getEstadoPrestamoId(), ""),
                montoPendiente != null,
                montoPendiente != null ? montoPendiente : BigDecimal.ZERO);
    }

    // Días de préstamo prellenados según la configuración del sistema
    // ('dias_prestamo_default', editable por el Admin en /admin/configuracion).
    private Integer diasPrestamoSugerido() {
        return configuracionSistemaService.obtenerValorEntero(CLAVE_DIAS_PRESTAMO_DEFAULT);
    }

    // Se usa IllegalStateException para "fila de catálogo faltante": problema
    // de seed/configuración, no error del cliente (mismo criterio que
    // PrestamoService.idEstadoPrestamo).
    private Integer idEstadoMulta(String nombre) {
        return estadoMultaRepo.findByNombre(nombre)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_multa sin fila '" + nombre + "'"))
                .getId();
    }

    private Integer idEstadoReservacion(String nombre) {
        return estadoReservacionRepo.findByNombre(nombre)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_reservacion sin fila '" + nombre + "'"))
                .getId();
    }
}
