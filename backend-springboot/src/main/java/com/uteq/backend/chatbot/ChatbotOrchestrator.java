package com.uteq.backend.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uteq.backend.chatbot.tool.AbstractUsuarioAwareTool;
import com.uteq.backend.dto.MensajeChatHistorialDTO;
import com.uteq.backend.dto.MensajeChatRequestDTO;
import com.uteq.backend.dto.MensajeChatResponseDTO;
import com.uteq.backend.entity.BaseConocimiento;
import com.uteq.backend.entity.MensajeChat;
import com.uteq.backend.entity.SesionChat;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.integration.GeminiClient;
import com.uteq.backend.integration.GeminiClient.GeminiResponse;
import com.uteq.backend.repository.BaseConocimientoRepository;
import com.uteq.backend.repository.MensajeChatRepository;
import com.uteq.backend.repository.SesionChatRepository;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.security.ChatbotRateLimiter;
import com.uteq.backend.service.ChatbotRateLimitExcedidoException;
import com.uteq.backend.service.SesionChatNoEncontradaException;
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
 * Orquestador del chatbot con function calling (reemplaza a ChatbotService).
 * <p>
 * Flujo:
 * <ol>
 *   <li>Resuelve usuario, valida rate limit, resuelve sesión</li>
 *   <li>Persiste el mensaje del usuario</li>
 *   <li>Construye el system prompt con grounding (base_conocimiento)</li>
 *   <li>Obtiene las tools del {@link ChatbotToolRegistry}</li>
 *   <li>Llama a Gemini con tools habilitados</li>
 *   <li>Si Gemini responde con functionCall → ejecuta la tool real → devuelve resultado a Gemini</li>
 *   <li>Repite hasta obtener respuesta de texto</li>
 *   <li>Persiste la respuesta y retorna</li>
 * </ol>
 */
@Service
public class ChatbotOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatbotOrchestrator.class);

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ROL_USUARIO = "USUARIO";
    private static final String ROL_ASISTENTE = "ASISTENTE";

    /** Máximo de iteraciones functionCall antes de cortar (evita loops infinitos). */
    private static final int MAX_FUNCTION_CALL_ITERATIONS = 5;

    private final SesionChatRepository sesionChatRepo;
    private final MensajeChatRepository mensajeChatRepo;
    private final BaseConocimientoRepository baseConocimientoRepo;
    private final UsuarioRepository usuarioRepo;
    private final GeminiClient geminiClient;
    private final ChatbotToolRegistry toolRegistry;
    private final ChatbotRateLimiter chatbotRateLimiter;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatbotOrchestrator(
            SesionChatRepository sesionChatRepo,
            MensajeChatRepository mensajeChatRepo,
            BaseConocimientoRepository baseConocimientoRepo,
            UsuarioRepository usuarioRepo,
            GeminiClient geminiClient,
            ChatbotToolRegistry toolRegistry,
            ChatbotRateLimiter chatbotRateLimiter) {
        this.sesionChatRepo = sesionChatRepo;
        this.mensajeChatRepo = mensajeChatRepo;
        this.baseConocimientoRepo = baseConocimientoRepo;
        this.usuarioRepo = usuarioRepo;
        this.geminiClient = geminiClient;
        this.toolRegistry = toolRegistry;
        this.chatbotRateLimiter = chatbotRateLimiter;
    }

    @Transactional
    public MensajeChatResponseDTO enviarMensaje(MensajeChatRequestDTO dto, Authentication authentication) {
        Long usuarioId = resolverIdPorCorreo(authentication.getName());

        if (chatbotRateLimiter.estaBloqueado(usuarioId)) {
            throw new ChatbotRateLimitExcedidoException(
                    "Has alcanzado el límite de mensajes al asistente. Intenta de nuevo en un momento.");
        }

        SesionChat sesion = resolverSesion(dto.sesionId(), usuarioId);

        // Persistir mensaje del usuario ANTES de llamar a Gemini
        MensajeChat msgUsuario = new MensajeChat();
        msgUsuario.setSesionId(sesion.getId());
        msgUsuario.setRol(ROL_USUARIO);
        msgUsuario.setContenido(dto.texto());
        msgUsuario.setCreadoEn(OffsetDateTime.now());
        mensajeChatRepo.save(msgUsuario);

        // Construir system prompt + tools
        String promptSistema = construirPromptSistema();
        List<Map<String, Object>> tools = toolRegistry.buildToolsPayload();
        List<MensajeChat> historial =
                mensajeChatRepo.findBySesionIdOrderByCreadoEnAsc(sesion.getId());

        // Loop de function calling
        String respuestaFinal = ejecutarLoopFunctionCalling(promptSistema, historial, dto.texto(), tools, usuarioId);

        // Persistir respuesta del asistente
        MensajeChat msgAsistente = new MensajeChat();
        msgAsistente.setSesionId(sesion.getId());
        msgAsistente.setRol(ROL_ASISTENTE);
        msgAsistente.setContenido(respuestaFinal);
        msgAsistente.setCreadoEn(OffsetDateTime.now());
        mensajeChatRepo.save(msgAsistente);

        sesion.setUltimaActividad(OffsetDateTime.now());
        sesionChatRepo.save(sesion);

        chatbotRateLimiter.registrarMensaje(usuarioId);

        return new MensajeChatResponseDTO(sesion.getId(), respuestaFinal, msgAsistente.getCreadoEn());
    }

    @Transactional(readOnly = true)
    public List<MensajeChatHistorialDTO> obtenerHistorial(UUID sesionId, Authentication authentication) {
        Long usuarioId = resolverIdPorCorreo(authentication.getName());
        SesionChat sesion = validarPropiedadSesion(sesionId, usuarioId);
        return mensajeChatRepo.findBySesionIdOrderByCreadoEnAsc(sesion.getId()).stream()
                .map(m -> new MensajeChatHistorialDTO(m.getRol(), m.getContenido(), m.getCreadoEn()))
                .toList();
    }

    // ── Function calling loop ─────────────────────────────────────────────

    /**
     * Ejecuta el loop de function calling:
     * 1. Envía a Gemini con tools
     * 2. Si Gemini responde con functionCall → ejecuta tool → agrega resultado al historial → repite
     * 3. Si Gemini responde con texto → retorna
     */
    private String ejecutarLoopFunctionCalling(
            String promptSistema,
            List<MensajeChat> historial,
            String mensajeUsuario,
            List<Map<String, Object>> tools,
            Long usuarioId) {

        // Trabajamos con una copia mutable del historial
        List<MensajeChat> historialTrabajo = new ArrayList<>(historial);

        for (int i = 0; i < MAX_FUNCTION_CALL_ITERATIONS; i++) {
            GeminiResponse respuesta = geminiClient.generarRespuestaConTools(
                    promptSistema, historialTrabajo, mensajeUsuario, tools);

            if (!respuesta.isFunctionCall()) {
                // Respuesta de texto final
                return respuesta.getTexto();
            }

            // Gemini pidió ejecutar una tool
            log.info("Function call iteración {}: {} con args {}",
                    i + 1, respuesta.functionName(), respuesta.functionArgs());

            // Inyectar usuario_id si la tool lo requiere
            JsonNode argsFinal = inyectarUsuarioIdSiRequerido(respuesta.functionName(), respuesta.functionArgs(), usuarioId);

            // Ejecutar la tool real
            JsonNode resultado = toolRegistry.execute(respuesta.functionName(), argsFinal);

            // Agregar al historial: el functionCall y el functionResponse
            MensajeChat msgFuncCall = new MensajeChat();
            msgFuncCall.setRol(ROL_ASISTENTE);
            msgFuncCall.setContenido("[FunctionCall:" + respuesta.functionName() + ":" + respuesta.functionArgs() + "]");
            msgFuncCall.setCreadoEn(OffsetDateTime.now());
            historialTrabajo.add(msgFuncCall);

            MensajeChat msgFuncResponse = new MensajeChat();
            msgFuncResponse.setRol(ROL_USUARIO);
            msgFuncResponse.setContenido("[FunctionResponse:" + respuesta.functionName() + ":" + resultado.toString() + "]");
            msgFuncResponse.setCreadoEn(OffsetDateTime.now());
            historialTrabajo.add(msgFuncResponse);

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

    private String construirPromptSistema() {
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
        for (BaseConocimiento bc : baseConocimientoRepo.findByActivoTrue()) {
            sb.append("- [").append(bc.getCategoria()).append("] ")
                    .append(bc.getPreguntaEjemplo()).append(" => ").append(bc.getRespuesta())
                    .append("\n");
        }

        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

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

    /**
     * Inyecta automáticamente {@code usuario_id} en los argumentos de una tool
     * si el schema de la tool lo requiere (está en el array {@code required}).
     * <p>
     * Esto evita que Gemini tenga que conocer el ID del usuario autenticado,
     * y permite que tools como {@code consultar_multas}, {@code consultar_prestamos}
     * y {@code consultar_reservaciones} funcionen transparentes.
     */
    private JsonNode inyectarUsuarioIdSiRequerido(String toolName, JsonNode args, Long usuarioId) {
        if (toolRegistry.requiresUserId(toolName)) {
            if (args == null || args.isNull() || !args.has(AbstractUsuarioAwareTool.USUARIO_ID) || args.path(AbstractUsuarioAwareTool.USUARIO_ID).asLong(0) == 0) {
                ObjectNode argsConUsuario = mapper.createObjectNode();
                if (args != null && !args.isNull()) {
                    // Copiar campos existentes
                    args.fields().forEachRemaining(entry -> argsConUsuario.set(entry.getKey(), entry.getValue()));
                }
                argsConUsuario.put(AbstractUsuarioAwareTool.USUARIO_ID, usuarioId);
                log.debug("Inyectado usuario_id={} en tool {}", usuarioId, toolName);
                return argsConUsuario;
            }
        }
        return args;
    }
}
