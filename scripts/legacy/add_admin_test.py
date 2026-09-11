import os

base = 'backend-springboot/src/test/java/com/uteq/backend/controller/'

test_method = """
    @Test
    @WithMockUser(roles = "ADMIN")
    void eliminar_conRolAdmin_sePermite() throws Exception {
        org.mockito.Mockito.doNothing().when(usuarioAdminService).eliminarUsuario(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/admin/usuarios/1").param("motivo", "test"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
    }
}
"""

path = base + 'UsuarioAdminControllerSecurityTest.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.rstrip().removesuffix('}')

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content + test_method)

print("Added admin eliminar test to UsuarioAdminControllerSecurityTest")
