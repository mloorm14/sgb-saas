import os

path = 'backend-springboot/src/test/java/com/uteq/backend/controller/SugerenciaAdquisicionControllerSecurityTest.java'

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.rstrip().removesuffix('}')

test = """
    @Test
    @WithMockUser(roles = "LECTOR")
    void listarPropias_conRolLector_sePermite() throws Exception {
        org.springframework.data.domain.Page<com.uteq.backend.dto.SugerenciaAdquisicionResponseDTO> page = new org.springframework.data.domain.PageImpl<>(java.util.List.of());
        when(sugerenciaAdquisicionService.listarPropias(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/sugerencias-adquisicion/mias"))
                .andExpect(status().isOk());
    }
}
"""

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content + test)
print("Done")
