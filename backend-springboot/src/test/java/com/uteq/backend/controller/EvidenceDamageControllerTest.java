package com.uteq.backend.controller;

import com.uteq.backend.dto.EvidenceDamageFileDTO;
import com.uteq.backend.dto.EvidenceDamageResponseDTO;
import com.uteq.backend.entity.User;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.UserRepository;
import com.uteq.backend.service.LoanReturnService;
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

@WebMvcTest(EvidenceDamageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "biblio@correo.com", roles = "BIBLIOTECARIO")
class EvidenceDamageControllerTest extends WebMvcControllerTestSupport {

    org.springframework.security.authentication.TestingAuthenticationToken mockAuth = new org.springframework.security.authentication.TestingAuthenticationToken("biblio@correo.com", null, "ROLE_BIBLIOTECARIO");


    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanReturnService loanReturnService;

    @MockitoBean
    private UserRepository userRepo;

    private User librarian() {
        User u = new User();
        u.setId(8L);
        u.setEmail("biblio@correo.com");
        return u;
    }

    private EvidenceDamageResponseDTO evidence() {
        return new EvidenceDamageResponseDTO(
                11L, 4L, "foto.jpg", "image/jpeg",
                OffsetDateTime.parse("2026-01-15T10:00:00-05:00"));
    }

    @Test
    void uploadEvidence_dataValids_devuelve200() throws Exception {
        when(userRepo.findByEmail("biblio@correo.com")).thenReturn(Optional.of(librarian()));
        when(loanReturnService.uploadEvidence(eq(4L), any(), eq(8L))).thenReturn(evidence());

        MockMultipartFile file = new MockMultipartFile(
                "archivo", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/devoluciones/evidencia/4")
                .principal(mockAuth).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.archivoNombre").value("foto.jpg"));
    }

    @Test
    void uploadEvidence_userInexistente_devuelve404() throws Exception {
        when(userRepo.findByEmail("biblio@correo.com")).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile(
                "archivo", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/api/v1/devoluciones/evidencia/4")
                .principal(mockAuth).file(file))
                .andExpect(status().isNotFound());
    }

    @Test
    void listEvidences_devuelve200() throws Exception {
        when(loanReturnService.listEvidences(4L)).thenReturn(List.of(evidence()));

        mockMvc.perform(get("/api/v1/devoluciones/evidencia/4")
                .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].registroDanoId").value(4));
    }

    @Test
    void getFile_existing_devuelve200() throws Exception {
        when(loanReturnService.getFileBinario(11L))
                .thenReturn(new EvidenceDamageFileDTO("image/jpeg", new byte[]{9, 8, 7}));

        mockMvc.perform(get("/api/v1/devoluciones/evidencia/11/archivo")
                .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(new byte[]{9, 8, 7}));
    }

    @Test
    void getFile_inexistente_devuelve404() throws Exception {
        when(loanReturnService.getFileBinario(99L))
                .thenThrow(new EntityNotFoundException("Evidencia no encontrada: 99"));

        mockMvc.perform(get("/api/v1/devoluciones/evidencia/99/archivo")
                .principal(mockAuth))
                .andExpect(status().isNotFound());
    }
}
