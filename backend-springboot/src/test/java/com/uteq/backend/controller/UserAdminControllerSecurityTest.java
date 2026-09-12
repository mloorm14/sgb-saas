package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.dto.ChangeStatusUserRequestDTO;
import com.uteq.backend.dto.ChangeRoleRequestDTO;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.UserAdminService;
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
@WebMvcTest(UserAdminController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class UserAdminControllerSecurityTest {

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
    private UserAdminService userAdminService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_withRoleAdmin_sePermite() throws Exception {
        when(userAdminService.list(any(), any(Pageable.class), any(), anyBoolean())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/usuarios"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void list_withRoleManager_sePermite() throws Exception {
        when(userAdminService.list(any(), any(Pageable.class), any(), anyBoolean())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/usuarios"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void list_withRoleReader_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/admin/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRole_withRoleAdmin_sePermite() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/rol", 5L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChangeRoleRequestDTO("BIBLIOTECARIO"))))
                .andExpect(status().isNoContent());
    }

    // F8-gerente/V38: @PreAuthorize admite GERENTE; el recorte fino (solo sus
    // creados + LECTOR/BIBLIOTECARIO) vive en UsuarioAdminService.
    @Test
    @WithMockUser(roles = "GERENTE")
    void changeRole_withRoleManager_sePermiteALevelHttp() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/rol", 5L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChangeRoleRequestDTO("BIBLIOTECARIO"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void changeStatus_withRoleAdmin_sePermite() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/estado", 5L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new ChangeStatusUserRequestDTO("INACTIVO", "Baja solicitada por el usuario"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void changeStatus_withRoleManager_sePermiteALevelHttp() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/estado", 5L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new ChangeStatusUserRequestDTO("INACTIVO", "Baja solicitada por el usuario"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void changeStatus_withRoleLibrarian_seRechaza() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/estado", 5L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new ChangeStatusUserRequestDTO("INACTIVO", "motivo"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void create_withRoleManager_sePermiteALevelHttp() throws Exception {
        mockMvc.perform(post("/api/v1/admin/usuarios")
                        .contentType("application/json")
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Paz\",\"correo\":\"ana@correo.com\",\"password\":\"Secreta123\",\"rol\":\"LECTOR\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void create_withRoleReader_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/admin/usuarios")
                        .contentType("application/json")
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Paz\",\"correo\":\"ana@correo.com\",\"password\":\"Secreta123\",\"rol\":\"LECTOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void delete_withRoleManager_seRechaza() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/usuarios/{id}", 5L))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_withRoleAdmin_sePermite() throws Exception {
        org.mockito.Mockito.doNothing().when(userAdminService).deleteUser(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/admin/usuarios/1").param("motivo", "test"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
    }
}
