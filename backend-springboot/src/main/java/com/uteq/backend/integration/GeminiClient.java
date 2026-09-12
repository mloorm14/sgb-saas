package com.uteq.backend.integration;

import com.uteq.backend.entity.MessageChat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP de la API generateContent de Gemini con function calling.
 * Solo transporta el payload y parsea la respuesta; las tools las ejecuta el orchestrator.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String MENSAJE_SATURADO =
            "El asistente está saturado, intenta en unos segundos.";
    private static final String MENSAJE_FALLBACK_GENERICO =
            "No se pudo obtener respuesta del asistente, intenta de nuevo.";
    private static final String CLAVE_PARTS = "parts";
    private static final String CLAVE_FUNCTION_CALL = "functionCall";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String modelo;
    private final String urlBase;

    public GeminiClient(
            @Value("${app.gemini.api-key}") String apiKey,
            @Value("${app.gemini.modelo}") String modelo,
            @Value("${app.gemini.url-base}") String urlBase,
            @Value("${app.gemini.timeout-ms}") long timeoutMs) {
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.urlBase = urlBase;
        this.objectMapper = new ObjectMapper();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeoutMs);
        requestFactory.setReadTimeout((int) timeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY no configurada — Gemini deshabilitado (lookup ISBN usará solo Google Books).");
        } else {
            log.info("GeminiClient inicializado: modelo={}, url={}", modelo, urlBase);
        }
    }

    // ── API legacy (sin tools, backward-compatible) ───────────────────────

    /**
     * Genera una respuesta de texto simple (sin function calling).
     * Se mantiene por backward-compatibility con tests existentes.
     */
    public String generateResponse(String promptSystem, List<MessageChat> history, String messageFresh) {
        GeminiResponse response = generateResponseWithTools(promptSystem, history, messageFresh, List.of());
        return response.getText();
    }

    // ── API con function calling ──────────────────────────────────────────

    /**
     * Genera una respuesta que puede ser texto o un functionCall.
     *
     * @param promptSystem prompt de sistema (grounding)
     * @param history     mensajes previos de la sesión
     * @param messageFresh  mensaje del usuario
     * @param tools         lista de tools en formato Gemini (desde ChatbotToolRegistry)
     * @return GeminiResponse con texto y/o functionCall
     */
    public GeminiResponse generateResponseWithTools(
            String promptSystem,
            List<MessageChat> history,
            String messageFresh,
            List<Map<String, Object>> tools) {

        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Gemini deshabilitado (sin API key), devolviendo fallback");
            return GeminiResponse.text(MENSAJE_FALLBACK_GENERICO);
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return llamarGemini(promptSystem, history, messageFresh, tools);
            } catch (HttpClientErrorException.TooManyRequests ex) {
                log.warn("Gemini respondió 429 (intento {}/2)", attempt + 1);
                if (attempt == 0) continue;
                return GeminiResponse.text(MENSAJE_SATURADO);
            } catch (ResourceAccessException ex) {
                log.warn("Timeout/sin conexión hacia Gemini (intento {}/2)", attempt + 1, ex);
                if (attempt == 0) continue;
                return GeminiResponse.text(MENSAJE_SATURADO);
            } catch (HttpClientErrorException ex) {
                String responseBody = ex.getResponseBodyAsString();
                log.error("Gemini respondió {} en intento {}: body completo={}", ex.getStatusCode(), attempt + 1, responseBody);
                return GeminiResponse.text(MENSAJE_FALLBACK_GENERICO);
            } catch (HttpServerErrorException ex) {
                String responseBody = ex.getResponseBodyAsString();
                log.error("Gemini respondió error de servidor {} en intento {}: body completo={}", ex.getStatusCode(), attempt + 1, responseBody);
                return GeminiResponse.text(MENSAJE_FALLBACK_GENERICO);
            }
        }
        return GeminiResponse.text(MENSAJE_SATURADO);
    }

    // ── Lógica interna ────────────────────────────────────────────────────

    private GeminiResponse llamarGemini(
            String promptSystem,
            List<MessageChat> history,
            String messageFresh,
            List<Map<String, Object>> tools) {

        List<Map<String, Object>> contents = new ArrayList<>();

        for (MessageChat message : history) {
            String role = geminiRole(message.getRole());
            String content = message.getContent();

            if (content != null && content.startsWith("[FunctionCall:") && content.endsWith("]")) {
                // Parsear: [FunctionCall:nombre:{...}]
                String payload = content.substring("[FunctionCall:".length(), content.length() - 1);
                int sep = payload.indexOf(':');
                String name = payload.substring(0, sep);
                String argsJson = payload.substring(sep + 1);
                try {
                    JsonNode argsNode = objectMapper.readTree(argsJson);
                    contents.add(Map.of(
                            "role", "model",
                            CLAVE_PARTS, List.of(Map.of(CLAVE_FUNCTION_CALL, Map.of("name", name, "args", argsNode)))));
                } catch (Exception ex) {
                    log.warn("No se pudo parsear FunctionCall: {}", content);
                    contents.add(Map.of("role", role, CLAVE_PARTS, List.of(Map.of("text", content))));
                }
            } else if (content != null && content.startsWith("[FunctionResponse:") && content.endsWith("]")) {
                // Parsear: [FunctionResponse:nombre:{...}]
                String payload = content.substring("[FunctionResponse:".length(), content.length() - 1);
                int sep = payload.indexOf(':');
                String name = payload.substring(0, sep);
                String resultJson = payload.substring(sep + 1);
                try {
                    JsonNode resultNode = objectMapper.readTree(resultJson);
                    contents.add(Map.of(
                            "role", "user",
                            CLAVE_PARTS, List.of(Map.of("functionResponse", Map.of("name", name, "response", resultNode)))));
                } catch (Exception ex) {
                    log.warn("No se pudo parsear FunctionResponse: {}", content);
                    contents.add(Map.of("role", role, CLAVE_PARTS, List.of(Map.of("text", content))));
                }
            } else {
                contents.add(Map.of(
                        "role", role,
                        CLAVE_PARTS, List.of(Map.of("text", content != null ? content : ""))));
            }
        }

        if (history.isEmpty()
                || !"USUARIO".equals(history.get(history.size() - 1).getRole())
                || !messageFresh.equals(history.get(history.size() - 1).getContent())) {
            contents.add(Map.of(
                    "role", "user",
                    CLAVE_PARTS, List.of(Map.of("text", messageFresh))));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", contents);
        body.put("systemInstruction", Map.of(CLAVE_PARTS, List.of(Map.of("text", promptSystem))));

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        String url = urlBase + "/models/" + modelo + ":generateContent?key=" + apiKey;

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el payload de Gemini", ex);
        }

        log.debug("Payload Gemini completo (tools={}): {}", tools != null ? tools.size() : 0, jsonBody);

        String responseJson = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .retrieve()
                .body(String.class);

        log.debug("Respuesta cruda completa de Gemini: {}", responseJson);
        return parsearResponse(responseJson);
    }

    private GeminiResponse parsearResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                log.warn("Respuesta de Gemini sin candidates: {}", responseJson);
                return GeminiResponse.text(MENSAJE_FALLBACK_GENERICO);
            }

            JsonNode candidate = candidates.get(0);
            JsonNode content = candidate.path("content");
            JsonNode parts = content.path(CLAVE_PARTS);

            if (!parts.isArray() || parts.isEmpty()) {
                log.warn("Respuesta de Gemini sin parts: {}", responseJson);
                return GeminiResponse.text(MENSAJE_FALLBACK_GENERICO);
            }

            // Verificar si hay functionCall
            JsonNode firstPart = parts.get(0);
        if (firstPart.has(CLAVE_FUNCTION_CALL)) {
            JsonNode functionCall = firstPart.path(CLAVE_FUNCTION_CALL);
                String name = functionCall.path("name").asText("");
                JsonNode args = functionCall.path("args");
                log.info("Gemini solicitó functionCall: {} con args: {}", name, args);
                return GeminiResponse.functionCall(name, args);
            }

            // Respuesta de texto normal
            String text = firstPart.path("text").asText("");
            return GeminiResponse.text(text);

        } catch (Exception ex) {
            log.error("No se pudo parsear la respuesta de Gemini: {}", responseJson, ex);
            return GeminiResponse.text(MENSAJE_FALLBACK_GENERICO);
        }
    }

    private String geminiRole(String role) {
        return "ASISTENTE".equals(role) ? "model" : "user";
    }

    // ── Response record ───────────────────────────────────────────────────

    /**
     * Respuesta estructurada de Gemini: puede contener texto, un functionCall,
     * o ambos (raro pero posible).
     */
    public record GeminiResponse(
            String text,
            String functionName,
            JsonNode functionArgs,
            boolean isFunctionCall
    ) {
        /**
     * Handles texto.
     *
     * @param text text value used to scope this texto
     * @return Gemini Response reflecting the state after the operation
     */
    public static GeminiResponse text(String text) {
            return new GeminiResponse(text, null, null, false);
        }

        /**
     * Handles function Call.
     *
     * @param name text value used to scope this function Call
     * @param args JSON payload Node used to scope this function Call
     * @return Gemini Response reflecting the state after the operation
     */

        public static GeminiResponse functionCall(String name, JsonNode args) {
            return new GeminiResponse(null, name, args, true);
        }

        /**
     * Retrieves texto.
     *
     * @return resulting text payload
     */

        public String getText() {
            return text != null ? text : "";
        }
    }
}
