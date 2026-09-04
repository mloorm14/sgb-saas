package com.uteq.backend.service;

import com.uteq.backend.dto.LibroSugerenciaDTO;
import com.uteq.backend.dto.MensajeChatHistorialDTO;
import com.uteq.backend.dto.MensajeChatRequestDTO;
import com.uteq.backend.dto.MensajeChatResponseDTO;
import com.uteq.backend.entity.BaseConocimiento;
import com.uteq.backend.entity.MensajeChat;
import com.uteq.backend.entity.SesionChat;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.integration.GeminiClient;
import com.uteq.backend.repository.BaseConocimientoRepository;
import com.uteq.backend.repository.MensajeChatRepository;
import com.uteq.backend.repository.SesionChatRepository;
import com.uteq.backend.repository.UsuarioRepository;
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

    @Mock SesionChatRepository sesionChatRepo;
    @Mock MensajeChatRepository mensajeChatRepo;
    @Mock BaseConocimientoRepository baseConocimientoRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock LibroService libroService;
    @Mock ReservacionService reservacionService;
    @Mock GeminiClient geminiClient;
    @Mock ChatbotRateLimiter chatbotRateLimiter;

    @InjectMocks ChatbotService chatbotService;

    // ── Test 1: sesión nueva ────────────────────────────────
    @Test
    void enviarMensaje_sesionNueva_creaSesionYPersisteAmbosMensajes() {
        Authentication auth = authComoLector();
        prepararUsuarioLector();
        given(chatbotRateLimiter.estaBloqueado(1L)).willReturn(false);
        given(sesionChatRepo.save(any(SesionChat.class))).willAnswer(inv -> {
            SesionChat s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        given(mensajeChatRepo.save(any(MensajeChat.class))).willAnswer(inv -> inv.getArgument(0));
        given(baseConocimientoRepo.findByActivoTrue()).willReturn(List.of());
        given(mensajeChatRepo.findBySesionIdOrderByCreadoEnAsc(any(UUID.class))).willReturn(List.of());
        given(geminiClient.generarRespuesta(anyString(), any(), anyString())).willReturn("Hola, ¿en qué te ayudo?");

        MensajeChatResponseDTO resultado = chatbotService.enviarMensaje(
                new MensajeChatRequestDTO(null, "¿Cuál es el horario?"), auth);

        assertThat(resultado.sesionId()).isNotNull();
        assertThat(resultado.respuesta()).isEqualTo("Hola, ¿en qué te ayudo?");
        // save x2: la creación de la sesión en resolverSesion + la
        // actualización de ultima_actividad al final de enviarMensaje.
        verify(sesionChatRepo, org.mockito.Mockito.times(2)).save(any(SesionChat.class));
        verify(mensajeChatRepo).save(argRol("USUARIO"));
        verify(mensajeChatRepo).save(argRol("ASISTENTE"));
        verify(chatbotRateLimiter).registrarMensaje(1L);
    }

    // ── Test 2: sesión existente propia ─────────────────────
    @Test
    void enviarMensaje_sesionExistentePropia_reutilizaSesionYActualizaUltimaActividad() {
        Authentication auth = authComoLector();
        prepararUsuarioLector();
        given(chatbotRateLimiter.estaBloqueado(1L)).willReturn(false);
        UUID sesionId = UUID.randomUUID();
        given(sesionChatRepo.findById(sesionId)).willReturn(Optional.of(sesionDeUsuario(sesionId, 1L)));
        given(mensajeChatRepo.save(any(MensajeChat.class))).willAnswer(inv -> inv.getArgument(0));
        given(baseConocimientoRepo.findByActivoTrue()).willReturn(List.of());
        given(mensajeChatRepo.findBySesionIdOrderByCreadoEnAsc(sesionId)).willReturn(List.of());
        given(geminiClient.generarRespuesta(anyString(), any(), anyString())).willReturn("Respuesta");

        MensajeChatResponseDTO resultado = chatbotService.enviarMensaje(
                new MensajeChatRequestDTO(sesionId, "¿Hay multas?"), auth);

        assertThat(resultado.sesionId()).isEqualTo(sesionId);
        // Reutiliza la sesión existente: el save que sí ocurre es la
        // actualización de ultima_actividad sobre ESA misma sesión, no la
        // creación de una nueva.
        ArgumentCaptor<SesionChat> captorSesion = ArgumentCaptor.forClass(SesionChat.class);
        verify(sesionChatRepo).save(captorSesion.capture());
        assertThat(captorSesion.getValue().getId()).isEqualTo(sesionId);
        assertThat(captorSesion.getValue().getUltimaActividad()).isAfter(
                captorSesion.getValue().getCreadoEn());
        verify(mensajeChatRepo).save(argRol("USUARIO"));
        verify(mensajeChatRepo).save(argRol("ASISTENTE"));
    }

    // ── Test 3: sesión de otro usuario ──────────────────────
    @Test
    void enviarMensaje_sesionDeOtroUsuario_lanzaSesionChatNoEncontrada() {
        Authentication auth = authComoLector();
        prepararUsuarioLector();
        given(chatbotRateLimiter.estaBloqueado(1L)).willReturn(false);
        UUID sesionId = UUID.randomUUID();
        given(sesionChatRepo.findById(sesionId)).willReturn(Optional.of(sesionDeUsuario(sesionId, 2L)));

        assertThatThrownBy(() -> chatbotService.enviarMensaje(
                new MensajeChatRequestDTO(sesionId, "hola"), auth))
                .isInstanceOf(SesionChatNoEncontradaException.class);
        verify(mensajeChatRepo, never()).save(any(MensajeChat.class));
    }

    // ── Test 4: rate limit excedido ─────────────────────────
    @Test
    void enviarMensaje_rateLimitExcedido_lanzaChatbotRateLimitExcedido() {
        Authentication auth = authComoLector();
        prepararUsuarioLector();
        given(chatbotRateLimiter.estaBloqueado(1L)).willReturn(true);

        assertThatThrownBy(() -> chatbotService.enviarMensaje(
                new MensajeChatRequestDTO(null, "hola"), auth))
                .isInstanceOf(ChatbotRateLimitExcedidoException.class);
        verify(geminiClient, never()).generarRespuesta(anyString(), any(), anyString());
        verify(sesionChatRepo, never()).save(any(SesionChat.class));
    }

    // ── Test 5: grounding de disponibilidad ─────────────────
    @Test
    void enviarMensaje_textoConsultaDisponibilidad_incluyeResultadosDeLibroServiceEnContexto() {
        Authentication auth = authComoLector();
        prepararUsuarioLector();
        given(chatbotRateLimiter.estaBloqueado(1L)).willReturn(false);
        given(sesionChatRepo.save(any(SesionChat.class))).willAnswer(inv -> {
            SesionChat s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        given(mensajeChatRepo.save(any(MensajeChat.class))).willAnswer(inv -> inv.getArgument(0));
        given(baseConocimientoRepo.findByActivoTrue()).willReturn(List.of());
        given(mensajeChatRepo.findBySesionIdOrderByCreadoEnAsc(any(UUID.class))).willReturn(List.of());
        given(libroService.sugerir(anyString())).willReturn(
                List.of(new LibroSugerenciaDTO(10L, "Clean Code", true)));
        given(geminiClient.generarRespuesta(anyString(), any(), anyString())).willReturn("Sí está disponible.");

        chatbotService.enviarMensaje(
                new MensajeChatRequestDTO(null, "¿hay disponible el libro Clean Code?"), auth);

        ArgumentCaptor<String> captorPrompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generarRespuesta(captorPrompt.capture(), any(), anyString());
        assertThat(captorPrompt.getValue())
                .contains("Clean Code")
                .contains("disponible=true");
        verify(libroService).sugerir(anyString());
    }

    // ── Test 6: fallback de Gemini no rompe el flujo ────────
    @Test
    void enviarMensaje_geminiClientDevuelveMensajeDeFallback_seGuardaComoRespuestaAsistente() {
        Authentication auth = authComoLector();
        prepararUsuarioLector();
        given(chatbotRateLimiter.estaBloqueado(1L)).willReturn(false);
        given(sesionChatRepo.save(any(SesionChat.class))).willAnswer(inv -> {
            SesionChat s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        given(mensajeChatRepo.save(any(MensajeChat.class))).willAnswer(inv -> inv.getArgument(0));
        given(baseConocimientoRepo.findByActivoTrue()).willReturn(List.of());
        given(mensajeChatRepo.findBySesionIdOrderByCreadoEnAsc(any(UUID.class))).willReturn(List.of());
        given(geminiClient.generarRespuesta(anyString(), any(), anyString()))
                .willReturn(MENSAJE_FALLBACK_GEMINI);

        MensajeChatResponseDTO resultado = chatbotService.enviarMensaje(
                new MensajeChatRequestDTO(null, "hola"), auth);

        assertThat(resultado.respuesta()).isEqualTo(MENSAJE_FALLBACK_GEMINI);
        verify(mensajeChatRepo).save(argRol("ASISTENTE"));
        verify(chatbotRateLimiter).registrarMensaje(1L);
    }

    // ── Test 7: historial propio ordenado ───────────────────
    @Test
    void obtenerHistorial_sesionPropia_retornaMensajesOrdenados() {
        Authentication auth = authComoLector();
        prepararUsuarioLector();
        UUID sesionId = UUID.randomUUID();
        given(sesionChatRepo.findById(sesionId)).willReturn(Optional.of(sesionDeUsuario(sesionId, 1L)));
        MensajeChat primero = mensaje("USUARIO", "¿hay libros?", OffsetDateTime.now().minusMinutes(2));
        MensajeChat segundo = mensaje("ASISTENTE", "Sí, revisa el catálogo", OffsetDateTime.now());
        given(mensajeChatRepo.findBySesionIdOrderByCreadoEnAsc(sesionId)).willReturn(List.of(primero, segundo));

        List<MensajeChatHistorialDTO> historial = chatbotService.obtenerHistorial(sesionId, auth);

        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).rol()).isEqualTo("USUARIO");
        assertThat(historial.get(1).rol()).isEqualTo("ASISTENTE");
        assertThat(historial.get(1).contenido()).isEqualTo("Sí, revisa el catálogo");
    }

    // ── Test 8: historial de sesión ajena ───────────────────
    @Test
    void obtenerHistorial_sesionAjena_lanzaSesionChatNoEncontrada() {
        Authentication auth = authComoLector();
        prepararUsuarioLector();
        UUID sesionId = UUID.randomUUID();
        given(sesionChatRepo.findById(sesionId)).willReturn(Optional.of(sesionDeUsuario(sesionId, 2L)));

        assertThatThrownBy(() -> chatbotService.obtenerHistorial(sesionId, auth))
                .isInstanceOf(SesionChatNoEncontradaException.class);
        verify(mensajeChatRepo, never()).findBySesionIdOrderByCreadoEnAsc(any(UUID.class));
    }

    // ── Helpers ────────────────────────────────────────────
    private Authentication authComoLector() {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(CORREO);
        return auth;
    }

    private void prepararUsuarioLector() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        given(usuarioRepo.findByCorreo(CORREO)).willReturn(Optional.of(usuario));
    }

    private SesionChat sesionDeUsuario(UUID id, Long usuarioId) {
        SesionChat s = new SesionChat();
        s.setId(id);
        s.setUsuarioId(usuarioId);
        s.setCreadoEn(OffsetDateTime.now().minusHours(1));
        s.setUltimaActividad(OffsetDateTime.now().minusMinutes(1));
        return s;
    }

    private MensajeChat mensaje(String rol, String contenido, OffsetDateTime creadoEn) {
        MensajeChat m = new MensajeChat();
        m.setSesionId(UUID.randomUUID());
        m.setRol(rol);
        m.setContenido(contenido);
        m.setCreadoEn(creadoEn);
        return m;
    }

    private MensajeChat argRol(String rol) {
        return org.mockito.ArgumentMatchers.argThat(m -> m instanceof MensajeChat
                && rol.equals(((MensajeChat) m).getRol()));
    }
}
