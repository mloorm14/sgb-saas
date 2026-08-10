package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.dto.MensajeChatRequestDTO;
import com.uteq.backend.dto.MensajeChatResponseDTO;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.ChatbotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Módulo H (chatbot): ChatbotController está restringido SOLO a LECTOR
 * (@PreAuthorize("hasRole('LECTOR')") en ambos endpoints), decisión
 * documentada en el propio controller. Mismo patrón de test que
 * LibroControllerSecurityTest: WebMvcTest + SecurityConfig real + MockMvc
 * reconstruido con springSecurity() para que @WithMockUser pueble el
 * SecurityContext de verdad.
 * <p>
 * DISCREPANCIA vs brief (documentada): el brief pedía el test
 * {@code enviarMensaje_sinAutenticar_retorna401}. En ESTE repositorio una
 * request no autenticada NO responde 401 sino 403: SecurityConfig no
 * configura un AuthenticationEntryPoint y Spring Security cae en el default
 * {@code Http403ForbiddenEntryPoint} (mismo comportamiento ya documentado en
 * LibroControllerSecurityTest). Se prioriza el patrón real del repo (regla 9
 * del brief) y el test verifica el rechazo real.
 */
@WebMvcTest(ChatbotController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class ChatbotControllerSecurityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void construirMockMvcConSeguridad() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ChatbotService chatbotService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    @WithMockUser(roles = "LECTOR")
    void enviarMensaje_conRolLector_retorna200() throws Exception {
        when(chatbotService.enviarMensaje(any(), any())).thenReturn(new MensajeChatResponseDTO(
                UUID.randomUUID(), "Respuesta", OffsetDateTime.now()));

        mockMvc.perform(post("/api/v1/chatbot/mensajes")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MensajeChatRequestDTO(null, "hola"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void enviarMensaje_conRolBibliotecario_retorna403() throws Exception {
        mockMvc.perform(post("/api/v1/chatbot/mensajes")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MensajeChatRequestDTO(null, "hola"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void enviarMensaje_conRolGerente_retorna403() throws Exception {
        mockMvc.perform(post("/api/v1/chatbot/mensajes")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MensajeChatRequestDTO(null, "hola"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void enviarMensaje_sinAutenticar_esRechazado() throws Exception {
        // Ver DISCREPANCIA en el Javadoc de la clase: el repo devuelve 403
        // (Http403ForbiddenEntryPoint) para no autenticado, no 401.
        mockMvc.perform(post("/api/v1/chatbot/mensajes")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MensajeChatRequestDTO(null, "hola"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void historial_conRolLector_retorna200() throws Exception {
        when(chatbotService.obtenerHistorial(any(), any())).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/chatbot/sesiones/{id}/historial", UUID.randomUUID()))
                .andExpect(status().isOk());
    }
}
