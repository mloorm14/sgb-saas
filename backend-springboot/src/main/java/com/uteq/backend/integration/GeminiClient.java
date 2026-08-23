package com.uteq.backend.integration;

import com.uteq.backend.entity.MensajeChat;
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
 * Cliente HTTP directo de la API {@code generateContent} de Gemini (Módulo H).
 * Soporta function calling: puede enviar {@code tools} en el payload y
 * parsear respuestas con {@code functionCall} en vez de solo texto.
 * <p>
 * La lógica de ejecución de tools vive en {@code ChatbotOrchestrator}, no
 * acá. Este cliente solo transporta el payload y parsea la respuesta.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String MENSAJE_SATURADO =
            "El asistente está saturado, intenta en unos segundos.";
    private static final String MENSAJE_FALLBACK_GENERICO =
            "No se pudo obtener respuesta del asistente, intenta de nuevo.";

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
            log.warn("GEMINI_API_KEY no está configurada — las llamadas a Gemini fallarán");
        } else {
            log.info("GeminiClient inicializado: modelo={}, url={}", modelo, urlBase);
        }
    }

    // ── API legacy (sin tools, backward-compatible) ───────────────────────

    /**
     * Genera una respuesta de texto simple (sin function calling).
     * Se mantiene por backward-compatibility con tests existentes.
     */
    public String generarRespuesta(String promptSistema, List<MensajeChat> historial, String mensajeNuevo) {
        GeminiResponse respuesta = generarRespuestaConTools(promptSistema, historial, mensajeNuevo, List.of());
        return respuesta.getTexto();
    }

    // ── API con function calling ──────────────────────────────────────────

    /**
     * Genera una respuesta que puede ser texto o un functionCall.
     *
     * @param promptSistema prompt de sistema (grounding)
     * @param historial     mensajes previos de la sesión
     * @param mensajeNuevo  mensaje del usuario
     * @param tools         lista de tools en formato Gemini (desde ChatbotToolRegistry)
     * @return GeminiResponse con texto y/o functionCall
     */
    public GeminiResponse generarRespuestaConTools(
            String promptSistema,
            List<MensajeChat> historial,
            String mensajeNuevo,
            List<Map<String, Object>> tools) {

        for (int intento = 0; intento < 2; intento++) {
            try {
                return llamarGemini(promptSistema, historial, mensajeNuevo, tools);
            } catch (HttpClientErrorException.TooManyRequests ex) {
                log.warn("Gemini respondió 429 (intento {}/2)", intento + 1);
                if (intento == 0) continue;
                return GeminiResponse.texto(MENSAJE_SATURADO);
            } catch (ResourceAccessException ex) {
                log.warn("Timeout/sin conexión hacia Gemini (intento {}/2)", intento + 1, ex);
                if (intento == 0) continue;
                return GeminiResponse.texto(MENSAJE_SATURADO);
            } catch (HttpClientErrorException ex) {
                log.error("Gemini respondió {} en intento {}: body={}",
                        ex.getStatusCode(), intento + 1, ex.getResponseBodyAsString());
                return GeminiResponse.texto(MENSAJE_FALLBACK_GENERICO);
            } catch (HttpServerErrorException ex) {
                log.error("Gemini respondió error de servidor {} en intento {}: body={}",
                        ex.getStatusCode(), intento + 1, ex.getResponseBodyAsString());
                return GeminiResponse.texto(MENSAJE_FALLBACK_GENERICO);
            }
        }
        return GeminiResponse.texto(MENSAJE_SATURADO);
    }

    // ── Lógica interna ────────────────────────────────────────────────────

    private GeminiResponse llamarGemini(
            String promptSistema,
            List<MensajeChat> historial,
            String mensajeNuevo,
            List<Map<String, Object>> tools) {

        List<Map<String, Object>> contents = new ArrayList<>();

        for (MensajeChat mensaje : historial) {
            contents.add(Map.of(
                    "role", geminiRol(mensaje.getRol()),
                    "parts", List.of(Map.of("text", mensaje.getContenido()))));
        }

        if (historial.isEmpty()
                || !"USUARIO".equals(historial.get(historial.size() - 1).getRol())
                || !mensajeNuevo.equals(historial.get(historial.size() - 1).getContenido())) {
            contents.add(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", mensajeNuevo))));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", contents);
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", promptSistema))));

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

        log.debug("Payload Gemini (tools={}): {}", tools != null ? tools.size() : 0,
                jsonBody.length() > 500 ? jsonBody.substring(0, 500) + "..." : jsonBody);

        String respuestaJson = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .retrieve()
                .body(String.class);

        log.debug("Respuesta cruda de Gemini: {}", respuestaJson);
        return parsearRespuesta(respuestaJson);
    }

    private GeminiResponse parsearRespuesta(String respuestaJson) {
        try {
            JsonNode root = objectMapper.readTree(respuestaJson);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                log.warn("Respuesta de Gemini sin candidates: {}", respuestaJson);
                return GeminiResponse.texto(MENSAJE_FALLBACK_GENERICO);
            }

            JsonNode candidate = candidates.get(0);
            JsonNode content = candidate.path("content");
            JsonNode parts = content.path("parts");

            if (!parts.isArray() || parts.isEmpty()) {
                log.warn("Respuesta de Gemini sin parts: {}", respuestaJson);
                return GeminiResponse.texto(MENSAJE_FALLBACK_GENERICO);
            }

            // Verificar si hay functionCall
            JsonNode firstPart = parts.get(0);
            if (firstPart.has("functionCall")) {
                JsonNode functionCall = firstPart.path("functionCall");
                String name = functionCall.path("name").asText("");
                JsonNode args = functionCall.path("args");
                log.info("Gemini solicitó functionCall: {} con args: {}", name, args);
                return GeminiResponse.functionCall(name, args);
            }

            // Respuesta de texto normal
            String texto = firstPart.path("text").asText("");
            return GeminiResponse.texto(texto);

        } catch (Exception ex) {
            log.error("No se pudo parsear la respuesta de Gemini", ex);
            return GeminiResponse.texto(MENSAJE_FALLBACK_GENERICO);
        }
    }

    private String geminiRol(String rol) {
        return "ASISTENTE".equals(rol) ? "model" : "user";
    }

    // ── Response record ───────────────────────────────────────────────────

    /**
     * Respuesta estructurada de Gemini: puede contener texto, un functionCall,
     * o ambos (raro pero posible).
     */
    public record GeminiResponse(
            String texto,
            String functionName,
            JsonNode functionArgs,
            boolean isFunctionCall
    ) {
        public static GeminiResponse texto(String texto) {
            return new GeminiResponse(texto, null, null, false);
        }

        public static GeminiResponse functionCall(String name, JsonNode args) {
            return new GeminiResponse(null, name, args, true);
        }

        public String getTexto() {
            return texto != null ? texto : "";
        }
    }
}
