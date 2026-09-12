package com.uteq.backend.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.chatbot.tool.AbstractUserAwareTool;
import com.uteq.backend.dto.MessageChatHistoryDTO;
import com.uteq.backend.dto.MessageChatRequestDTO;
import com.uteq.backend.dto.MessageChatResponseDTO;
import com.uteq.backend.entity.KnowledgeBase;
import com.uteq.backend.entity.MessageChat;
import com.uteq.backend.entity.SessionChat;
import com.uteq.backend.entity.User;
import com.uteq.backend.integration.GeminiClient;
import com.uteq.backend.integration.GeminiClient.GeminiResponse;
import com.uteq.backend.repository.BaseKnowledgeRepository;
import com.uteq.backend.repository.MessageChatRepository;
import com.uteq.backend.repository.SessionChatRepository;
import com.uteq.backend.repository.UserRepository;
import com.uteq.backend.security.ChatbotRateLimiter;
import com.uteq.backend.service.ChatbotRateLimitExceededException;
import com.uteq.backend.service.SessionChatNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Orquestador del chatbot con function calling.
 * Flujo: valida rate limit → persiste mensaje → system prompt con base de
 * conocimiento → loop Gemini (functionCall → ejecuta tool → reinyecta) → persiste respuesta.
 */
@Service
public class ChatbotOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatbotOrchestrator.class);

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ROL_USUARIO = "USUARIO";
    private static final String ROL_ASISTENTE = "ASISTENTE";

    /** Máximo de iteraciones functionCall antes de cortar (evita loops infinitos). */
    private static final int MAX_FUNCTION_CALL_ITERATIONS = 5;

    private final SessionChatRepository sessionChatRepo;
    private final MessageChatRepository messageChatRepo;
    private final BaseKnowledgeRepository baseKnowledgeRepo;
    private final UserRepository userRepo;
    private final GeminiClient geminiClient;
    private final ChatbotToolRegistry toolRegistry;
    private final ChatbotRateLimiter chatbotRateLimiter;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatbotOrchestrator(
            SessionChatRepository sessionChatRepo,
            MessageChatRepository messageChatRepo,
            BaseKnowledgeRepository baseKnowledgeRepo,
            UserRepository userRepo,
            GeminiClient geminiClient,
            ChatbotToolRegistry toolRegistry,
            ChatbotRateLimiter chatbotRateLimiter) {
        this.sessionChatRepo = sessionChatRepo;
        this.messageChatRepo = messageChatRepo;
        this.baseKnowledgeRepo = baseKnowledgeRepo;
        this.userRepo = userRepo;
        this.geminiClient = geminiClient;
        this.toolRegistry = toolRegistry;
        this.chatbotRateLimiter = chatbotRateLimiter;
    }

    @Transactional
    /**
     * Sends message Chat Response data transfer object.
     *
     * @param dto message Chat Request data transfer object used to scope this message Chat Response data transfer object
     * @param authentication authentication of the caller used to scope this message Chat Response data transfer object
     * @return message Chat Response data transfer object reflecting the state after the operation
     * @throws ChatbotRateLimitExcedidoException when the message Chat Response data transfer object cannot be processed with the given input
     */
    public MessageChatResponseDTO sendMessage(MessageChatRequestDTO dto, Authentication authentication) {
        Long userId = resolveIdByEmail(authentication.getName());

        if (chatbotRateLimiter.estaBlocked(userId)) {
            throw new ChatbotRateLimitExceededException(
                    "Has alcanzado el límite de mensajes al asistente. Intenta de nuevo en un momento.");
        }

        SessionChat session = resolveSession(dto.sessionId(), userId);

        // Persistir mensaje del usuario ANTES de llamar a Gemini
        MessageChat msgUser = new MessageChat();
        msgUser.setSessionId(session.getId());
        msgUser.setRole(ROL_USUARIO);
        msgUser.setContent(dto.text());
        msgUser.setCreated(OffsetDateTime.now());
        messageChatRepo.save(msgUser);

        // Construir system prompt + tools
        String promptSystem = construirPromptSystem();
        List<Map<String, Object>> tools = toolRegistry.buildToolsPayload();
        List<MessageChat> history =
                messageChatRepo.findBySessionIdOrderByCreatedAsc(session.getId());

        // Loop de function calling
        String responseFinal = executeLoopFunctionCalling(promptSystem, history, dto.text(), tools, userId);

        // Persistir respuesta del asistente
        MessageChat msgAsistente = new MessageChat();
        msgAsistente.setSessionId(session.getId());
        msgAsistente.setRole(ROL_ASISTENTE);
        msgAsistente.setContent(responseFinal);
        msgAsistente.setCreated(OffsetDateTime.now());
        messageChatRepo.save(msgAsistente);

        session.setLastActividad(OffsetDateTime.now());
        sessionChatRepo.save(session);

        chatbotRateLimiter.registerMessage(userId);

        return new MessageChatResponseDTO(session.getId(), responseFinal, msgAsistente.getCreated());
    }

    @Transactional(readOnly = true)
    /**
     * Retrieves message Chat history DTO records.
     *
     * @param sesionId UUID used to scope this message Chat history DTO records
     * @param authentication authentication of the caller used to scope this message Chat history DTO records
     * @return list of message Chat history data transfer object matching the requested criteria
     */
    public List<MessageChatHistoryDTO> getHistory(UUID sessionId, Authentication authentication) {
        Long userId = resolveIdByEmail(authentication.getName());
        SessionChat session = validatePropiedadSession(sessionId, userId);
        return messageChatRepo.findBySessionIdOrderByCreatedAsc(session.getId()).stream()
                .map(m -> new MessageChatHistoryDTO(m.getRole(), m.getContent(), m.getCreated()))
                .toList();
    }

    // ── Function calling loop ─────────────────────────────────────────────

    /**
     * Ejecuta el loop de function calling:
     * 1. Envía a Gemini con tools
     * 2. Si Gemini responde con functionCall → ejecuta tool → agrega resultado al historial → repite
     * 3. Si Gemini responde con texto → retorna
     */
    private String executeLoopFunctionCalling(
            String promptSystem,
            List<MessageChat> history,
            String messageUser,
            List<Map<String, Object>> tools,
            Long userId) {

        // Trabajamos con una copia mutable del historial
        List<MessageChat> historyJob = new ArrayList<>(history);

        for (int i = 0; i < MAX_FUNCTION_CALL_ITERATIONS; i++) {
            GeminiResponse response = geminiClient.generateResponseWithTools(
                    promptSystem, historyJob, messageUser, tools);

            if (!response.isFunctionCall()) {
                // Respuesta de texto final
                return response.getText();
            }

            // Gemini pidió ejecutar una tool
            log.info("Function call iteración {}: {} con args {}",
                    i + 1, response.functionName(), response.functionArgs());

            // Inyectar usuario_id si la tool lo requiere
            JsonNode argsFinal = inyectarUserIdSiRequired(response.functionName(), response.functionArgs(), userId);

            // Ejecutar la tool real
            JsonNode result = toolRegistry.execute(response.functionName(), argsFinal);

            // Agregar al historial: el functionCall y el functionResponse
            MessageChat msgFuncCall = new MessageChat();
            msgFuncCall.setRole(ROL_ASISTENTE);
            msgFuncCall.setContent("[FunctionCall:" + response.functionName() + ":" + response.functionArgs() + "]");
            msgFuncCall.setCreated(OffsetDateTime.now());
            historyJob.add(msgFuncCall);

            MessageChat msgFuncResponse = new MessageChat();
            msgFuncResponse.setRole(ROL_USUARIO);
            msgFuncResponse.setContent("[FunctionResponse:" + response.functionName() + ":" + result.toString() + "]");
            msgFuncResponse.setCreated(OffsetDateTime.now());
            historyJob.add(msgFuncResponse);

            // En la siguiente iteración, Gemini verá el resultado y decidirá
            // si necesita otra tool o si ya puede responder al usuario.
            // El "mensajeNuevo" se mantiene como el último mensaje del usuario
            // para que Gemini tenga contexto de qué se pidió originalmente.
        }

        log.warn("Máximo de iteraciones de function calling alcanzado ({})", MAX_FUNCTION_CALL_ITERATIONS);
        return "No pude completar tu consulta con las herramientas disponibles. "
                + "Por favor, intenta reformular tu pregunta o consulta en ventanilla.";
    }

    // ── System prompt ─────────────────────────────────────────────────────

    private String construirPromptSystem() {
        StringBuilder sb = new StringBuilder();
        sb.append("Eres el asistente virtual de la biblioteca Leibri. ")
                .append("Tienes acceso a herramientas que consultan la base de datos real de la biblioteca. ")
                .append("Usa las herramientas cuando el usuario pregunte sobre:\n")
                .append("- Libros del catálogo (buscar por título, autor o tema)\n")
                .append("- Disponibilidad de un libro\n")
                .append("- Préstamos activos de un usuario\n")
                .append("- Multas pendientes de pago\n")
                .append("- Horarios de apertura\n")
                .append("- Políticas de préstamo, devolución y sanciones\n\n")
                .append("INSTRUCCIONES IMPORTANTES:\n")
                .append("1. NUNCA inventes datos. Si una herramienta no retorna resultados, dilo claramente.\n")
                .append("2. Si el usuario pregunta por disponibilidad de un libro, USA la herramienta buscar_libro.\n")
                .append("3. Si el usuario pregunta por sus préstamos o multas, USA las herramientas correspondientes con el usuario_id que se proporciona en el contexto.\n")
                .append("4. Si el usuario quiere RESERVAR un libro, NO ejecutes la reserva. Indícale que puede hacerlo desde el catálogo o en ventanilla.\n")
                .append("5. Responde en español, breve y útil. Usa un tono amigable de bibliotecario virtual.\n")
                .append("6. Si no tienes contexto suficiente para responder, sugiere consultar en ventanilla.\n\n");

        sb.append("### Base de conocimiento:\n");
        for (KnowledgeBase bc : baseKnowledgeRepo.findByActiveTrue()) {
            sb.append("- [").append(bc.getCategory()).append("] ")
                    .append(bc.getQuestionExample()).append(" => ").append(bc.getResponse())
                    .append("\n");
        }

        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

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

    /**
     * Inyecta automáticamente {@code usuario_id} en los argumentos de una tool
     * si el schema de la tool lo requiere (está en el array {@code required}).
     * <p>
     * Esto evita que Gemini tenga que conocer el ID del usuario autenticado,
     * y permite que tools como {@code consultar_multas}, {@code consultar_prestamos}
     * y {@code consultar_reservaciones} funcionen transparentes.
     */
    private JsonNode inyectarUserIdSiRequired(String toolName, JsonNode args, Long userId) {
        if (toolRegistry.requiresUserId(toolName)) {
            if (args == null || args.isNull() || !args.has(AbstractUserAwareTool.USUARIO_ID) || args.path(AbstractUserAwareTool.USUARIO_ID).asLong(0) == 0) {
                ObjectNode argsWithUser = mapper.createObjectNode();
                if (args != null && !args.isNull()) {
                    // Copiar campos existentes
                    args.fields().forEachRemaining(entry -> argsWithUser.set(entry.getKey(), entry.getValue()));
                }
                argsWithUser.put(AbstractUserAwareTool.USUARIO_ID, userId);
                log.debug("Inyectado usuario_id={} en tool {}", userId, toolName);
                return argsWithUser;
            }
        }
        return args;
    }
}
