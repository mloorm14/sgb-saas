package com.uteq.backend.service;

import com.uteq.backend.dto.BookSuggestionDTO;
import com.uteq.backend.dto.MessageChatHistoryDTO;
import com.uteq.backend.dto.MessageChatRequestDTO;
import com.uteq.backend.dto.MessageChatResponseDTO;
import com.uteq.backend.entity.KnowledgeBase;
import com.uteq.backend.entity.MessageChat;
import com.uteq.backend.entity.SessionChat;
import com.uteq.backend.entity.User;
import com.uteq.backend.integration.GeminiClient;
import com.uteq.backend.repository.BaseKnowledgeRepository;
import com.uteq.backend.repository.MessageChatRepository;
import com.uteq.backend.repository.SessionChatRepository;
import com.uteq.backend.repository.UserRepository;
import com.uteq.backend.security.ChatbotRateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceTest {

    private static final String CORREO = "lector@correo.com";
    private static final String MENSAJE_FALLBACK_GEMINI =
            "El asistente está saturado, intenta en unos segundos.";

    @Mock SessionChatRepository sessionChatRepo;
    @Mock MessageChatRepository messageChatRepo;
    @Mock BaseKnowledgeRepository baseKnowledgeRepo;
    @Mock UserRepository userRepo;
    @Mock BookService bookService;
    @Mock ReservationService reservationService;
    @Mock GeminiClient geminiClient;
    @Mock ChatbotRateLimiter chatbotRateLimiter;

    @InjectMocks ChatbotService chatbotService;

    // ── Test 1: sesión nueva ────────────────────────────────
    @Test
    void sendMessage_sessionFresh_creaSessionYPersisteAmbosMensajes() {
        Authentication auth = authComoReader();
        prepararUserReader();
        given(chatbotRateLimiter.estaBlocked(1L)).willReturn(false);
        given(sessionChatRepo.save(any(SessionChat.class))).willAnswer(inv -> {
            SessionChat s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        given(messageChatRepo.save(any(MessageChat.class))).willAnswer(inv -> inv.getArgument(0));
        given(baseKnowledgeRepo.findByActiveTrue()).willReturn(List.of());
        given(messageChatRepo.findBySessionIdOrderByCreatedAsc(any(UUID.class))).willReturn(List.of());
        given(geminiClient.generateResponse(anyString(), any(), anyString())).willReturn("Hola, ¿en qué te ayudo?");

        MessageChatResponseDTO result = chatbotService.sendMessage(
                new MessageChatRequestDTO(null, "¿Cuál es el horario?"), auth);

        assertThat(result.sessionId()).isNotNull();
        assertThat(result.response()).isEqualTo("Hola, ¿en qué te ayudo?");
        // save x2: la creación de la sesión en resolverSesion + la
        // actualización de ultima_actividad al final de enviarMensaje.
        verify(sessionChatRepo, org.mockito.Mockito.times(2)).save(any(SessionChat.class));
        verify(messageChatRepo).save(argRole("USUARIO"));
        verify(messageChatRepo).save(argRole("ASISTENTE"));
        verify(chatbotRateLimiter).registerMessage(1L);
    }

    // ── Test 2: sesión existente propia ─────────────────────
    @Test
    void sendMessage_sessionExistingOwn_reutilizaSessionYActualizaLastActividad() {
        Authentication auth = authComoReader();
        prepararUserReader();
        given(chatbotRateLimiter.estaBlocked(1L)).willReturn(false);
        UUID sessionId = UUID.randomUUID();
        given(sessionChatRepo.findById(sessionId)).willReturn(Optional.of(sessionUser(sessionId, 1L)));
        given(messageChatRepo.save(any(MessageChat.class))).willAnswer(inv -> inv.getArgument(0));
        given(baseKnowledgeRepo.findByActiveTrue()).willReturn(List.of());
        given(messageChatRepo.findBySessionIdOrderByCreatedAsc(sessionId)).willReturn(List.of());
        given(geminiClient.generateResponse(anyString(), any(), anyString())).willReturn("Respuesta");

        MessageChatResponseDTO result = chatbotService.sendMessage(
                new MessageChatRequestDTO(sessionId, "¿Hay multas?"), auth);

        assertThat(result.sessionId()).isEqualTo(sessionId);
        // Reutiliza la sesión existente: el save que sí ocurre es la
        // actualización de ultima_actividad sobre ESA misma sesión, no la
        // creación de una nueva.
        ArgumentCaptor<SessionChat> captorSession = ArgumentCaptor.forClass(SessionChat.class);
        verify(sessionChatRepo).save(captorSession.capture());
        assertThat(captorSession.getValue().getId()).isEqualTo(sessionId);
        assertThat(captorSession.getValue().getLastActividad()).isAfter(
                captorSession.getValue().getCreated());
        verify(messageChatRepo).save(argRole("USUARIO"));
        verify(messageChatRepo).save(argRole("ASISTENTE"));
    }

    // ── Test 3: sesión de otro usuario ──────────────────────
    @Test
    void sendMessage_sessionOtroUser_lanzaSessionChatNotFound() {
        Authentication auth = authComoReader();
        prepararUserReader();
        given(chatbotRateLimiter.estaBlocked(1L)).willReturn(false);
        UUID sessionId = UUID.randomUUID();
        given(sessionChatRepo.findById(sessionId)).willReturn(Optional.of(sessionUser(sessionId, 2L)));

        assertThatThrownBy(() -> chatbotService.sendMessage(
                new MessageChatRequestDTO(sessionId, "hola"), auth))
                .isInstanceOf(SessionChatNotFoundException.class);
        verify(messageChatRepo, never()).save(any(MessageChat.class));
    }

    // ── Test 4: rate limit excedido ─────────────────────────
    @Test
    void sendMessage_rateLimitExceeded_lanzaChatbotRateLimitExceeded() {
        Authentication auth = authComoReader();
        prepararUserReader();
        given(chatbotRateLimiter.estaBlocked(1L)).willReturn(true);

        assertThatThrownBy(() -> chatbotService.sendMessage(
                new MessageChatRequestDTO(null, "hola"), auth))
                .isInstanceOf(ChatbotRateLimitExceededException.class);
        verify(geminiClient, never()).generateResponse(anyString(), any(), anyString());
        verify(sessionChatRepo, never()).save(any(SessionChat.class));
    }

    // ── Test 5: grounding de disponibilidad ─────────────────
    @Test
    void sendMessage_textConsultaAvailability_incluyeResultsBookServiceContexto() {
        Authentication auth = authComoReader();
        prepararUserReader();
        given(chatbotRateLimiter.estaBlocked(1L)).willReturn(false);
        given(sessionChatRepo.save(any(SessionChat.class))).willAnswer(inv -> {
            SessionChat s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        given(messageChatRepo.save(any(MessageChat.class))).willAnswer(inv -> inv.getArgument(0));
        given(baseKnowledgeRepo.findByActiveTrue()).willReturn(List.of());
        given(messageChatRepo.findBySessionIdOrderByCreatedAsc(any(UUID.class))).willReturn(List.of());
        given(bookService.sugerir(anyString())).willReturn(
                List.of(new BookSuggestionDTO(10L, "Clean Code", true)));
        given(geminiClient.generateResponse(anyString(), any(), anyString())).willReturn("Sí está disponible.");

        chatbotService.sendMessage(
                new MessageChatRequestDTO(null, "¿hay disponible el libro Clean Code?"), auth);

        ArgumentCaptor<String> captorPrompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateResponse(captorPrompt.capture(), any(), anyString());
        assertThat(captorPrompt.getValue())
                .contains("Clean Code")
                .contains("disponible=true");
        verify(bookService).sugerir(anyString());
    }

    // ── Test 6: fallback de Gemini no rompe el flujo ────────
    @Test
    void sendMessage_geminiClientDevuelveMessageFallback_seGuardaComoResponseAsistente() {
        Authentication auth = authComoReader();
        prepararUserReader();
        given(chatbotRateLimiter.estaBlocked(1L)).willReturn(false);
        given(sessionChatRepo.save(any(SessionChat.class))).willAnswer(inv -> {
            SessionChat s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        given(messageChatRepo.save(any(MessageChat.class))).willAnswer(inv -> inv.getArgument(0));
        given(baseKnowledgeRepo.findByActiveTrue()).willReturn(List.of());
        given(messageChatRepo.findBySessionIdOrderByCreatedAsc(any(UUID.class))).willReturn(List.of());
        given(geminiClient.generateResponse(anyString(), any(), anyString()))
                .willReturn(MENSAJE_FALLBACK_GEMINI);

        MessageChatResponseDTO result = chatbotService.sendMessage(
                new MessageChatRequestDTO(null, "hola"), auth);

        assertThat(result.response()).isEqualTo(MENSAJE_FALLBACK_GEMINI);
        verify(messageChatRepo).save(argRole("ASISTENTE"));
        verify(chatbotRateLimiter).registerMessage(1L);
    }

    // ── Test 7: historial propio ordenado ───────────────────
    @Test
    void getHistory_sessionOwn_retornaMensajesOrdenados() {
        Authentication auth = authComoReader();
        prepararUserReader();
        UUID sessionId = UUID.randomUUID();
        given(sessionChatRepo.findById(sessionId)).willReturn(Optional.of(sessionUser(sessionId, 1L)));
        MessageChat primero = message("USUARIO", "¿hay libros?", OffsetDateTime.now().minusMinutes(2));
        MessageChat second = message("ASISTENTE", "Sí, revisa el catálogo", OffsetDateTime.now());
        given(messageChatRepo.findBySessionIdOrderByCreatedAsc(sessionId)).willReturn(List.of(primero, second));

        List<MessageChatHistoryDTO> history = chatbotService.getHistory(sessionId, auth);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).role()).isEqualTo("USUARIO");
        assertThat(history.get(1).role()).isEqualTo("ASISTENTE");
        assertThat(history.get(1).content()).isEqualTo("Sí, revisa el catálogo");
    }

    // ── Test 8: historial de sesión ajena ───────────────────
    @Test
    void getHistory_sessionAjena_lanzaSessionChatNotFound() {
        Authentication auth = authComoReader();
        prepararUserReader();
        UUID sessionId = UUID.randomUUID();
        given(sessionChatRepo.findById(sessionId)).willReturn(Optional.of(sessionUser(sessionId, 2L)));

        assertThatThrownBy(() -> chatbotService.getHistory(sessionId, auth))
                .isInstanceOf(SessionChatNotFoundException.class);
        verify(messageChatRepo, never()).findBySessionIdOrderByCreatedAsc(any(UUID.class));
    }

    // ── Helpers ────────────────────────────────────────────
    private Authentication authComoReader() {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(CORREO);
        return auth;
    }

    private void prepararUserReader() {
        User user = new User();
        user.setId(1L);
        given(userRepo.findByEmail(CORREO)).willReturn(Optional.of(user));
    }

    private SessionChat sessionUser(UUID id, Long userId) {
        SessionChat s = new SessionChat();
        s.setId(id);
        s.setUserId(userId);
        s.setCreated(OffsetDateTime.now().minusHours(1));
        s.setLastActividad(OffsetDateTime.now().minusMinutes(1));
        return s;
    }

    private MessageChat message(String role, String content, OffsetDateTime created) {
        MessageChat m = new MessageChat();
        m.setSessionId(UUID.randomUUID());
        m.setRole(role);
        m.setContent(content);
        m.setCreated(created);
        return m;
    }

    private MessageChat argRole(String role) {
        return org.mockito.ArgumentMatchers.argThat(m -> m instanceof MessageChat
                && role.equals(((MessageChat) m).getRole()));
    }
}
