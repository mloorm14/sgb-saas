package com.uteq.backend.dto;
import com.fasterxml.jackson.annotation.JsonProperty;


// Respuesta de GET /api/v1/libros/lookup-isbn?isbn= (Google Books).
// anioPublicacion puede ser null si Google Books no trae fecha; la
// portada NO viaja acá (se descarga aparte por /lookup-isbn/portada).
public record BookIsbnLookupDTO( @JsonProperty("titulo") String title, @JsonProperty("autor") String author, @JsonProperty("resumen") String summary, @JsonProperty("anioPublicacion") Integer yearPublication, @JsonProperty("portadaDisponible") Boolean coverAvailable, @JsonProperty("editorial") String publisher,
        @JsonProperty("numeroPaginas") Integer numberPages
) {}