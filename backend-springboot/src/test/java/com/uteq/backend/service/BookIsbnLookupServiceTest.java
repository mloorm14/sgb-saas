package com.uteq.backend.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// HTTP 100% mockeado con un stub HTTP en proceso (com.sun.net.httpserver,
// puerto aleatorio): NUNCA hay llamadas reales a Google Books.
class BookIsbnLookupServiceTest {

    private HttpServer server;
    private BookIsbnLookupService service;
    private String urlBase;
    private final AtomicReference<String> lastQuery = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        urlBase = "http://127.0.0.1:" + server.getAddress().getPort() + "/books/v1";

        server.createContext("/books/v1/volumes", this::answerVolumes);
        server.createContext("/books/v1/thumb.jpg", exchange -> {
            byte[] body = new byte[]{9, 8, 7};
            exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/books/v1/falla", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.createContext("/api/books", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        service = new BookIsbnLookupService(urlBase, 8000L, urlBase.replace("/books/v1", ""), null);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // Router por ISBN: los ISBN "fantasma" simulan los casos sin resultados.
    private void answerVolumes(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        lastQuery.set(query);
        String json;
        if (query.contains("0000000000000")) {
            json = "{\"totalItems\":0}";
        } else if (query.contains("1111111111111")) {
            json = "{\"totalItems\":1}";
        } else if (query.contains("2222222222222")) {
            // Google Books caído: el servicio traduce el 500 a 404.
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        } else if (query.contains("3333333333333")) {
            json = "{\"kind\":\"books#volume\",\"totalItems\":1,\"items\":[{\"volumeInfo\":{\"title\":\"Sin portada\"}}]}";
        } else {
            json = """
                    {"kind":"books#volume","totalItems":1,"items":[{"volumeInfo":{
                    "title":"Clean Code","authors":["Robert C. Martin"],
                    "description":"resumen largo","publishedDate":"2008-06-19",
                    "imageLinks":{"thumbnail":"%s/thumb.jpg"}}}]}
                    """.formatted(urlBase);
        }
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Test
    void searchByIsbn_withResult_mapeaTodosFields() {
        var dto = service.searchByIsbn("9780132350884");

        assertThat(dto.title()).isEqualTo("Clean Code");
        assertThat(dto.author()).isEqualTo("Robert C. Martin");
        assertThat(dto.summary()).isEqualTo("resumen largo");
        assertThat(dto.yearPublication()).isEqualTo(2008); // "2008-06-19" -> 2008
        assertThat(dto.coverAvailable()).isTrue();
    }

    @Test
    void searchByIsbn_withGuiones_limpiaIsbnForGoogleBooks() {
        service.searchByIsbn("978-0132350884");

        assertThat(lastQuery.get()).isEqualTo("q=isbn:9780132350884");
    }

    @Test
    void searchByIsbn_withoutResults_lanzaEntityNotFound() {
        assertThatThrownBy(() -> service.searchByIsbn("0000000000000"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void searchByIsbn_withoutItems_lanzaEntityNotFound() {
        assertThatThrownBy(() -> service.searchByIsbn("1111111111111"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void searchByIsbn_googleBooksCaido_tambienEs404() {
        assertThatThrownBy(() -> service.searchByIsbn("2222222222222"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getCover_descargaThumbnailComoBytes() {
        var cover = service.getCover("9780132350884");

        assertThat(cover.bytes()).containsExactly(9, 8, 7);
        assertThat(cover.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void getCover_withoutThumbnail_lanzaEntityNotFound() {
        assertThatThrownBy(() -> service.getCover("3333333333333"))
                .isInstanceOf(EntityNotFoundException.class);
    }
}