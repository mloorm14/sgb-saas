package com.uteq.backend.service;

import com.uteq.backend.dto.BookSuggestionDTO;
import com.uteq.backend.dto.MessageChatHistoryDTO;
import com.uteq.backend.dto.MessageChatRequestDTO;
import com.uteq.backend.dto.MessageChatResponseDTO;
import com.uteq.backend.entity.KnowledgeBase;
import com.uteq.backend.entity.MessageChat;
import com.uteq.backend.entity.SessionChat;
import com.uteq.backend.entity.User;
import com.uteq.backend.integration.GeminiClient;
import com.uteq.backend.repository.BaseKnowledgeRepository;
import com.uteq.backend.repository.MessageChatRepository;
import com.uteq.backend.repository.SessionChatRepository;
import com.uteq.backend.repository.UserRepository;
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

    private final SessionChatRepository sessionChatRepo;
    private final MessageChatRepository messageChatRepo;
    private final BaseKnowledgeRepository baseKnowledgeRepo;
    private final UserRepository userRepo;
    private final BookService bookService;
    // Integración reservada para v2 (reservas desde el chat); aún no se invoca.
    private final ReservationService reservationService;
    private final GeminiClient geminiClient;
    private final ChatbotRateLimiter chatbotRateLimiter;

    @Transactional
    /**
     * Envia send message usando los datos y destinatarios recibidos.
     *
     * @param dto datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public MessageChatResponseDTO sendMessage(MessageChatRequestDTO dto, Authentication authentication) {
        Long userId = resolveIdByEmail(authentication.getName());

        if (chatbotRateLimiter.estaBlocked(userId)) {
            throw new ChatbotRateLimitExceededException(
                    "Has alcanzado el límite de mensajes al asistente. Intenta de nuevo en un momento.");
        }

        SessionChat session = resolveSession(dto.sessionId(), userId);

        // Persiste el mensaje del usuario ANTES de llamar a Gemini para no perder el intento.
        MessageChat msgUser = new MessageChat();
        msgUser.setSessionId(session.getId());
        msgUser.setRole(ROL_USUARIO);
        msgUser.setContent(dto.text());
        msgUser.setCreated(OffsetDateTime.now());
        messageChatRepo.save(msgUser);

        String promptSystem = construirPromptSystem(dto.text());
        List<MessageChat> history =
                messageChatRepo.findBySessionIdOrderByCreatedAsc(session.getId());

        String response = geminiClient.generateResponse(promptSystem, history, dto.text());

        MessageChat msgAsistente = new MessageChat();
        msgAsistente.setSessionId(session.getId());
        msgAsistente.setRole(ROL_ASISTENTE);
        msgAsistente.setContent(response);
        msgAsistente.setCreated(OffsetDateTime.now());
        messageChatRepo.save(msgAsistente);

        session.setLastActividad(OffsetDateTime.now());
        sessionChatRepo.save(session);

        chatbotRateLimiter.registerMessage(userId);

        return new MessageChatResponseDTO(session.getId(), response, msgAsistente.getCreated());
    }

    @Transactional(readOnly = true)
    /**
     * Consulta get history usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param sessionId valor de entrada sessionId usado por la operacion para completar su regla de negocio
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return lista de resultados que coincide con la consulta solicitada
     */
    public List<MessageChatHistoryDTO> getHistory(UUID sessionId, Authentication authentication) {
        Long userId = resolveIdByEmail(authentication.getName());
        SessionChat session = validatePropiedadSession(sessionId, userId);
        return messageChatRepo.findBySessionIdOrderByCreatedAsc(session.getId()).stream()
                .map(m -> new MessageChatHistoryDTO(m.getRole(), m.getContent(), m.getCreated()))
                .toList();
    }

    // ── Prompt de sistema con grounding real ──
    // Instruye al modelo a responder SOLO con el contexto provisto, sin inventar disponibilidad.
    private String construirPromptSystem(String textUser) {
        StringBuilder sb = new StringBuilder();
        sb.append("Eres el asistente virtual de la biblioteca SGB-SaaS. ")
                .append("Responde SOLO con la información real provista abajo (base de conocimiento y, ")
                .append("si aplica, resultados de búsqueda de libros). ")
                .append("NUNCA inventes datos, horarios, multas ni afirmes que un libro está o no ")
                .append("disponible si no aparece en los resultados reales. ")
                .append("Si no tienes contexto suficiente, dilo y sugiere consultar en ventanilla o en la app. ")
                .append("Responde en español, breve y útil.\n\n");

        sb.append("### Base de conocimiento:\n");
        for (KnowledgeBase bc : baseKnowledgeRepo.findByActiveTrue()) {
            sb.append("- [").append(bc.getCategory()).append("] ")
                    .append(bc.getQuestionExample()).append(" => ").append(bc.getResponse())
                    .append("\n");
        }

        if (tieneIntencionAvailability(textUser)) {
            List<BookSuggestionDTO> suggestions = bookService.sugerir(textUser);
            sb.append("\n### Disponibilidad real de libros (única fuente veraz):\n");
            if (suggestions.isEmpty()) {
                sb.append("(sin coincidencias en el catálogo para esta búsqueda)\n");
            }
            for (BookSuggestionDTO s : suggestions) {
                sb.append("- ").append(s.title())
                        .append(" [id=").append(s.id())
                        .append(", disponible=").append(s.available()).append("]\n");
            }
        }

        if (tieneIntencionReservation(textUser)) {
            sb.append("\n### Si el usuario pide reservar o apartar un libro: NO ejecutes la reserva. ")
                    .append("Indícale que confirme el título exacto y que puede reservar desde el ")
                    .append("catálogo o en ventanilla. Pide confirmación antes de dar por hecho nada.\n");
        }
        return sb.toString();
    }

    private boolean tieneIntencionAvailability(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        return PALABRAS_DISPONIBILIDAD.stream().anyMatch(t::contains);
    }

    private boolean tieneIntencionReservation(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        return PALABRAS_RESERVA.stream().anyMatch(t::contains);
    }

    private SessionChat resolveSession(UUID sessionId, Long userId) {
        if (sessionId == null) {
            OffsetDateTime ahora = OffsetDateTime.now();
            SessionChat fresh = new SessionChat();
            fresh.setUserId(userId);
            fresh.setCreated(ahora);
            fresh.setLastActividad(ahora);
            return sessionChatRepo.save(fresh);
        }
        return validatePropiedadSession(sessionId, userId);
    }

    private SessionChat validatePropiedadSession(UUID sessionId, Long userId) {
        SessionChat session = sessionChatRepo.findById(sessionId)
                .orElseThrow(() -> new SessionChatNotFoundException(
                        "Sesión de chat no encontrada: " + sessionId));
        // Sesión ajena se reporta igual que inexistente (404 genérico).
        if (!session.getUserId().equals(userId)) {
            throw new SessionChatNotFoundException(
                    "Sesión de chat no encontrada: " + sessionId);
        }
        return session;
    }

    private Long resolveIdByEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + email));
        return user.getId();
    }
}
