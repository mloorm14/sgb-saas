package com.uteq.backend.dto;

import java.util.List;

public record UsuarioResponseDTO(Long id, String nombre, String correo, List<String> roles) {
}
