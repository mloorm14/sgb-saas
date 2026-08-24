package com.uteq.backend.service;

import com.uteq.backend.dto.LibroIsbnLookupDTO;
import com.uteq.backend.dto.PortadaImagenDTO;
import com.uteq.backend.integration.GeminiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autocompletar de inventario (Módulo inventario, mockup 14): consulta la
 * API pública de Google Books ({@code /volumes?q=isbn:...}) y mapea el
 * primer resultado a {@link LibroIsbnLookupDTO}. Mismo criterio de HTTP
 * directo que {@code integration/GeminiClient}: {@link RestClient} con
 * timeouts configurables, sin dependencias nuevas (verificado en pom.xml).
 * <p>
 * NUNCA se propagan errores de transporte al cliente HTTP: cualquier
 * fallo de red o 4xx/5xx de Google Books se traduce a
 * {@link EntityNotFoundException} (404 con ProblemDetail vía
 * GlobalExceptionHandler), igual que un ISBN sin resultados -- el
 * frontend muestra el mensaje "no se encontró información" en todos los
 * casos. La portada NO viaja en el DTO: se descarga por separado en
 * {@link #obtenerPortada(String)} (el frontend la sube con
 * LibroService.subirPortada al guardar el libro).
 */
@Service
public class LibroIsbnLookupService {

    private static final Logger log = LoggerFactory.getLogger(LibroIsbnLookupService.class);

    private static final String NO_ENCONTRADO =
            "No se pudo encontrar información de ese libro";
    private static final String ERROR_GOOGLE =
            "No se pudo encontrar información de ese libro";
    private static final Pattern ANIO_PATTERN = Pattern.compile("^(\\d{4})");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String urlBase;
    private final GeminiClient geminiClient;

    public LibroIsbnLookupService(
            @Value("${app.google-books.url-base}") String urlBase,
            @Value("${app.google-books.timeout-ms}") long timeoutMs,
            @Autowired(required = false) GeminiClient geminiClient) {
        this.urlBase = urlBase;
        this.objectMapper = new ObjectMapper();
        this.geminiClient = geminiClient;

        // Timeout de conexión/lectura configurable (app.google-books.timeout-ms),
        // mismo criterio de configuración externa que app.gemini.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeoutMs);
        requestFactory.setReadTimeout((int) timeoutMs);

        // RestClient propio (igual que GeminiClient): NO se inyecta
        // RestClient.Builder porque Boot 4 modular de este proyecto no
        // autoconfigura el bean (verificado: el context no arrancaba).
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public LibroIsbnLookupDTO buscarPorIsbn(String isbn) {
        try {
            JsonNode volume = buscarPrimerVolume(isbn);
            JsonNode volumeInfo = volume.path("volumeInfo");

            String autor = null;
            JsonNode authors = volumeInfo.path("authors");
            if (authors.isArray() && authors.size() > 0) {
                autor = authors.get(0).asText(null);
            }

            String titulo = volumeInfo.path("title").asText(null);
            String resumen = volumeInfo.path("description").asText(null);
            Integer anio = anioDesde(volumeInfo.path("publishedDate").asText(null));
            boolean portada = !volumeInfo.path("imageLinks").path("thumbnail").isMissingNode();

            // Solo titulo/resumen/anio son requeridos por el frontend; si Google trae alguno vacío,
            // se intenta complementar con IA (traducción/generación de resumen en español neutro).
            if ((resumen == null || resumen.isBlank()) && geminiClient != null) {
                String iaResumen = generarResumenViaIA(titulo, autor, isbn);
                if (iaResumen != null && !iaResumen.isBlank()) resumen = iaResumen;
            }

            return new LibroIsbnLookupDTO(titulo, autor, resumen, anio, portada);
        } catch (EntityNotFoundException ex) {
            // Google no encontró el ISBN: no inventar con IA (evita alucinaciones como Valencia vs programar).
            // Se propaga 404 y el frontend muestra "completa manualmente".
            throw ex;
        }
    }

    private String generarResumenViaIA(String titulo, String autor, String isbn) {
        try {
            String promptSistema = "Eres bibliotecario. Genera un resumen breve (max 500 caracteres, español neutro) para el libro."
                    + " Si no lo conoces, responde vacio. Responde SOLO con el texto del resumen, sin JSON ni comillas extra.";
            String mensaje = "ISBN: " + isbn + (titulo != null ? ", Titulo: " + titulo : "") + (autor != null ? ", Autor: " + autor : "");
            String resp = geminiClient.generarRespuesta(promptSistema, List.of(), mensaje);
            if (resp == null || resp.isBlank() || resp.contains("No se pudo")) return null;
            return resp.trim();
        } catch (Exception e) {
            log.warn("Fallback IA resumen falló para ISBN {}", isbn, e);
            return null;
        }
    }

    public PortadaImagenDTO obtenerPortada(String isbn) {
        JsonNode volume = buscarPrimerVolume(isbn);
        String thumbnail = volume.path("volumeInfo").path("imageLinks").path("thumbnail").asText(null);
        if (thumbnail == null) {
            throw new EntityNotFoundException(NO_ENCONTRADO);
        }
        byte[] bytes = restClient.get()
                .uri(thumbnail)
                .retrieve()
                .body(byte[].class);
        return new PortadaImagenDTO(bytes, "image/jpeg");
    }

    // El ISBN guardado admite guiones (misma regex que LibroRequestDTO);
    // Google Books espera solo dígitos, así que se limpian acá.
    private JsonNode buscarPrimerVolume(String isbn) {
        String url = urlBase + "/volumes?q=isbn:" + isbn.replace("-", "");
        String json = null;
        try {
            json = restClient.get().uri(url).retrieve().body(String.class);
        } catch (Exception ex) {
            // 429 Too Many Requests de Google (cuota sin API key) es temporal: reintento una vez
            if (ex.getMessage() != null && ex.getMessage().contains("429")) {
                try { Thread.sleep(1200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                try {
                    json = restClient.get().uri(url).retrieve().body(String.class);
                } catch (Exception ex2) {
                    log.error("Google Books no respondió (reintento) para el ISBN {}", isbn, ex2);
                    throw new EntityNotFoundException(ERROR_GOOGLE);
                }
            } else {
                log.error("Google Books no respondió para el ISBN {}", isbn, ex);
                throw new EntityNotFoundException(ERROR_GOOGLE);
            }
        }
        if (json == null) {
            throw new EntityNotFoundException(ERROR_GOOGLE);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception ex) {
            log.error("No se pudo parsear la respuesta de Google Books para el ISBN {}", isbn, ex);
            throw new EntityNotFoundException(ERROR_GOOGLE);
        }

        JsonNode items = root.path("items");
        if (root.path("totalItems").asInt(0) == 0 || !items.isArray() || items.isEmpty()) {
            throw new EntityNotFoundException(NO_ENCONTRADO);
        }
        return items.get(0);
    }

    // "2008" -> 2008, "2008-06-19" -> 2008, sin fecha -> null.
    private Integer anioDesde(String publishedDate) {
        if (publishedDate == null) {
            return null;
        }
        Matcher matcher = ANIO_PATTERN.matcher(publishedDate);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }
}