package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.dto.BookIsbnLookupDTO;
import com.uteq.backend.dto.BookRequestDTO;
import com.uteq.backend.dto.BookResponseDTO;
import com.uteq.backend.dto.CoverImageDTO;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.BookService;
import com.uteq.backend.service.BookIsbnLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
@WebMvcTest(BookController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class BookControllerSecurityTest {

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
    void construirMockMvcWithSeguridad() {
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
    private BookService bookService;

    // Dependencia de los endpoints /lookup-isbn (Módulo inventario).
    @MockitoBean
    private BookIsbnLookupService bookIsbnLookupService;

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

    private BookRequestDTO bookValid() {
        return new BookRequestDTO(
                "Clean Code", "9780132350884", 2008, null, new java.math.BigDecimal("25.00"), "resumen", null, null,
                1, 1, 1, 3, 3, null, null, null
        );
    }

    private BookResponseDTO bookCreated() {
        return new BookResponseDTO(
                1L, "Clean Code", "9780132350884", "resumen", null,
                false, null, null, 2008, null, null,
                1, "Editorial X", 1, "Español", 1, "ACTIVO", 3, 3, null,
                OffsetDateTime.now(), List.of(), List.of(), null, null
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_withRoleAdmin_sePermite() throws Exception {
        when(bookService.create(any())).thenReturn(bookCreated());

        mockMvc.perform(post("/api/v1/libros")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(bookValid())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_withRoleAdmin_sePermite() throws Exception {
        when(bookService.update(anyLong(), any())).thenReturn(bookCreated());

        mockMvc.perform(put("/api/v1/libros/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(bookValid())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_withRoleAdmin_sePermite() throws Exception {
        mockMvc.perform(delete("/api/v1/libros/1"))
                .andExpect(status().isNoContent());
    }

    // Regresion: confirma que el fix no sobre-otorgo permisos -- un rol sin
    // relacion con el catalogo sigue recibiendo 403.
    @Test
    @WithMockUser(roles = "LECTOR")
    void create_withRoleReader_sigueRejected() throws Exception {
        mockMvc.perform(post("/api/v1/libros")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(bookValid())))
                .andExpect(status().isForbidden());
    }

    // ── Módulo portada binaria (V13__portada_imagen.sql): ──
    // LECTOR puede VER la portada (GET) pero NO subirla (POST) -- la
    // subida es gestión de catálogo, mismo criterio que crear/actualizar.
    @Test
    @WithMockUser(roles = "LECTOR")
    void uploadCover_withRoleReader_rechazado403() throws Exception {
        mockMvc.perform(multipart("/api/v1/libros/1/portada")
                        .file(new MockMultipartFile("archivo", "portada.png", "image/png", new byte[]{1})))
                .andExpect(status().isForbidden());
        verify(bookService, never()).updateCover(anyLong(), any());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void getCover_withRoleReader_sePermite() throws Exception {
        when(bookService.getCover(anyLong()))
                .thenReturn(new CoverImageDTO(new byte[]{1, 2, 3}, "image/png"));

        mockMvc.perform(get("/api/v1/libros/1/portada"))
                .andExpect(status().isOk())
                // Content-Type dinámico según portada_tipo guardado.
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadCover_withRoleAdmin_sePermite() throws Exception {
        when(bookService.updateCover(anyLong(), any())).thenReturn(bookCreated());

        mockMvc.perform(multipart("/api/v1/libros/1/portada")
                        .file(new MockMultipartFile("archivo", "portada.png", "image/png", new byte[]{1})))
                .andExpect(status().isOk());
    }

    // ── Módulo inventario (autocompletar ISBN): solo gestión de catálogo,
    // mismo criterio de roles que crear/actualizar el libro. ──
    @Test
    @WithMockUser(roles = "ADMIN")
    void lookupIsbn_withRoleAdmin_sePermite() throws Exception {
        when(bookIsbnLookupService.searchByIsbn("9780132350884"))
                .thenReturn(new BookIsbnLookupDTO("Clean Code", "Robert C. Martin", "resumen", 2008, true, null, 400));

        mockMvc.perform(get("/api/v1/libros/lookup-isbn").param("isbn", "9780132350884"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"titulo\":\"Clean Code\",\"anioPublicacion\":2008,\"portadaDisponible\":true}"));
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void lookupIsbn_withRoleReader_rechazado403() throws Exception {
        mockMvc.perform(get("/api/v1/libros/lookup-isbn").param("isbn", "9780132350884"))
                .andExpect(status().isForbidden());
        verify(bookIsbnLookupService, never()).searchByIsbn(any());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void lookupIsbnCover_withRoleManager_sePermite() throws Exception {
        when(bookIsbnLookupService.getCover("9780132350884"))
                .thenReturn(new CoverImageDTO(new byte[]{1, 2, 3}, "image/jpeg"));

        mockMvc.perform(get("/api/v1/libros/lookup-isbn/portada").param("isbn", "9780132350884"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void lookupIsbnCover_withRoleReader_rechazado403() throws Exception {
        mockMvc.perform(get("/api/v1/libros/lookup-isbn/portada").param("isbn", "9780132350884"))
                .andExpect(status().isForbidden());
        verify(bookIsbnLookupService, never()).getCover(any());
    }
}
