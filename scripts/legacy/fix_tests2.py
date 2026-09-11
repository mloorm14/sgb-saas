import os, sys

base = 'backend-springboot/src/test/java/com/uteq/backend/controller/'

contents = {
    'PublicoCategoriaControllerTest.java': (
        "package com.uteq.backend.controller;\n\n"
        "import com.uteq.backend.entity.Categoria;\n"
        "import com.uteq.backend.exception.GlobalExceptionHandler;\n"
        "import com.uteq.backend.repository.CategoriaRepository;\n"
        "import org.junit.jupiter.api.Test;\n"
        "import org.springframework.beans.factory.annotation.Autowired;\n"
        "import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;\n"
        "import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;\n"
        "import org.springframework.context.annotation.Import;\n"
        "import org.springframework.test.context.bean.override.mockito.MockitoBean;\n"
        "import org.springframework.test.web.servlet.MockMvc;\n"
        "import java.util.List;\n"
        "import static org.mockito.Mockito.when;\n"
        "import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;\n"
        "import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;\n"
        "import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;\n\n"
        "@WebMvcTest(PublicoCategoriaController.class)\n"
        "@AutoConfigureMockMvc(addFilters = false)\n"
        "@Import(GlobalExceptionHandler.class)\n"
        "class PublicoCategoriaControllerTest extends WebMvcControllerTestSupport {\n\n"
        "    @Autowired\n"
        "    private MockMvc mockMvc;\n\n"
        "    @MockitoBean\n"
        "    private CategoriaRepository categoriaRepository;\n\n"
        "    @Test\n"
        "    void listar_devuelve200ConLista() throws Exception {\n"
        "        Categoria c = new Categoria();\n"
        "        c.setId(1L);\n"
        '        c.setNombre("Novela");\n'
        "        when(categoriaRepository.findAll()).thenReturn(List.of(c));\n\n"
        '        mockMvc.perform(get("/api/publico/categorias"))\n'
        "                .andExpect(status().isOk())\n"
        '                .andExpect(jsonPath("$[0].id").value(1))\n'
        '                .andExpect(jsonPath("$[0].nombre").value("Novela"));\n'
        "    }\n\n"
        "    @Test\n"
        "    void listar_vacio_devuelve200() throws Exception {\n"
        "        when(categoriaRepository.findAll()).thenReturn(List.of());\n\n"
        '        mockMvc.perform(get("/api/publico/categorias"))\n'
        "                .andExpect(status().isOk())\n"
        '                .andExpect(jsonPath("$").isEmpty());\n'
        "    }\n"
        "}\n"
    )
}

for fname, content in contents.items():
    path = base + fname
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(content)
    print('Written: ' + fname)
