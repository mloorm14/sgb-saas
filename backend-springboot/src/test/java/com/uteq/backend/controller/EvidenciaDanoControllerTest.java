package com.uteq.backend.controller;

import com.uteq.backend.dto.EvidenciaDanoArchivoDTO;
import com.uteq.backend.dto.EvidenciaDanoResponseDTO;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.service.DevolucionService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvidenciaDanoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "biblio@correo.com", roles = "BIBLIOTECARIO")
class EvidenciaDanoControllerTest extends WebMvcControllerTestSupport {

    org.springframework.security.authentication.TestingAuthenticationToken mockAuth = new org.springframework.security.authentication.TestingAuthenticationToken("biblio@correo.com", null, "ROLE_BIBLIOTECARIO");


    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DevolucionService devolucionService;

    @MockitoBean
    private UsuarioRepository usuarioRepo;

    private Usuario bibliotecario() {
        Usuario u = new Usuario();
        u.setId(8L);
        u.setCorreo("biblio@correo.com");
        return u;
    }

    private EvidenciaDanoResponseDTO evidencia() {
        return new EvidenciaDanoResponseDTO(
                11L, 4L, "foto.jpg", "image/jpeg",
                OffsetDateTime.parse("2026-01-15T10:00:00-05:00"));
    }

    @Test
    void subirEvidencia_datosValidos_devuelve200() throws Exception {
        when(usuarioRepo.findByCorreo("biblio@correo.com")).thenReturn(Optional.of(bibliotecario()));
        when(devolucionService.subirEvidencia(eq(4L), any(), eq(8L))).thenReturn(evidencia());

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/devoluciones/evidencia/4")
                .principal(mockAuth).file(archivo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.archivoNombre").value("foto.jpg"));
    }

    @Test
    void subirEvidencia_usuarioInexistente_devuelve404() throws Exception {
        when(usuarioRepo.findByCorreo("biblio@correo.com")).thenReturn(Optional.empty());

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/api/v1/devoluciones/evidencia/4")
                .principal(mockAuth).file(archivo))
                .andExpect(status().isNotFound());
    }

    @Test
    void listarEvidencias_devuelve200() throws Exception {
        when(devolucionService.listarEvidencias(4L)).thenReturn(List.of(evidencia()));

        mockMvc.perform(get("/api/v1/devoluciones/evidencia/4")
                .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].registroDanoId").value(4));
    }

    @Test
    void obtenerArchivo_existente_devuelve200() throws Exception {
        when(devolucionService.obtenerArchivoBinario(11L))
                .thenReturn(new EvidenciaDanoArchivoDTO("image/jpeg", new byte[]{9, 8, 7}));

        mockMvc.perform(get("/api/v1/devoluciones/evidencia/11/archivo")
                .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(new byte[]{9, 8, 7}));
    }

    @Test
    void obtenerArchivo_inexistente_devuelve404() throws Exception {
        when(devolucionService.obtenerArchivoBinario(99L))
                .thenThrow(new EntityNotFoundException("Evidencia no encontrada: 99"));

        mockMvc.perform(get("/api/v1/devoluciones/evidencia/99/archivo")
                .principal(mockAuth))
                .andExpect(status().isNotFound());
    }
}
