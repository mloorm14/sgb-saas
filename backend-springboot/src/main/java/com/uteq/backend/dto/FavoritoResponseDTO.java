package com.uteq.backend.dto;

import java.time.OffsetDateTime;

// No hay FavoritoRequestDTO: crear/eliminar un favorito solo necesita el
// libroId (viaja en el path, POST /favoritos/{libroId} /
// DELETE /favoritos/{libroId}) y el usuario autenticado (Authentication,
// mismo patrón que PrestamoController.renovar) -- no hay body que validar.
public record FavoritoResponseDTO(
        Long usuarioId,
        Long libroId,
        String tituloLibro,
        OffsetDateTime agregadoEn
) {}
