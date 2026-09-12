package com.uteq.backend.controller;

import com.uteq.backend.chatbot.ChatbotOrchestrator;
import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.dto.MessageChatRequestDTO;
import com.uteq.backend.dto.MessageChatResponseDTO;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
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
    void construirMockMvcWithSeguridad() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ChatbotOrchestrator chatbotOrchestrator;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    @WithMockUser(roles = "LECTOR")
    void sendMessage_withRoleReader_retorna200() throws Exception {
        when(chatbotOrchestrator.sendMessage(any(), any())).thenReturn(new MessageChatResponseDTO(
                UUID.randomUUID(), "Respuesta", OffsetDateTime.now()));

        mockMvc.perform(post("/api/v1/chatbot/mensajes")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MessageChatRequestDTO(null, "hola"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void sendMessage_withRoleLibrarian_retorna403() throws Exception {
        mockMvc.perform(post("/api/v1/chatbot/mensajes")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MessageChatRequestDTO(null, "hola"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void sendMessage_withRoleManager_retorna403() throws Exception {
        mockMvc.perform(post("/api/v1/chatbot/mensajes")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MessageChatRequestDTO(null, "hola"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void sendMessage_withoutAutenticar_esRejected() throws Exception {
        // Ver DISCREPANCIA en el Javadoc de la clase: el repo devuelve 403
        // (Http403ForbiddenEntryPoint) para no autenticado, no 401.
        mockMvc.perform(post("/api/v1/chatbot/mensajes")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MessageChatRequestDTO(null, "hola"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void history_withRoleReader_retorna200() throws Exception {
        when(chatbotOrchestrator.getHistory(any(), any())).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/chatbot/sesiones/{id}/historial", UUID.randomUUID()))
                .andExpect(status().isOk());
    }
}
