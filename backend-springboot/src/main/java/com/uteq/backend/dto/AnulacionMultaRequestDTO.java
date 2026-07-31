package com.uteq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// NO contiene un campo rolEjecutor. p_rol_ejecutor se
// resuelve en MultaService.anular() a partir de las authorities REALES
// del Authentication -- nunca del body. Aceptar el rol desde el JSON
// permitiría que un BIBLIOTECARIO autenticado enviara
// {"rolEjecutor": "GERENTE"} y colara la anulación (sp_anular_multa
// valida el rol como defensa en profundidad, pero eso no reemplaza
// esta restricción en el backend).
public record AnulacionMultaRequestDTO(

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 255, message = "El motivo no puede superar 255 caracteres")
        String motivo
) {}