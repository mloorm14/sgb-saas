package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.dto.LibroSugerenciaDTO;
import com.uteq.backend.dto.PortadaImagenDTO;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.LibroService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Rama C (portal público): el controller /api/publico/libros es la única
// superficie que SecurityConfig deja pasar sin JWT (permitAll() sobre
// /api/publico/**). Este test verifica (a) que todos los GET públicos
// responden 200 SIN header Authorization, (b) que el endpoint viejo
// /api/v1/libros sigue protegido (regresión: no se abrió por accidente),
// y (c) que la superficie pública es angosta: no existe POST y no hay
// mapeos fuera de /api/publico/libros.
//
// El entry point por defecto de la cadena (sin httpBasic/formLogin
// configurados en SecurityConfig) es Http403ForbiddenEntryPoint: una
// request anónima a /api/v1/libros responde 403, no 401 (mismo
// comportamiento documentado en LibroControllerSecurityTest).
@WebMvcTest(PublicoLibroController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class PublicoLibroControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private LibroService libroService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @BeforeEach
    void construirMockMvcConSeguridad() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private LibroResponseDTO libroRespuesta() {
        return new LibroResponseDTO(
                1L, "Clean Code", "9780132350884", "resumen", null,
                false, null, null, 2008, null, null,
                1, "Editorial X", 1, "Español", 1, "ACTIVO", 3, 3, null,
                OffsetDateTime.now(), List.of(), List.of()
        );
    }

    @Test
    void listar_sinToken_responde200() throws Exception {
        when(libroService.listarConFiltros(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(libroRespuesta())));

        mockMvc.perform(get("/api/publico/libros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].titulo").value("Clean Code"));
    }

    @Test
    void listar_conFiltroCategoria_sinToken_responde200YDelega() throws Exception {
        when(libroService.listarConFiltros(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(5), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(libroRespuesta())));

        mockMvc.perform(get("/api/publico/libros").param("categoriaId", "5"))
                .andExpect(status().isOk());

        verify(libroService).listarConFiltros(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(5), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listar_conFiltroAutor_sinToken_responde200YDelega() throws Exception {
        when(libroService.listarConFiltros(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(libroRespuesta())));

        mockMvc.perform(get("/api/publico/libros").param("autorId", "3"))
                .andExpect(status().isOk());

        verify(libroService).listarConFiltros(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sugerencias_sinToken_responde200() throws Exception {
        when(libroService.sugerir("clean"))
                .thenReturn(List.of(new LibroSugerenciaDTO(1L, "Clean Code", true)));

        mockMvc.perform(get("/api/publico/libros/sugerencias").param("texto", "clean"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Clean Code"));
    }

    @Test
    void obtener_sinToken_responde200() throws Exception {
        when(libroService.buscarPorIdPublico(1L)).thenReturn(libroRespuesta());

        mockMvc.perform(get("/api/publico/libros/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Clean Code"));
    }

    @Test
    void portada_sinToken_responde200ConContentTypeDinamico() throws Exception {
        when(libroService.obtenerPortada(1L))
                .thenReturn(new PortadaImagenDTO(new byte[]{1, 2, 3}, "image/webp"));

        mockMvc.perform(get("/api/publico/libros/1/portada"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/webp"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    // Regresión: el endpoint autenticado viejo NO se abrió por accidente al
    // agregar /api/publico/** a permitAll().
    @Test
    void endpointViejo_sinToken_sigueProtegido() throws Exception {
        mockMvc.perform(get("/api/v1/libros"))
                .andExpect(status().isForbidden());
    }

    // Superficie angosta: el portal público no expone escritura. No hay
    // mapeo POST en PublicoLibroController; Spring lanza
    // HttpRequestMethodNotSupportedException y el GlobalExceptionHandler
    // genérico la convierte en 500 (comportamiento ya documentado ahí).
    @Test
    void post_sinMapeo_quedaRechazado() throws Exception {
        mockMvc.perform(post("/api/publico/libros"))
                .andExpect(status().is5xxServerError());
    }

    // Superficie angosta: fuera de /api/publico/libros no hay nada público.
    @Test
    void otrosDominios_noExistenEnLaSuperficiePublica() throws Exception {
        mockMvc.perform(get("/api/publico/prestamos"))
                .andExpect(status().isNotFound());
    }
}