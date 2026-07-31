package com.uteq.backend.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;

// Serializable: el cache Redis "libros" (RedisConfig) usa serialización Java
// para sus valores (Page<LibroResponseDTO> incluido) en vez de JSON --
// PageImpl/PageRequest/Sort de Spring Data ya son Serializable, pero
// Jackson no puede reconstruir un PageImpl al leer de vuelta (no expone un
// constructor utilizable por Jackson), así que este DTO también necesita
// serlo para que todo el grafo del objeto cacheado sea serializable.
public record LibroResponseDTO(
        Long id,
        String titulo,
        String isbn,
        String resumen,
        String portadaUrl,
        Integer anioPublicacion,
        Integer editorialId,
        String editorial,
        Integer idiomaId,
        String idioma,
        Integer estadoId,
        String estado,
        Integer stockTotal,
        Integer stockDisponible,
        String ubicacionFisica,
        OffsetDateTime fechaRegistro
) implements Serializable {}
