package com.uteq.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code JwtAuthFilter} se registra como {@code Filter} y {@code @WebMvcTest} lo carga.
 * Estos mocks evitan fallar el contexto por dependencias de seguridad no usadas
 * cuando {@code addFilters = false}.
 *
 * ObjectMapper no está disponible como bean en este slice (Spring Boot 4);
 * se usa una instancia local con JavaTimeModule.
 */
abstract class WebMvcControllerTestSupport {

    protected final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    /**
     * Con {@code addFilters = false} no corre el filtro que copia
     * {@code TestSecurityContextHolder} al {@code SecurityContextHolder}.
     * Sin esto, los parámetros {@code Authentication} del controller llegan null.
     */
    @BeforeEach
    void copiarContextoSeguridadTest() {
        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }
}
