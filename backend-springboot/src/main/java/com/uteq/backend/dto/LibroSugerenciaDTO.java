package com.uteq.backend.dto;

import java.io.Serializable;

// Versión ligera de LibroResponseDTO para el endpoint de autocompletado
// (GET /libros/sugerencias): no expone el objeto completo (editorial,
// idioma, stock, fechas...) en cada tecla presionada por el usuario en el
// frontend, solo lo mínimo para mostrar la lista desplegable.
// Serializable: se cachea en Redis con el mismo serializador Java estándar
// que "libros" (ver RedisConfig -- JdkSerializationRedisSerializer por
// defecto, no JSON), así que necesita el mismo tratamiento que
// LibroResponseDTO para completar el grafo de serialización.
public record LibroSugerenciaDTO(
        Long id,
        String titulo,
        Boolean disponible
) implements Serializable {}
