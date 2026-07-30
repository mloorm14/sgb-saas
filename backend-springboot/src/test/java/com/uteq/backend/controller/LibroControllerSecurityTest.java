package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.dto.LibroRequestDTO;
import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.LibroService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Bloque C.2 (hallazgo OWASP A01): LibroController no incluia ADMIN en la
// lista de roles permitidos de ningun endpoint -- un usuario exclusivamente
// ADMIN (sin GERENTE/BIBLIOTECARIO) recibia 403 incluso para operaciones que
// ADMIN deberia poder hacer segun la jerarquia ADMIN > GERENTE >
// BIBLIOTECARIO > LECTOR establecida en el resto del sistema (ver
// JwtService.JERARQUIA_ROLES). Este test verifica @PreAuthorize a nivel de
// controller via MockMvc + @WithMockUser -- un test de LibroServiceTest
// (Mockito puro, sin contexto Spring) no puede probar esto porque
// @PreAuthorize es AOP de metodo, evaluado solo con un contexto Spring real
// y method security habilitado (@EnableMethodSecurity en SecurityConfig).
@WebMvcTest(LibroController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class LibroControllerSecurityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    // El MockMvc autoconfigurado por @WebMvcTest no aplica el soporte de
    // spring-security-test (SecurityMockMvcConfigurers.springSecurity())
    // automaticamente en este proyecto -- sin el, @WithMockUser no puebla
    // el SecurityContext real para la request y CUALQUIER request llega
    // como no autenticada (403 via Http403ForbiddenEntryPoint por defecto,
    // no por @PreAuthorize), enmascarando el comportamiento real que este
    // test necesita verificar. Se reconstruye MockMvc explicitamente con
    // el soporte de seguridad aplicado.
    @BeforeEach
    void construirMockMvcConSeguridad() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // No autowireado: en el slice de @WebMvcTest de este proyecto (Spring
    // Boot 4, modulos separados) el ObjectMapper configurado por Spring no
    // esta disponible como bean sin autoconfig adicional. No hace falta
    // ninguna configuracion especial (deserializers custom, etc.) para
    // serializar un record simple como LibroRequestDTO, asi que una
    // instancia local alcanza.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private LibroService libroService;

    // Dependencias de JwtAuthFilter (el filtro real se usa tal cual, ver
    // @Import arriba -- mockearlo directamente rompe la cadena de filtros,
    // porque Mockito no invoca filterChain.doFilter() en un metodo void sin
    // stub, y la request nunca llega al controller). No se ejercitan en
    // este test porque @WithMockUser inyecta la Authentication directo en
    // el SecurityContext sin enviar header Authorization -- JwtAuthFilter
    // hace un return temprano en ese caso sin tocar ninguna de estas.
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private LibroRequestDTO libroValido() {
        return new LibroRequestDTO(
                "Clean Code", "9780132350884", 2008, "resumen", null,
                1, 1, 1, 3, 3
        );
    }

    private LibroResponseDTO libroCreado() {
        return new LibroResponseDTO(
                1L, "Clean Code", "9780132350884", "resumen", null, 2008,
                1, "Editorial X", 1, "Español", 1, "ACTIVO", 3, 3, null,
                OffsetDateTime.now()
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crear_conRolAdmin_sePermite() throws Exception {
        when(libroService.crear(any())).thenReturn(libroCreado());

        mockMvc.perform(post("/api/v1/libros")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(libroValido())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void actualizar_conRolAdmin_sePermite() throws Exception {
        when(libroService.actualizar(anyLong(), any())).thenReturn(libroCreado());

        mockMvc.perform(put("/api/v1/libros/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(libroValido())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void eliminar_conRolAdmin_sePermite() throws Exception {
        mockMvc.perform(delete("/api/v1/libros/1"))
                .andExpect(status().isNoContent());
    }

    // Regresion: confirma que el fix no sobre-otorgo permisos -- un rol sin
    // relacion con el catalogo sigue recibiendo 403.
    @Test
    @WithMockUser(roles = "LECTOR")
    void crear_conRolLector_sigueRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/libros")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(libroValido())))
                .andExpect(status().isForbidden());
    }
}
