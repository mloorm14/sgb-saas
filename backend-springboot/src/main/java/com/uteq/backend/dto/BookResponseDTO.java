package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

// Serializable: el cache Redis "libros" (RedisConfig) usa serialización Java
// para sus valores (Page<LibroResponseDTO> incluido) en vez de JSON --
// PageImpl/PageRequest/Sort de Spring Data ya son Serializable, pero
// Jackson no puede reconstruir un PageImpl al leer de vuelta (no expone un
// constructor utilizable por Jackson), así que este DTO también necesita
// serlo para que todo el grafo del objeto cacheado sea serializable.
public record BookResponseDTO(
        Long id, @JsonProperty("titulo") String title,
        String isbn, @JsonProperty("resumen") String summary, @JsonProperty("portadaUrl") String coverUrl,
        // Portada binaria (V13__portada_imagen.sql): solo metadata, NUNCA
        // el byte[] -- el binario se sirve aparte por
        // GET /api/v1/libros/{id}/portada con su Content-Type dinámico.
        // tienePortada es true cuando portadaImagen != null (el frontend
        // decide con esto si mostrarla o pedirla al endpoint).
        Boolean tieneCover, @JsonProperty("portadaNombre") String coverName, @JsonProperty("portadaTipo") String coverType, @JsonProperty("anioPublicacion") Integer yearPublication, @JsonProperty("numeroPaginas") Integer numberPages, @JsonProperty("precioBase") java.math.BigDecimal priceBase, @JsonProperty("editorialId") Integer publisherId, @JsonProperty("editorial") String publisher, @JsonProperty("idiomaId") Integer languageId, @JsonProperty("idioma") String language, @JsonProperty("estadoId") Integer statusId, @JsonProperty("estado") String status,
        Integer stockTotal, @JsonProperty("stockDisponible") Integer stockAvailable,
        @JsonProperty("ubicacionFisica") String locationPhysical, @JsonProperty("fechaRegistro") OffsetDateTime dateRegistration,
        // Solo nombres de categorías/autores (@JsonProperty("autores") List<String> serializable para el cache).
        List<String> categories, List<String> authors,
        // Proveedor opcional — null = S/P
        Integer supplierId,
        @JsonProperty("proveedor") String supplier
) implements Serializable {}
