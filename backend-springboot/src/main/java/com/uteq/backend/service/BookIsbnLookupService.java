package com.uteq.backend.service;

import com.uteq.backend.dto.BookIsbnLookupDTO;
import com.uteq.backend.dto.CoverImageDTO;
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
 * Autocompletar de inventario: consulta Google Books por ISBN y mapea el
 * primer resultado. Los fallos de red se traducen a 404; la portada se
 * descarga por separado en {@code getCoverImage(String)}.
 */
@Service
public class BookIsbnLookupService {

    private static final Logger log = LoggerFactory.getLogger(BookIsbnLookupService.class);

    private static final String NO_ENCONTRADO =
            "No se pudo encontrar información de ese libro";
    private static final String ERROR_GOOGLE =
            "No se pudo encontrar información de ese libro";
    private static final Pattern ANIO_PATTERN = Pattern.compile("^(\\d{4})");
    private static final String CAMPO_COVER = "cover";
    private static final String CAMPO_MEDIUM = "medium";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String urlBase;
    private final String openLibraryUrlBase;
    private final GeminiClient geminiClient;

    public BookIsbnLookupService(
            @Value("${app.google-books.url-base}") String urlBase,
            @Value("${app.google-books.timeout-ms}") long timeoutMs,
            @Value("${app.open-library.url-base:https://openlibrary.org}") String openLibraryUrlBase,
            @Autowired(required = false) GeminiClient geminiClient) {
        this.urlBase = urlBase;
        this.openLibraryUrlBase = openLibraryUrlBase;
        this.objectMapper = new ObjectMapper();
        this.geminiClient = geminiClient;

        // Timeouts de conexión/lectura configurables por propiedad.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeoutMs);
        requestFactory.setReadTimeout((int) timeoutMs);

        // RestClient propio con timeouts configurables.
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Searches book Isbn Lookup data transfer object.
     *
     * @param isbn text value used to scope this book Isbn Lookup data transfer object
     * @return book Isbn Lookup data transfer object reflecting the state after the operation
     */

    public BookIsbnLookupDTO searchByIsbn(String isbn) {
        try {
            JsonNode volume = searchPrimerVolume(isbn);
            JsonNode volumeInfo = volume.path("volumeInfo");

            String author = null;
            JsonNode authors = volumeInfo.path("authors");
            if (authors.isArray() && authors.size() > 0) {
                author = authors.get(0).asText(null);
            }

            String title = volumeInfo.path("title").asText(null);
            String summary = volumeInfo.path("description").asText(null);
            Integer year = yearFrom(volumeInfo.path("publishedDate").asText(null));
            boolean cover = !volumeInfo.path("imageLinks").path("thumbnail").isMissingNode();
            String publisher = volumeInfo.path("publisher").asText(null);
            Integer numberPages = volumeInfo.has("pageCount") ? volumeInfo.path("pageCount").asInt() : null;

            // Solo título/resumen/año son requeridos: si falta el resumen se complementa con IA.
            if ((summary == null || summary.isBlank()) && geminiClient != null) {
                String iaSummary = generateSummaryViaIA(title, author, isbn);
                if (iaSummary != null && !iaSummary.isBlank()) summary = iaSummary;
            }

            return new BookIsbnLookupDTO(title, author, summary, year, cover, publisher, numberPages);
        } catch (EntityNotFoundException ex) {
            // Fallback a Open Library cuando Google no encuentra o limita (429).
            BookIsbnLookupDTO ol = searchOpenLibrary(isbn);
            if (ol != null) {
                // Si Open Library trae titulo pero sin resumen, complementar solo resumen con IA
                if ((ol.summary() == null || ol.summary().isBlank()) && geminiClient != null) {
                    String iaSummary = generateSummaryViaIA(ol.title(), ol.author(), isbn);
                    if (iaSummary != null && !iaSummary.isBlank()) {
                        return new BookIsbnLookupDTO(ol.title(), ol.author(), iaSummary, ol.yearPublication(), ol.coverAvailable(), ol.publisher(), ol.numberPages());
                    }
                }
                return ol;
            }
            throw ex;
        }
    }

    private BookIsbnLookupDTO searchOpenLibrary(String isbn) {
        try {
            String limpio = isbn.replace("-", "").replace(" ", "");
            String url = openLibraryUrlBase + "/api/books?bibkeys=ISBN:" + limpio + "&format=json&jscmd=data";
            String json = restClient.get().uri(url).retrieve().body(String.class);
            if (json == null || json.isBlank() || json.trim().equals("{}")) return null;
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("ISBN:" + limpio);
            if (data.isMissingNode() || data.isEmpty()) return null;
            String title = data.path("title").asText(null);
            String summary = null;
            JsonNode desc = data.path("description");
            if (desc.isTextual()) summary = desc.asText(null);
            else if (desc.isObject()) summary = desc.path("value").asText(null);
            if (summary == null) {
                JsonNode excerpts = data.path("excerpts");
                if (excerpts.isArray() && excerpts.size() > 0) summary = excerpts.get(0).path("text").asText(null);
            }
            String author = null;
            JsonNode authors = data.path("authors");
            if (authors.isArray() && authors.size() > 0) author = authors.get(0).path("name").asText(null);
            Integer year = null;
            String publishDate = data.path("publish_date").asText(null);
            if (publishDate != null) {
                Matcher m = ANIO_PATTERN.matcher(publishDate);
                if (m.find()) year = Integer.parseInt(m.group(1));
            }
            boolean cover = data.has(CAMPO_COVER) && !data.path(CAMPO_COVER).path(CAMPO_MEDIUM).isMissingNode();
            String publisher = null;
            JsonNode pubs = data.path("publishers");
            if (pubs.isArray() && pubs.size() > 0) publisher = pubs.get(0).path("name").asText(null);
            Integer numberPages = data.has("number_of_pages") ? data.path("number_of_pages").asInt() : null;
            if (title == null && summary == null && year == null) return null;
            log.info("Open Library fallback OK para ISBN {} -> {}", isbn, title);
            return new BookIsbnLookupDTO(title, author, summary, year, cover, publisher, numberPages);
        } catch (Exception e) {
            log.debug("Open Library fallback falló para ISBN {}", isbn, e);
            return null;
        }
    }

    private String generateSummaryViaIA(String title, String author, String isbn) {
        try {
            String promptSystem = "Eres bibliotecario. Genera un resumen breve (max 500 caracteres, español neutro) para el libro."
                    + " Si no lo conoces, responde vacio. Responde SOLO con el texto del resumen, sin JSON ni comillas extra.";
            String message = "ISBN: " + isbn + (title != null ? ", Titulo: " + title : "") + (author != null ? ", Autor: " + author : "");
            String resp = geminiClient.generateResponse(promptSystem, List.of(), message);
            if (resp == null || resp.isBlank() || resp.contains("No se pudo")) return null;
            return resp.trim();
        } catch (Exception e) {
            log.warn("Fallback IA resumen falló para ISBN {}", isbn, e);
            return null;
        }
    }

    /**
     * Retrieves cover image Imagen data transfer object.
     *
     * @param isbn text value used to scope this cover image Imagen data transfer object
     * @return cover image Imagen data transfer object reflecting the state after the operation
     * @throws EntityNotFoundException when the cover image Imagen data transfer object cannot be processed with the given input
     */

    public CoverImageDTO getCover(String isbn) {
        try {
            JsonNode volume = searchPrimerVolume(isbn);
            String thumbnail = volume.path("volumeInfo").path("imageLinks").path("thumbnail").asText(null);
            if (thumbnail != null) {
                byte[] bytes = restClient.get()
                        .uri(thumbnail)
                        .retrieve()
                        .body(byte[].class);
                return new CoverImageDTO(bytes, "image/jpeg");
            }
        } catch (EntityNotFoundException ex) {
            // Google no encontró o falló, se intenta el fallback
        }

        // Fallback Open Library
        try {
            String limpio = isbn.replace("-", "").replace(" ", "");
            String url = openLibraryUrlBase + "/api/books?bibkeys=ISBN:" + limpio + "&format=json&jscmd=data";
            String json = restClient.get().uri(url).retrieve().body(String.class);
            if (json != null && !json.isBlank() && !json.trim().equals("{}")) {
                JsonNode root = objectMapper.readTree(json);
                JsonNode data = root.path("ISBN:" + limpio);
                if (!data.isMissingNode() && !data.isEmpty()) {
                    if (data.has(CAMPO_COVER) && !data.path(CAMPO_COVER).path(CAMPO_MEDIUM).isMissingNode()) {
                        String coverUrl = data.path(CAMPO_COVER).path(CAMPO_MEDIUM).asText();
                        byte[] bytes = restClient.get()
                                .uri(coverUrl)
                                .retrieve()
                                .body(byte[].class);
                        return new CoverImageDTO(bytes, "image/jpeg");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Open Library fallback portada falló para ISBN {}", isbn, e);
        }

        throw new EntityNotFoundException(NO_ENCONTRADO);
    }

    // El ISBN admite guiones; Google Books espera solo dígitos, se limpian acá.
    private JsonNode searchPrimerVolume(String isbn) {
        String url = urlBase + "/volumes?q=isbn:" + isbn.replace("-", "");
        String json = null;
        try {
            json = restClient.get().uri(url).retrieve().body(String.class);
        } catch (Exception ex) {
            // 429 de Google (cuota): reintento una vez.
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
    private Integer yearFrom(String publishedDate) {
        if (publishedDate == null) {
            return null;
        }
        Matcher matcher = ANIO_PATTERN.matcher(publishedDate);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }
}