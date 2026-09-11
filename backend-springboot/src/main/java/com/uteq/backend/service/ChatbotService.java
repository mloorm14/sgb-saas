package com.uteq.backend.service;

import com.uteq.backend.dto.LibroSugerenciaDTO;
import com.uteq.backend.dto.MensajeChatHistorialDTO;
import com.uteq.backend.dto.MensajeChatRequestDTO;
import com.uteq.backend.dto.MensajeChatResponseDTO;
import com.uteq.backend.entity.BaseConocimiento;
import com.uteq.backend.entity.MensajeChat;
import com.uteq.backend.entity.SesionChat;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.integration.GeminiClient;
import com.uteq.backend.repository.BaseConocimientoRepository;
import com.uteq.backend.repository.MensajeChatRepository;
import com.uteq.backend.repository.SesionChatRepository;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.security.ChatbotRateLimiter;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Orquesta el chatbot asistente virtual (solo LECTOR): persiste el mensaje,
 * arma el prompt con grounding real y pide la respuesta a Gemini.
 * <p>
 * No ejecuta reservas desde el chat: solo indica cómo reservar; la ejecución
 * directa queda para v2 con confirmación explícita y selección por id.
 */
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ROL_USUARIO = "USUARIO";
    private static final String ROL_ASISTENTE = "ASISTENTE";

    private static final List<String> PALABRAS_DISPONIBILIDAD =
            List.of("disponible", "hay", "tienen", "existe");
    private static final List<String> PALABRAS_RESERVA =
            List.of("reservar", "apartar", "reserva");

    private final SesionChatRepository sesionChatRepo;
    private final MensajeChatRepository mensajeChatRepo;
    private final BaseConocimientoRepository baseConocimientoRepo;
    private final UsuarioRepository usuarioRepo;
    private final LibroService libroService;
    // Integración reservada para v2 (reservas desde el chat); aún no se invoca.
    private final ReservacionService reservacionService;
    private final GeminiClient geminiClient;
    private final ChatbotRateLimiter chatbotRateLimiter;

    @Transactional
    /**
     * Executes the enviarMensaje operation.
     * @param dto value required by the operation
     * @param authentication value required by the operation
     * @return operation result
     */
    public MensajeChatResponseDTO enviarMensaje(MensajeChatRequestDTO dto, Authentication authentication) {
        Long usuarioId = resolverIdPorCorreo(authentication.getName());

        if (chatbotRateLimiter.estaBloqueado(usuarioId)) {
            throw new ChatbotRateLimitExcedidoException(
                    "Has alcanzado el límite de mensajes al asistente. Intenta de nuevo en un momento.");
        }

        SesionChat sesion = resolverSesion(dto.sesionId(), usuarioId);

        // Persiste el mensaje del usuario ANTES de llamar a Gemini para no perder el intento.
        MensajeChat msgUsuario = new MensajeChat();
        msgUsuario.setSesionId(sesion.getId());
        msgUsuario.setRol(ROL_USUARIO);
        msgUsuario.setContenido(dto.texto());
        msgUsuario.setCreadoEn(OffsetDateTime.now());
        mensajeChatRepo.save(msgUsuario);

        String promptSistema = construirPromptSistema(dto.texto());
        List<MensajeChat> historial =
                mensajeChatRepo.findBySesionIdOrderByCreadoEnAsc(sesion.getId());

        String respuesta = geminiClient.generarRespuesta(promptSistema, historial, dto.texto());

        MensajeChat msgAsistente = new MensajeChat();
        msgAsistente.setSesionId(sesion.getId());
        msgAsistente.setRol(ROL_ASISTENTE);
        msgAsistente.setContenido(respuesta);
        msgAsistente.setCreadoEn(OffsetDateTime.now());
        mensajeChatRepo.save(msgAsistente);

        sesion.setUltimaActividad(OffsetDateTime.now());
        sesionChatRepo.save(sesion);

        chatbotRateLimiter.registrarMensaje(usuarioId);

        return new MensajeChatResponseDTO(sesion.getId(), respuesta, msgAsistente.getCreadoEn());
    }

    @Transactional(readOnly = true)
    /**
     * Executes the obtenerHistorial operation.
     * @param sesionId value required by the operation
     * @param authentication value required by the operation
     * @return operation result
     */
    public List<MensajeChatHistorialDTO> obtenerHistorial(UUID sesionId, Authentication authentication) {
        Long usuarioId = resolverIdPorCorreo(authentication.getName());
        SesionChat sesion = validarPropiedadSesion(sesionId, usuarioId);
        return mensajeChatRepo.findBySesionIdOrderByCreadoEnAsc(sesion.getId()).stream()
                .map(m -> new MensajeChatHistorialDTO(m.getRol(), m.getContenido(), m.getCreadoEn()))
                .toList();
    }

    // ── Prompt de sistema con grounding real ──
    // Instruye al modelo a responder SOLO con el contexto provisto, sin inventar disponibilidad.
    private String construirPromptSistema(String textoUsuario) {
        StringBuilder sb = new StringBuilder();
        sb.append("Eres el asistente virtual de la biblioteca SGB-SaaS. ")
                .append("Responde SOLO con la información real provista abajo (base de conocimiento y, ")
                .append("si aplica, resultados de búsqueda de libros). ")
                .append("NUNCA inventes datos, horarios, multas ni afirmes que un libro está o no ")
                .append("disponible si no aparece en los resultados reales. ")
                .append("Si no tienes contexto suficiente, dilo y sugiere consultar en ventanilla o en la app. ")
                .append("Responde en español, breve y útil.\n\n");

        sb.append("### Base de conocimiento:\n");
        for (BaseConocimiento bc : baseConocimientoRepo.findByActivoTrue()) {
            sb.append("- [").append(bc.getCategoria()).append("] ")
                    .append(bc.getPreguntaEjemplo()).append(" => ").append(bc.getRespuesta())
                    .append("\n");
        }

        if (tieneIntencionDisponibilidad(textoUsuario)) {
            List<LibroSugerenciaDTO> sugerencias = libroService.sugerir(textoUsuario);
            sb.append("\n### Disponibilidad real de libros (única fuente veraz):\n");
            if (sugerencias.isEmpty()) {
                sb.append("(sin coincidencias en el catálogo para esta búsqueda)\n");
            }
            for (LibroSugerenciaDTO s : sugerencias) {
                sb.append("- ").append(s.titulo())
                        .append(" [id=").append(s.id())
                        .append(", disponible=").append(s.disponible()).append("]\n");
            }
        }

        if (tieneIntencionReserva(textoUsuario)) {
            sb.append("\n### Si el usuario pide reservar o apartar un libro: NO ejecutes la reserva. ")
                    .append("Indícale que confirme el título exacto y que puede reservar desde el ")
                    .append("catálogo o en ventanilla. Pide confirmación antes de dar por hecho nada.\n");
        }
        return sb.toString();
    }

    private boolean tieneIntencionDisponibilidad(String texto) {
        String t = texto.toLowerCase(Locale.ROOT);
        return PALABRAS_DISPONIBILIDAD.stream().anyMatch(t::contains);
    }

    private boolean tieneIntencionReserva(String texto) {
        String t = texto.toLowerCase(Locale.ROOT);
        return PALABRAS_RESERVA.stream().anyMatch(t::contains);
    }

    private SesionChat resolverSesion(UUID sesionId, Long usuarioId) {
        if (sesionId == null) {
            OffsetDateTime ahora = OffsetDateTime.now();
            SesionChat nueva = new SesionChat();
            nueva.setUsuarioId(usuarioId);
            nueva.setCreadoEn(ahora);
            nueva.setUltimaActividad(ahora);
            return sesionChatRepo.save(nueva);
        }
        return validarPropiedadSesion(sesionId, usuarioId);
    }

    private SesionChat validarPropiedadSesion(UUID sesionId, Long usuarioId) {
        SesionChat sesion = sesionChatRepo.findById(sesionId)
                .orElseThrow(() -> new SesionChatNoEncontradaException(
                        "Sesión de chat no encontrada: " + sesionId));
        // Sesión ajena se reporta igual que inexistente (404 genérico).
        if (!sesion.getUsuarioId().equals(usuarioId)) {
            throw new SesionChatNoEncontradaException(
                    "Sesión de chat no encontrada: " + sesionId);
        }
        return sesion;
    }

    private Long resolverIdPorCorreo(String correo) {
        Usuario usuario = usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + correo));
        return usuario.getId();
    }
}
