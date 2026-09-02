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
    private static final String TIPO_COMPROBANTE_PAGO = "COMPROBANTE_PAGO";
    private static final String TIPO_DISPONIBLE = "DISPONIBLE";
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

    /**
     * Envía comprobante de pago de multa por correo al usuario dueño.
     * Incluye formato profesional: nombre del sistema, fecha/hora, datos
     * del usuario, detalle del pago con monto pagado y saldo.
     */
    @Transactional
    public void notificarComprobantePago(Long usuarioId, Long multaId, BigDecimal montoPagado) {
        Usuario usuario = usuarioRepo.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + usuarioId));

        String asunto = "Comprobante de pago de multa - SGB";
        String fechaHora = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy, HH:mm:ss"));

        String html = "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:0 auto;border:1px solid #e0e0e0;border-radius:8px;overflow:hidden;\">"
                + "<div style=\"background:#1a237e;color:white;padding:24px;text-align:center;\">"
                + "<h1 style=\"margin:0;font-size:22px;\">Sistema de Gestion Bibliotecaria</h1>"
                + "<p style=\"margin:8px 0 0;font-size:14px;opacity:0.9;\">Comprobante de Pago de Multa</p>"
                + "</div>"
                + "<div style=\"padding:24px;\">"
                + "<p style=\"color:#555;font-size:13px;margin:0 0 16px;\">Fecha: <strong>" + fechaHora + "</strong></p>"
                + "<p style=\"color:#555;font-size:13px;margin:0 0 4px;\">Cliente: <strong>"
                + usuario.getNombre() + " " + usuario.getApellido() + "</strong></p>"
                + "<p style=\"color:#555;font-size:13px;margin:0 0 20px;\">" + usuario.getCorreo() + "</p>"
                + "<hr style=\"border:none;border-top:1px solid #e0e0e0;margin:0 0 20px;\">"
                + "<table style=\"width:100%;font-size:14px;\">"
                + "<tr><td style=\"padding:6px 0;color:#555;\">Multa #</td><td style=\"padding:6px 0;text-align:right;font-weight:bold;\">" + multaId + "</td></tr>"
                + "<tr><td style=\"padding:6px 0;color:#555;\">Monto pagado</td><td style=\"padding:6px 0;text-align:right;font-weight:bold;color:#1565c0;\">$" + montoPagado + "</td></tr>"
                + "</table>"
                + "<div style=\"background:#e8f5e9;border-radius:8px;padding:16px;text-align:center;margin-top:20px;\">"
                + "<p style=\"margin:0;color:#2e7d32;font-size:13px;\">Estado del pago</p>"
                + "<p style=\"margin:4px 0 0;color:#1b5e20;font-size:18px;font-weight:bold;\">REGISTRADO</p>"
                + "</div>"
                + "</div>"
                + "<div style=\"background:#f5f5f5;padding:16px;text-align:center;font-size:11px;color:#999;\">"
                + "Sistema de Gestion Bibliotecaria &copy; " + java.time.Year.now().getValue()
                + "</div>"
                + "</div>";

        crearYEnviar(usuarioId, null, idDelTipo(TIPO_COMPROBANTE_PAGO), html, asunto);
    }

    @Transactional
    public void notificarLibroDisponible(Long usuarioId, Long libroId, String titulo) {
        String mensaje = "El libro \"" + titulo + "\" esta disponible ahora — reservalo antes que otros.";
        Integer tipoId = idDelTipo(TIPO_DISPONIBLE);
        // Notificacion manual: si permite envio, se intenta correo
        crearYEnviarDisponible(usuarioId, null, tipoId, mensaje, "Libro disponible");
    }

    private void crearYEnviarDisponible(Long usuarioId, Long prestamoId, Integer tipoNotificacionId, String mensaje, String asunto) {
        Usuario usuario = usuarioRepo.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + usuarioId));
        String cuerpoHtml = mensaje.startsWith("<") ? mensaje : "<p>" + mensaje + "</p>";
        boolean enviado = false;
        String error = null;
        try {
            enviado = emailService.enviarCorreo(usuario.getCorreo(), asunto, cuerpoHtml);
        } catch (Exception e) {
            error = e.getMessage();
        }
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuarioId(usuarioId);
        notificacion.setPrestamoId(prestamoId);
        notificacion.setTipoNotificacionId(tipoNotificacionId);
        notificacion.setMensaje(mensaje);
        notificacion.setEnviadoOk(enviado);
        notificacion.setErrorEnvio(enviado ? null : (error != null ? error : "Fallo envio correo disponible"));
        notificacion.setFechaEnvio(enviado ? OffsetDateTime.now() : null);
        notificacion.setCreadoEn(OffsetDateTime.now());
        notificacionRepo.save(notificacion);
    }

    // 2026-08-30: correos automáticos DESACTIVADOS por volumen en producción
    // (~100k registros, ~1k+ reservas caducadas) — SMTP generaba 1k+ intentos
    // por corrida (ReservacionScheduler cada 15 min + NotificacionVencimientoScheduler
    // cada 60s) con Authentication failed en EmailService. Se mantiene solo el
    // correo manual de verificación de cuenta (VerificacionCorreoService).
    // La notificación in-app (tabla notificaciones) sigue creándose.
    // Para DISPONIBLE (manual Notificarme) se usa crearYEnviarDisponible con email activo.
    private void crearYEnviar(Long usuarioId, Long prestamoId, Integer tipoNotificacionId, String mensaje, String asunto) {
        Usuario usuario = usuarioRepo.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + usuarioId));

        String cuerpoHtml = mensaje.startsWith("<") ? mensaje : "<p>" + mensaje + "</p>";
        // DESACTIVADO: boolean enviado = emailService.enviarCorreo(usuario.getCorreo(), asunto, cuerpoHtml);
        boolean enviado = false;

        Notificacion notificacion = new Notificacion();
        notificacion.setUsuarioId(usuarioId);
        notificacion.setPrestamoId(prestamoId);
        notificacion.setTipoNotificacionId(tipoNotificacionId);
        notificacion.setMensaje(mensaje);
        notificacion.setEnviadoOk(enviado);
        notificacion.setErrorEnvio(enviado ? null : "Correo automático desactivado (solo verificación activa) - ver NotificacionService.crearYEnviar");
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
