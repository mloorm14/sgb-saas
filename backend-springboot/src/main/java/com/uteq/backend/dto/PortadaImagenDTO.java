package com.uteq.backend.dto;

import java.util.Arrays;
import java.util.Objects;

// Resultado de LibroService.obtenerPortada (GET /api/v1/libros/{id}/portada):
// el binario de la portada junto al Content-Type con que debe servirse
// (portada_tipo, ej. image/png) para que el controller responda el header
// dinamico sin hacer una segunda consulta de metadata. Solo se construye en
// la capa de servicio, nunca se expone por JSON.
//
// equals()/hashCode()/toString() explícitos: el record compararía el
// campo byte[] por referencia; con Arrays se compara por contenido.
public record PortadaImagenDTO(
        byte[] bytes,
        String contentType
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PortadaImagenDTO otro)) return false;
        return Arrays.equals(bytes, otro.bytes) && Objects.equals(contentType, otro.contentType);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(bytes) + Objects.hashCode(contentType);
    }

    @Override
    public String toString() {
        return "PortadaImagenDTO[bytes=" + Arrays.toString(bytes) + ", contentType=" + contentType + "]";
    }
}
