package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.dto.CambioEstadoUsuarioRequestDTO;
import com.uteq.backend.dto.CambioRolRequestDTO;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.UsuarioAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Módulo 5 / adr-014 + F8-gerente/V38: GERENTE lista (solo suyos vía ?mios
// en el service) y puede crear/cambiar-rol/cambiar-estado limitado a
// LECTOR/BIBLIOTECARIO + ACTIVO/INACTIVO sobre sus creados (el recorte fino
// vive en UsuarioAdminService, no en @PreAuthorize). Solo ADMIN elimina y
// crea GERENTE/ADMIN. Mismo patrón que ConfiguracionSistemaControllerSecurityTest.
@WebMvcTest(UsuarioAdminController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class UsuarioAdminControllerSecurityTest {

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
    private UsuarioAdminService usuarioAdminService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    @WithMockUser(roles = "ADMIN")
    void listar_conRolAdmin_sePermite() throws Exception {
        when(usuarioAdminService.listar(any(), any(Pageable.class), any(), anyBoolean())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/usuarios"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void listar_conRolGerente_sePermite() throws Exception {
        when(usuarioAdminService.listar(any(), any(Pageable.class), any(), anyBoolean())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/usuarios"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void listar_conRolLector_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/admin/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cambiarRol_conRolAdmin_sePermite() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/rol", 5L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CambioRolRequestDTO("BIBLIOTECARIO"))))
                .andExpect(status().isNoContent());
    }

    // F8-gerente/V38: @PreAuthorize admite GERENTE; el recorte fino (solo sus
    // creados + LECTOR/BIBLIOTECARIO) vive en UsuarioAdminService.
    @Test
    @WithMockUser(roles = "GERENTE")
    void cambiarRol_conRolGerente_sePermiteANivelHttp() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/rol", 5L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CambioRolRequestDTO("BIBLIOTECARIO"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cambiarEstado_conRolAdmin_sePermite() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/estado", 5L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CambioEstadoUsuarioRequestDTO("INACTIVO", "Baja solicitada por el usuario"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void cambiarEstado_conRolGerente_sePermiteANivelHttp() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/estado", 5L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CambioEstadoUsuarioRequestDTO("INACTIVO", "Baja solicitada por el usuario"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void cambiarEstado_conRolBibliotecario_seRechaza() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/estado", 5L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CambioEstadoUsuarioRequestDTO("INACTIVO", "motivo"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void crear_conRolGerente_sePermiteANivelHttp() throws Exception {
        mockMvc.perform(post("/api/v1/admin/usuarios")
                        .contentType("application/json")
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Paz\",\"correo\":\"ana@uteq.edu.ec\",\"password\":\"Secreta123\",\"rol\":\"LECTOR\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void crear_conRolLector_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/admin/usuarios")
                        .contentType("application/json")
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Paz\",\"correo\":\"ana@uteq.edu.ec\",\"password\":\"Secreta123\",\"rol\":\"LECTOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void eliminar_conRolGerente_seRechaza() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/usuarios/{id}", 5L))
                .andExpect(status().isForbidden());
    }
}
