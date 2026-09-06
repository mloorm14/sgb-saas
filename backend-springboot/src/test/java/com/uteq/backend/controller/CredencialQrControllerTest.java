package com.uteq.backend.controller;

import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.CredencialQrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CredencialQrController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "lector@correo.com", roles = "LECTOR")
class CredencialQrControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CredencialQrService credencialQrService;

    @Test
    void miCredencial_devuelve200ConImagen() throws Exception {
        byte[] pngMock = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        when(credencialQrService.generarImagenQrPropio(any())).thenReturn(pngMock);

        mockMvc.perform(get("/api/v1/credencial-qr/mi-credencial")
                        .principal(new TestingAuthenticationToken("lector@correo.com", null, "ROLE_LECTOR")))
                .andExpect(status().isOk());
    }
}
