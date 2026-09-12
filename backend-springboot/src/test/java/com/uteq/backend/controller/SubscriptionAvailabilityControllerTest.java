package com.uteq.backend.controller;

import com.uteq.backend.entity.User;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.UserRepository;
import com.uteq.backend.service.SubscriptionAvailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.authentication.TestingAuthenticationToken;

@WebMvcTest(SubscriptionAvailabilityController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SubscriptionAvailabilityControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionAvailabilityService service;

    @MockitoBean
    private UserRepository userRepo;

    private User reader() {
        User u = new User();
        u.setId(7L);
        u.setEmail("lector@correo.com");
        return u;
    }

    @Test
    @WithMockUser(username = "lector@correo.com", roles = "LECTOR")
    void suscribir_userByEmail_devuelve200() throws Exception {
        when(userRepo.findByEmail("lector@correo.com")).thenReturn(Optional.of(reader()));

        mockMvc.perform(post("/api/v1/libros/3/suscripciones")
                .principal(new TestingAuthenticationToken("lector@correo.com", null, "ROLE_LECTOR")))
                .andExpect(status().isOk());

        verify(service).suscribir(7L, 3L);
    }

    @Test
    @WithMockUser(username = "lector@correo.com", roles = "LECTOR")
    void suscribir_userInexistente_devuelve404() throws Exception {
        when(userRepo.findByEmail("lector@correo.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/libros/3/suscripciones")
                .principal(new TestingAuthenticationToken("lector@correo.com", null, "ROLE_LECTOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Usuario no encontrado: lector@correo.com"));
    }

    @Test
    @WithMockUser(username = "5", roles = "LECTOR")
    void desuscribir_userNumerico_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/libros/3/suscripciones")
                .principal(new TestingAuthenticationToken("5", null, "ROLE_LECTOR")))
                .andExpect(status().isNoContent());

        verify(service).desuscribir(5L, 3L);
    }

    @Test
    @WithMockUser(username = "lector@correo.com", roles = "LECTOR")
    void misSubscriptions_devuelve200() throws Exception {
        when(userRepo.findByEmail("lector@correo.com")).thenReturn(Optional.of(reader()));
        when(service.listBooksIds(7L)).thenReturn(List.of(3L, 9L));

        mockMvc.perform(get("/api/v1/libros/suscripciones/mias")
                .principal(new TestingAuthenticationToken("lector@correo.com", null, "ROLE_LECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(3))
                .andExpect(jsonPath("$[1]").value(9));
    }

    @Test
    @WithMockUser(username = "7", roles = "LECTOR")
    void misSubscriptions_userNumerico_devuelve200() throws Exception {
        when(service.listBooksIds(7L)).thenReturn(List.of(3L));

        mockMvc.perform(get("/api/v1/libros/suscripciones/mias")
                .principal(new TestingAuthenticationToken("7", null, "ROLE_LECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(3));
    }
}
