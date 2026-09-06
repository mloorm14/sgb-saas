import os

base = 'backend-springboot/src/test/java/com/uteq/backend/controller/'

test_controller_test = """package com.uteq.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.uteq.backend.exception.GlobalExceptionHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TestControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protegido_devuelve200() throws Exception {
        mockMvc.perform(get("/api/test/protegido"))
                .andExpect(status().isOk());
    }
}
"""

with open(base + 'TestControllerTest.java', 'w', encoding='utf-8', newline='\n') as f:
    f.write(test_controller_test)

with open(base + 'RespaldoCompletoControllerTest.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Quitar la llave final
content = content.rstrip().removesuffix('}')

trigger_test = """
    @Test
    void triggerBackupCompleto_devuelve503SiNodeNoResponde() throws Exception {
        mockMvc.perform(post("/api/v1/admin/respaldo-completo/trigger")
                        .principal(new org.springframework.security.authentication.TestingAuthenticationToken("admin@correo.com", null, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable());
    }
}
"""

with open(base + 'RespaldoCompletoControllerTest.java', 'w', encoding='utf-8', newline='\n') as f:
    f.write(content + trigger_test)
