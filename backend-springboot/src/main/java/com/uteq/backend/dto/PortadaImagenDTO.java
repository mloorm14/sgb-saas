package com.uteq.backend.dto;

// Resultado de LibroService.obtenerPortada (GET /api/v1/libros/{id}/portada):
// el binario de la portada junto al Content-Type con que debe servirse
// (portada_tipo, ej. image/png) para que el controller responda el header
// dinamico sin hacer una segunda consulta de metadata. Solo se construye en
// la capa de servicio, nunca se expone por JSON.
public record PortadaImagenDTO(
        byte[] bytes,
        String contentType
) {}