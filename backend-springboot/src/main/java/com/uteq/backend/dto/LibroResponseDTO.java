package com.uteq.backend.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

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
        // Portada binaria (V13__portada_imagen.sql): solo metadata, NUNCA
        // el byte[] -- el binario se sirve aparte por
        // GET /api/v1/libros/{id}/portada con su Content-Type dinámico.
        // tienePortada es true cuando portadaImagen != null (el frontend
        // decide con esto si mostrarla o pedirla al endpoint).
        Boolean tienePortada,
        String portadaNombre,
        String portadaTipo,
        Integer anioPublicacion,
        Integer numeroPaginas,
        java.math.BigDecimal precioBase,
        Integer editorialId,
        String editorial,
        Integer idiomaId,
        String idioma,
        Integer estadoId,
        String estado,
        Integer stockTotal,
        Integer stockDisponible,
        String ubicacionFisica,
        OffsetDateTime fechaRegistro,
        // Solo nombres de categorías/autores (List<String> serializable para el cache).
        List<String> categorias,
        List<String> autores,
        // Proveedor opcional — null = S/P
        Integer proveedorId,
        String proveedor
) implements Serializable {}
