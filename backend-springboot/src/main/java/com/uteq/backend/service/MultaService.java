package com.uteq.backend.service;

import com.uteq.backend.dto.MultaAccionResponseDTO;
import com.uteq.backend.dto.MultaDetalleResponseDTO;
import com.uteq.backend.dto.MultaResponseDTO;
import com.uteq.backend.dto.ResumenFinancieroMultasResponseDTO;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.entity.Multa;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.MultaProcedureRepository;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.repository.projection.ResumenFinancieroMultasProjection;
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
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

@Service
public class MultaService {

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ROL_LECTOR = "LECTOR";
    private static final String PREFIJO_ROL = "ROLE_";
    private static final Set<String> ROLES_ANULACION = Set.of("GERENTE", "ADMIN");

    private final MultaRepository multaRepo;
    private final MultaProcedureRepository multaProcRepo;
    private final UsuarioRepository usuarioRepo;
    private final LibroRepository libroRepo;
    private final PrestamoRepository prestamoRepo;

    // La auditoria de esta tabla ya no se hace aqui: trg_auditoria_multas
    // (V49__auditoria_triggers_negocio.sql) audita INSERT/UPDATE/DELETE a nivel de motor.
    // sp_anular_multa (db/procs/sp_anular_multa.sql) sigue con su propio INSERT
    // manual interno -- eso es SQL dentro del procedimiento, no Java, y ya
    // estaba documentado como duplicacion aceptada en db/auditoria-triggers.sql
    // seccion 3; no se toca aqui.
    public MultaService(MultaRepository multaRepo,
                        MultaProcedureRepository multaProcRepo,
                        UsuarioRepository usuarioRepo,
                        LibroRepository libroRepo,
                        PrestamoRepository prestamoRepo) {
        this.multaRepo = multaRepo;
        this.multaProcRepo = multaProcRepo;
        this.usuarioRepo = usuarioRepo;
        this.libroRepo = libroRepo;
        this.prestamoRepo = prestamoRepo;
    }

    /**
     * Lista las multas de un usuario en forma resumida y paginada.
     * Aplica control de acceso por rol antes de consultar: un LECTOR solo
     * puede ver sus propias multas, otros roles pueden ver las de cualquiera.
     *
     * @param usuarioId identificador del dueño de las multas a listar
     * @param authentication autenticación vigente, usada para resolver el rol y el usuario propio
     * @param pageable paginación y orden solicitados por el cliente
     * @return página de multas en formato resumido
     * @throws AuthorizationDeniedException si un LECTOR pide multas de otro usuario
     */
    @Transactional(readOnly = true)
    /**
     * Executes the listarPorUsuario operation.
     * @param usuarioId value required by the operation
     * @param authentication value required by the operation
     * @param pageable value required by the operation
     * @return operation result
     */
    public Page<MultaResponseDTO> listarPorUsuario(Long usuarioId, Authentication authentication, Pageable pageable) {
        validarAccesoUsuario(usuarioId, authentication);
        return multaRepo.findByUsuarioId(usuarioId, pageable).map(this::toDTO);
    }

    /**
     * Lista las multas de un usuario con detalle enriquecido (libro, fechas
     * del préstamo, días de atraso y saldo), para la vista de gestión.
     * Comparte el mismo control de acceso por rol que {@link #listarPorUsuario}.
     *
     * @param usuarioId identificador del dueño de las multas a listar
     * @param authentication autenticación vigente, usada para resolver el rol y el usuario propio
     * @param pageable paginación y orden solicitados por el cliente
     * @return página de multas con detalle de préstamo y libro
     * @throws AuthorizationDeniedException si un LECTOR pide multas de otro usuario
     */
    @Transactional(readOnly = true)
    /**
     * Executes the listarDetallePorUsuario operation.
     * @param usuarioId value required by the operation
     * @param authentication value required by the operation
     * @param pageable value required by the operation
     * @return operation result
     */
    public Page<MultaDetalleResponseDTO> listarDetallePorUsuario(Long usuarioId, Authentication authentication, Pageable pageable) {
        validarAccesoUsuario(usuarioId, authentication);
        return multaRepo.findByUsuarioId(usuarioId, pageable).map(this::toDetalleDTO);
    }

    /**
     * Registra un pago parcial contra una multa sin saldarla por completo.
     * Delega en el procedimiento almacenado {@code sp_pago_parcial_multa},
     * que valida el monto y actualiza el saldo pendiente a nivel de motor.
     *
     * @param multaId identificador de la multa a abonar
     * @param montoPagado monto del abono parcial, debe ser positivo y no superar el saldo
     * @return mapa con las salidas del procedimiento (identificadores y estado resultante)
     */
    @Transactional
    /**
     * Executes the pagoParcial operation.
     * @param multaId value required by the operation
     * @param montoPagado value required by the operation
     * @return operation result
     */
    public Map<String, Object> pagoParcial(Long multaId, BigDecimal montoPagado) {
        return multaProcRepo.spPagoParcialMulta(multaId, montoPagado);
    }

    /**
     * Paga totalmente una multa y, si era la única pendiente del usuario,
     * lo desbloquea para nuevos préstamos. La lógica vive en
     * {@code sp_pagar_multa}; aquí solo se adapta su salida al DTO.
     *
     * @param multaId identificador de la multa a pagar
     * @return acción resultante con el id de la multa y si el usuario quedó desbloqueado
     */
    @Transactional
    /**
     * Executes the pagar operation.
     * @param multaId value required by the operation
     * @return operation result
     */
    public MultaAccionResponseDTO pagar(Long multaId) {
        Map<String, Object> resultado = multaProcRepo.spPagarMulta(multaId);
        return new MultaAccionResponseDTO(
                (Long) resultado.get("o_multa_id"),
                (Boolean) resultado.get("o_usuario_desbloqueado"));
    }

    /**
     * Anula una multa con motivo registrado. Solo GERENTE o ADMIN pueden
     * ejecutarla: el rol se resuelve desde la autenticación y se envía al
     * procedimiento {@code sp_anular_multa} para auditoría.
     *
     * @param multaId identificador de la multa a anular
     * @param motivo justificación de la anulación, queda registrada en la multa
     * @param authentication autenticación vigente, de donde se extrae el rol ejecutor
     * @return acción resultante con el id de la multa y si el usuario quedó desbloqueado
     * @throws AuthorizationDeniedException si el ejecutor no es GERENTE ni ADMIN
     */
    @Transactional
    /**
     * Executes the anular operation.
     * @param multaId value required by the operation
     * @param motivo value required by the operation
     * @param authentication value required by the operation
     * @return operation result
     */
    public MultaAccionResponseDTO anular(Long multaId, String motivo, Authentication authentication) {
        String rolEjecutor = resolverRolAnulacion(authentication);
        Map<String, Object> resultado = multaProcRepo.spAnularMulta(multaId, motivo, rolEjecutor);
        return new MultaAccionResponseDTO(
                (Long) resultado.get("o_multa_id"),
                (Boolean) resultado.get("o_usuario_desbloqueado"));
    }

    /**
     * Resuelve el dueño de una multa navegando multa → préstamo → usuario.
     * Se usa para validar acceso y para notificar al lector correcto.
     *
     * @param multaId identificador de la multa cuyo dueño se busca
     * @return identificador del usuario dueño del préstamo multado
     * @throws EntityNotFoundException si la multa o su préstamo no existen
     */
    @Transactional(readOnly = true)
    /**
     * Executes the resolverUsuarioIdDeMulta operation.
     * @param multaId value required by the operation
     * @return operation result
     */
    public Long resolverUsuarioIdDeMulta(Long multaId) {
        Multa multa = multaRepo.findById(multaId)
                .orElseThrow(() -> new EntityNotFoundException("Multa no encontrada: " + multaId));
        Prestamo prestamo = prestamoRepo.findById(multa.getPrestamoId())
                .orElseThrow(() -> new EntityNotFoundException("Prestamo no encontrado: " + multa.getPrestamoId()));
        return prestamo.getUsuarioId();
    }

    /**
     * Arma el resumen financiero de multas para el dashboard gerente:
     * total recaudado y pendiente en el rango pedido, total generado hoy
     * y los 5 pagos más recientes. Combina tres funciones de base de datos.
     *
     * @param desde inicio del rango del resumen, inclusivo
     * @param hasta fin del rango del resumen, inclusivo
     * @return resumen con recaudado, pendiente, generado hoy y pagos recientes
     */
    @Transactional(readOnly = true)
    /**
     * Executes the reporteResumenFinanciero operation.
     * @param desde value required by the operation
     * @param hasta value required by the operation
     * @return operation result
     */
    public ResumenFinancieroMultasResponseDTO reporteResumenFinanciero(OffsetDateTime desde, OffsetDateTime hasta) {
        ResumenFinancieroMultasProjection resumen = multaProcRepo.fnReporteResumenFinanciero(desde, hasta);

        // Total generado hoy: SUM(monto) de multas generadas hoy
        java.time.OffsetDateTime inicioHoy = java.time.OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        java.time.OffsetDateTime finHoy = inicioHoy.plusDays(1);
        ResumenFinancieroMultasProjection hoy = multaProcRepo.fnReporteResumenFinanciero(inicioHoy, finHoy);
        BigDecimal totalGeneradoHoy = hoy.getTotalRecaudado().add(hoy.getTotalPendiente());

        // Pagos recientes: últimos 5
        var pagosRecientes = multaProcRepo.fnPagosRecientes(5).stream()
                .map(p -> new com.uteq.backend.dto.PagoRecienteDTO(
                        p.getMultaId(),
                        p.getMontoPagado(),
                        p.getFechaPagada().atOffset(java.time.ZoneOffset.UTC),
                        p.getUsuarioCorreo(),
                        p.getUsuarioNombre(),
                        p.getLibroTitulo()))
                .toList();

        return new ResumenFinancieroMultasResponseDTO(
                resumen.getTotalRecaudado(),
                resumen.getTotalPendiente(),
                totalGeneradoHoy,
                pagosRecientes);
    }

    private String resolverRolAnulacion(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(rol -> rol.startsWith(PREFIJO_ROL))
                .map(rol -> rol.substring(PREFIJO_ROL.length()))
                .filter(ROLES_ANULACION::contains)
                .findFirst()
                .orElseThrow(() -> new AuthorizationDeniedException(
                        "Solo GERENTE o ADMIN puede anular multas."));
    }

    private void validarAccesoUsuario(Long usuarioIdSolicitado, Authentication authentication) {
        boolean esLector = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(rol -> rol.equals(PREFIJO_ROL + ROL_LECTOR));
        if (!esLector) {
            return;
        }
        Long idPropio = resolverIdPorCorreo(authentication.getName());
        if (!idPropio.equals(usuarioIdSolicitado)) {
            throw new AuthorizationDeniedException(
                    "Un LECTOR solo puede consultar sus propias multas.");
        }
    }

    private Long resolverIdPorCorreo(String correo) {
        Usuario usuario = usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + correo));
        return usuario.getId();
    }

    private MultaResponseDTO toDTO(Multa m) {
        return new MultaResponseDTO(
                m.getId(), m.getPrestamoId(), m.getMonto(),
                m.getEstadoMultaId(), m.getFechaGenerada(),
                m.getFechaPagada(), m.getObservaciones());
    }

    private MultaDetalleResponseDTO toDetalleDTO(Multa m) {
        Prestamo prestamo = prestamoRepo.findById(m.getPrestamoId()).orElse(null);
        String libroTitulo = "";
        String libroIsbn = "";
        OffsetDateTime fechaPrestamoInicio = null;
        OffsetDateTime fechaPrestamoFin = null;

        if (prestamo != null) {
            fechaPrestamoInicio = prestamo.getFechaPrestamo();
            fechaPrestamoFin = prestamo.getFechaDevolucionEstimada();
            Libro libro = libroRepo.findById(prestamo.getLibroId()).orElse(null);
            if (libro != null) {
                libroTitulo = libro.getTitulo();
                libroIsbn = libro.getIsbn() != null ? libro.getIsbn() : "";
            }
        }

        int diasAtraso = 0;
        if (m.getFechaPagada() != null && m.getFechaGenerada() != null) {
            diasAtraso = (int) ChronoUnit.DAYS.between(m.getFechaGenerada(), m.getFechaPagada());
        } else if (m.getEstadoMultaId() != null && m.getEstadoMultaId() == 1) {
            diasAtraso = (int) ChronoUnit.DAYS.between(m.getFechaGenerada(), OffsetDateTime.now());
        }

        BigDecimal montoPagado = m.getMontoPagado() != null ? m.getMontoPagado() : BigDecimal.ZERO;
        BigDecimal saldo = m.getMonto().subtract(montoPagado);

        Map<Integer, String> estados = Map.of(1, "PENDIENTE", 2, "PAGADA", 3, "ANULADA");
        String estadoNombre = estados.getOrDefault(m.getEstadoMultaId(), "DESCONOCIDO");

        return new MultaDetalleResponseDTO(
                m.getId(),
                m.getPrestamoId(),
                libroTitulo,
                libroIsbn,
                m.getObservaciones(),
                m.getMonto(),
                montoPagado,
                saldo,
                m.getEstadoMultaId(),
                estadoNombre,
                m.getFechaGenerada(),
                m.getFechaPagada(),
                fechaPrestamoInicio,
                fechaPrestamoFin,
                diasAtraso);
    }
}
