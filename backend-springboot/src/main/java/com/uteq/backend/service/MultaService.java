package com.uteq.backend.service;

import com.uteq.backend.dto.MultaAccionResponseDTO;
import com.uteq.backend.dto.MultaDetalleResponseDTO;
import com.uteq.backend.dto.MultaResponseDTO;
import com.uteq.backend.dto.ResumenFinancieroMultasResponseDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.entity.Multa;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
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
    private static final String TABLA_MULTAS = "multas";
    private static final String PREFIJO_ROL = "ROLE_";
    private static final Set<String> ROLES_ANULACION = Set.of("GERENTE", "ADMIN");

    private final MultaRepository multaRepo;
    private final MultaProcedureRepository multaProcRepo;
    private final UsuarioRepository usuarioRepo;
    private final LibroRepository libroRepo;
    private final PrestamoRepository prestamoRepo;
    private final BitacoraAuditoriaRepository bitacoraAuditoriaRepo;

    public MultaService(MultaRepository multaRepo,
                        MultaProcedureRepository multaProcRepo,
                        UsuarioRepository usuarioRepo,
                        LibroRepository libroRepo,
                        PrestamoRepository prestamoRepo,
                        BitacoraAuditoriaRepository bitacoraAuditoriaRepo) {
        this.multaRepo = multaRepo;
        this.multaProcRepo = multaProcRepo;
        this.usuarioRepo = usuarioRepo;
        this.libroRepo = libroRepo;
        this.prestamoRepo = prestamoRepo;
        this.bitacoraAuditoriaRepo = bitacoraAuditoriaRepo;
    }

    @Transactional(readOnly = true)
    public Page<MultaResponseDTO> listarPorUsuario(Long usuarioId, Authentication authentication, Pageable pageable) {
        validarAccesoUsuario(usuarioId, authentication);
        return multaRepo.findByUsuarioId(usuarioId, pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<MultaDetalleResponseDTO> listarDetallePorUsuario(Long usuarioId, Authentication authentication, Pageable pageable) {
        validarAccesoUsuario(usuarioId, authentication);
        return multaRepo.findByUsuarioId(usuarioId, pageable).map(this::toDetalleDTO);
    }

    @Transactional
    public Map<String, Object> pagoParcial(Long multaId, BigDecimal montoPagado) {
        Map<String, Object> resultado = multaProcRepo.spPagoParcialMulta(multaId, montoPagado);
        registrarAuditoria(null, multaId, "Pago parcial de la multa " + multaId + " por monto " + montoPagado);
        return resultado;
    }

    @Transactional
    public MultaAccionResponseDTO pagar(Long multaId) {
        Map<String, Object> resultado = multaProcRepo.spPagarMulta(multaId);
        registrarAuditoria(null, multaId, "Pago total de la multa " + multaId);
        return new MultaAccionResponseDTO(
                (Long) resultado.get("o_multa_id"),
                (Boolean) resultado.get("o_usuario_desbloqueado"));
    }

    @Transactional
    public MultaAccionResponseDTO anular(Long multaId, String motivo, Authentication authentication) {
        String rolEjecutor = resolverRolAnulacion(authentication);
        Map<String, Object> resultado = multaProcRepo.spAnularMulta(multaId, motivo, rolEjecutor);
        Long ejecutorId = resolverIdPorCorreo(authentication.getName());
        registrarAuditoria(ejecutorId, multaId, "Anulación de la multa " + multaId + ": " + motivo);
        return new MultaAccionResponseDTO(
                (Long) resultado.get("o_multa_id"),
                (Boolean) resultado.get("o_usuario_desbloqueado"));
    }

    @Transactional(readOnly = true)
    public Long resolverUsuarioIdDeMulta(Long multaId) {
        Multa multa = multaRepo.findById(multaId)
                .orElseThrow(() -> new EntityNotFoundException("Multa no encontrada: " + multaId));
        Prestamo prestamo = prestamoRepo.findById(multa.getPrestamoId())
                .orElseThrow(() -> new EntityNotFoundException("Prestamo no encontrado: " + multa.getPrestamoId()));
        return prestamo.getUsuarioId();
    }

    @Transactional(readOnly = true)
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

    private void registrarAuditoria(Long ejecutorId, Long registroId, String detalles) {
        BitacoraAuditoria evento = BitacoraAuditoria.builder()
                .usuarioId(ejecutorId)
                .tipoOperacion("UPDATE")
                .tablaAfectada(TABLA_MULTAS)
                .registroId(registroId)
                .detalles(detalles)
                .fechaHora(OffsetDateTime.now())
                .build();
        bitacoraAuditoriaRepo.save(evento);
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
