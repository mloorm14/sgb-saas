package com.uteq.backend.config;

import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Fix de sesión 2026-08: el backend ahora sirve el SPA de Angular desde el
// mismo origen (WebConfig), porque la cookie refreshToken HttpOnly no
// sobrevivía entre dos subdominios de Render (onrender.com está en la
// Public Suffix List). Estos tests blindan el contrato del manejador de
// recursos: las rutas del router del SPA caen a index.html y /api/** sigue
// exigiendo autenticación (403 anónimo, ver también ChatbotControllerSecurityTest).
// El placeholder de index.html vive en src/test/resources/static/; en el
// despliegue real lo reemplaza el build de Angular (ver WebConfig).
@WebMvcTest(useDefaultFilters = false)
@Import({SecurityConfig.class, JwtAuthFilter.class, WebConfig.class})
class SpaFallbackTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

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

    @Test
    void rutaDelRouterDelSPA_devuelveIndexHtml() throws Exception {
        mockMvc.perform(get("/libros"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void raiz_devuelveIndexHtml() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    void rutaConVariosSegmentos_devuelveIndexHtml() throws Exception {
        mockMvc.perform(get("/libros/5/editar"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void apiSinAutenticacion_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/libros"))
                .andExpect(status().isForbidden());
    }
}