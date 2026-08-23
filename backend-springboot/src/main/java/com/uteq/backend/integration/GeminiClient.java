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
 * Cliente HTTP directo de la API {@code generateContent} de Gemini (Módulo
 * H). Se decidió NO usar el SDK de Vertex AI: el roadmap lo dejaba como
 * opción, pero en el repo la decisión ya tomada es HTTP directo vía
 * {@link RestClient} (spring-web viene con spring-boot-starter-webmvc, sin
 * dependencia nueva -- verificado en pom.xml).
 * <p>
 * IMPORTANTE (grounding): {@code promptSistema} DEBE instruir al modelo a
 * responder SOLO con la información real pasada como contexto y a NUNCA
 * inventar disponibilidad de libros. ChatbotService es quien construye ese
 * contexto real (base_conocimiento + resultados de LibroService.sugerir),
 * este cliente solo lo transporta.
 * <p>
 * Formato del payload: se usa el campo {@code systemInstruction} (soporte
 * oficial de generateContent) en vez de inyectar el prompt como primer
 * mensaje, porque así el historial de {@code contents} queda limpio para
 * alternar roles user/model como exige la API; el historial de la sesión se
 * mapea USUARIO-&gt;user / ASISTENTE-&gt;model. Respuesta parseada desde
 * {@code candidates[0].content.parts[].text}.
 * <p>
 * Errores de la API NUNCA se propagan al cliente HTTP: 429, timeouts y
 * cualquier 4xx/5xx se traducen a mensajes amigables fijos (con un único
 * reintento automático antes del fallback, sin librería de retries).
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

        // Timeout de conexión/lectura configurable (app.gemini.timeout-ms),
        // mismo criterio de configuración externa que el resto del repo.
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

    public String generarRespuesta(String promptSistema, List<MensajeChat> historial, String mensajeNuevo) {
        // Máximo 1 reintento automático: si el primer intento falla con 429
        // o timeout, se reintenta una vez antes de caer al mensaje amigable.
        for (int intento = 0; intento < 2; intento++) {
            try {
                return llamarGemini(promptSistema, historial, mensajeNuevo);
            } catch (HttpClientErrorException.TooManyRequests ex) {
                log.warn("Gemini respondió 429 (intento {}/2)", intento + 1);
                if (intento == 0) {
                    continue;
                }
                return MENSAJE_SATURADO;
            } catch (ResourceAccessException ex) {
                log.warn("Timeout/sin conexión hacia Gemini (intento {}/2)", intento + 1, ex);
                if (intento == 0) {
                    continue;
                }
                return MENSAJE_SATURADO;
        } catch (HttpClientErrorException ex) {
            log.error("Gemini respondió {} en el intento {}: body={}", ex.getStatusCode(), intento + 1, ex.getResponseBodyAsString());
            return MENSAJE_FALLBACK_GENERICO;
        } catch (HttpServerErrorException ex) {
            log.error("Gemini respondió error de servidor {} en el intento {}: body={}", ex.getStatusCode(), intento + 1, ex.getResponseBodyAsString());
            return MENSAJE_FALLBACK_GENERICO;
            }
        }
        return MENSAJE_SATURADO;
    }

    private String llamarGemini(String promptSistema, List<MensajeChat> historial, String mensajeNuevo) {
        List<Map<String, Object>> contents = new ArrayList<>();

        // Historial previo de la sesión (roles alternados user/model).
        for (MensajeChat mensaje : historial) {
            contents.add(Map.of(
                    "role", geminiRol(mensaje.getRol()),
                    "parts", List.of(Map.of("text", mensaje.getContenido()))));
        }

        // El mensaje nuevo va como último contenido "user". Si ya quedó
        // incluido como último elemento del historial (ChatbotService lo
        // persiste antes de llamar acá), no se duplica.
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

        String url = urlBase + "/models/" + modelo + ":generateContent?key=" + apiKey;

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            // Solo ocurre si la estructura interna del payload cambió de
            // forma incompatible -- un error de programación, no del cliente.
            throw new IllegalStateException("No se pudo serializar el payload de Gemini", ex);
        }

        String respuestaJson = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .retrieve()
                .body(String.class);

        log.debug("Respuesta cruda de Gemini: {}", respuestaJson);
        return extraerTexto(respuestaJson);
    }

    private String extraerTexto(String respuestaJson) {
        try {
            JsonNode root = objectMapper.readTree(respuestaJson);
            JsonNode parts = root.at("/candidates/0/content/parts");
            if (parts.isArray() && parts.size() > 0) {
                return parts.get(0).path("text").asText("");
            }
            log.warn("Respuesta de Gemini sin texto en candidates[0].content.parts: {}",
                    respuestaJson);
            return MENSAJE_FALLBACK_GENERICO;
        } catch (Exception ex) {
            log.error("No se pudo parsear la respuesta de Gemini", ex);
            return MENSAJE_FALLBACK_GENERICO;
        }
    }

    private String geminiRol(String rol) {
        return "ASISTENTE".equals(rol) ? "model" : "user";
    }
}
