package com.uteq.backend.service;

import com.uteq.backend.dto.NotificacionResponseDTO;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.entity.Notificacion;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.entity.Reservacion;
import com.uteq.backend.entity.TipoNotificacion;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.NotificacionRepository;
import com.uteq.backend.repository.TipoNotificacionRepository;
import com.uteq.backend.repository.UsuarioRepository;
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

/**
 * Genera y envía las 3 alertas del Módulo 2 (VENCIMIENTO, MULTA,
 * RESERVA_CADUCADA), y expone el listado por usuario para
 * {@code NotificacionController}. Cada método público corresponde a un
 * disparador distinto -- ver {@code NotificacionVencimientoScheduler},
 * {@code PrestamoService#registrarDevolucion} y
 * {@code ReservacionScheduler} para dónde se invocan.
 */
@Service
public class NotificacionService {

    private static final String TIPO_VENCIMIENTO = "VENCIMIENTO";
    private static final String TIPO_MULTA = "MULTA";
    private static final String TIPO_RESERVA_CADUCADA = "RESERVA_CADUCADA";
    private static final String ROL_LECTOR = "LECTOR";
    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String TIPO_NO_ENCONTRADO = "Catalogo tipos_notificacion sin fila '";

    private final NotificacionRepository notificacionRepo;
    private final TipoNotificacionRepository tipoNotificacionRepo;
    private final UsuarioRepository usuarioRepo;
    private final LibroRepository libroRepo;
    private final EmailService emailService;

    public NotificacionService(NotificacionRepository notificacionRepo,
                                TipoNotificacionRepository tipoNotificacionRepo,
                                UsuarioRepository usuarioRepo,
                                LibroRepository libroRepo,
                                EmailService emailService) {
        this.notificacionRepo = notificacionRepo;
        this.tipoNotificacionRepo = tipoNotificacionRepo;
        this.usuarioRepo = usuarioRepo;
        this.libroRepo = libroRepo;
        this.emailService = emailService;
    }

    /**
     * Invocado por {@code NotificacionVencimientoScheduler}. No hace nada
     * si ya existe una notificación VENCIMIENTO para este préstamo (ver
     * {@code NotificacionRepository#existsByPrestamoIdAndTipoNotificacionId})
     * -- el scheduler corre cada minuto y un préstamo puede seguir dentro
     * de la ventana de anticipación en varias ejecuciones consecutivas.
     */
    @Transactional
    public void generarAlertaVencimiento(Prestamo prestamo) {
        Integer tipoId = idDelTipo(TIPO_VENCIMIENTO);
        if (notificacionRepo.existsByPrestamoIdAndTipoNotificacionId(prestamo.getId(), tipoId)) {
            return;
        }

        String titulo = tituloDelLibro(prestamo.getLibroId());
        String mensaje = "Tu préstamo de \"" + titulo + "\" vence el " + prestamo.getFechaDevolucionEstimada()
                + ". Recuerda devolverlo o renovarlo a tiempo.";

        crearYEnviar(prestamo.getUsuarioId(), prestamo.getId(), tipoId, mensaje,
                "Tu préstamo está por vencer");
    }

    /**
     * Invocado por {@code PrestamoService#registrarDevolucion} cuando el
     * resultado de {@code sp_registrar_devolucion} reporta
     * {@code o_hubo_multa = true}. No hay deduplicación aquí (a diferencia
     * de {@code generarAlertaVencimiento}): una devolución con atraso
     * genera la multa una sola vez, nunca dos veces para el mismo préstamo.
     */
    @Transactional
    public void notificarMulta(Long usuarioId, Long prestamoId, BigDecimal monto) {
        String mensaje = "Se generó una multa de $" + monto + " asociada a tu préstamo #" + prestamoId
                + " por atraso en la devolución.";
        crearYEnviar(usuarioId, prestamoId, idDelTipo(TIPO_MULTA), mensaje, "Se generó una multa en tu cuenta");
    }

    /**
     * Invocado por {@code ReservacionScheduler} para cada reservación que
     * va a expirar en la corrida actual (resuelta ANTES de invocar
     * {@code sp_expirar_reservaciones_vencidas}, que solo devuelve un
     * conteo -- ver Javadoc de {@code ReservacionScheduler}).
     */
    @Transactional
    public void notificarReservaCaducada(Reservacion reservacion) {
        String titulo = tituloDelLibro(reservacion.getLibroId());
        String mensaje = "Tu reserva de \"" + titulo + "\" caducó porque no se retiró dentro del plazo.";
        crearYEnviar(reservacion.getUsuarioId(), null, idDelTipo(TIPO_RESERVA_CADUCADA), mensaje,
                "Tu reserva caducó");
    }

    // ── GET /notificaciones/usuario/{id} -- "propio vs cualquiera", mismo
    // patrón que MultaService/PrestamoService/ReservacionService. ──
    @Transactional(readOnly = true)
    public Page<NotificacionResponseDTO> listarPorUsuario(Long usuarioId, Authentication authentication, Pageable pageable) {
        validarAccesoUsuario(usuarioId, authentication);
        return notificacionRepo.findByUsuarioId(usuarioId, pageable).map(this::toDTO);
    }

    private void crearYEnviar(Long usuarioId, Long prestamoId, Integer tipoNotificacionId, String mensaje, String asunto) {
        Usuario usuario = usuarioRepo.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + usuarioId));

        boolean enviado = emailService.enviarCorreo(usuario.getCorreo(), asunto, "<p>" + mensaje + "</p>");

        Notificacion notificacion = new Notificacion();
        notificacion.setUsuarioId(usuarioId);
        notificacion.setPrestamoId(prestamoId);
        notificacion.setTipoNotificacionId(tipoNotificacionId);
        notificacion.setMensaje(mensaje);
        notificacion.setEnviadoOk(enviado);
        notificacion.setErrorEnvio(enviado ? null : "Fallo al enviar el correo (ver logs de EmailService)");
        notificacion.setFechaEnvio(enviado ? OffsetDateTime.now() : null);
        notificacion.setCreadoEn(OffsetDateTime.now());
        notificacionRepo.save(notificacion);
    }

    private String tituloDelLibro(Long libroId) {
        return libroRepo.findById(libroId).map(Libro::getTitulo).orElse("(libro no encontrado)");
    }

    private Integer idDelTipo(String nombre) {
        return tipoNotificacionRepo.findByNombre(nombre)
                .map(TipoNotificacion::getId)
                .orElseThrow(() -> new IllegalStateException(TIPO_NO_ENCONTRADO + nombre + "'"));
    }

    private void validarAccesoUsuario(Long usuarioIdSolicitado, Authentication authentication) {
        boolean esLector = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(rol -> rol.equals("ROLE_" + ROL_LECTOR));
        if (!esLector) {
            return;
        }
        Long idPropio = usuarioRepo.findByCorreo(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + authentication.getName()))
                .getId();
        if (!idPropio.equals(usuarioIdSolicitado)) {
            throw new AuthorizationDeniedException("Un LECTOR solo puede consultar sus propias notificaciones.");
        }
    }

    private NotificacionResponseDTO toDTO(Notificacion n) {
        return new NotificacionResponseDTO(
                n.getId(), n.getPrestamoId(), n.getTipoNotificacionId(),
                n.getMensaje(), n.getFechaEnvio(), n.isEnviadoOk(), n.getCreadoEn());
    }
}
