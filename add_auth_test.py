import os

base = 'backend-springboot/src/test/java/com/uteq/backend/controller/'

test_methods = """
    @Test
    void reenviarCodigo_devuelve204() throws Exception {
        com.uteq.backend.dto.ReenviarCodigoRequestDTO dto = new com.uteq.backend.dto.ReenviarCodigoRequestDTO("test@correo.com");
        doNothing().when(authService).reenviarCodigo(anyString());

        mockMvc.perform(post("/api/auth/reenviar-codigo")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());
    }

    @Test
    void solicitarReset_devuelve204() throws Exception {
        com.uteq.backend.dto.SolicitarResetRequestDTO dto = new com.uteq.backend.dto.SolicitarResetRequestDTO("test@correo.com");
        doNothing().when(authService).solicitarReset(anyString());

        mockMvc.perform(post("/api/auth/solicitar-reset")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());
    }

    @Test
    void reset_devuelve204() throws Exception {
        com.uteq.backend.dto.ResetPasswordRequestDTO dto = new com.uteq.backend.dto.ResetPasswordRequestDTO("test@correo.com", "123456", "Nueva123!");
        doNothing().when(authService).resetPassword(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/auth/reset")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());
    }

    @Test
    void verificarCorreo_devuelve200() throws Exception {
        com.uteq.backend.dto.CodigoVerificacionRequestDTO dto = new com.uteq.backend.dto.CodigoVerificacionRequestDTO("test@correo.com", "123456");
        when(authService.verificarCorreo(anyString(), anyString(), anyString())).thenReturn(new com.uteq.backend.dto.UsuarioResponseDTO(1L, "Juan", "Perez", "test@correo.com", "LECTOR", "ACTIVO"));

        mockMvc.perform(post("/api/auth/verificar-correo")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }
"""

path = base + 'AuthControllerTest.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.rstrip().removesuffix('}')

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content + test_methods + "}\n")

print("Added tests to AuthControllerTest")
