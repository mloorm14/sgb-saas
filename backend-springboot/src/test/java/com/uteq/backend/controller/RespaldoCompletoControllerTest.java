package com.uteq.backend.controller;

import com.uteq.backend.entity.ConfiguracionRespaldo;
import com.uteq.backend.entity.RegistroRespaldo;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.RespaldoCompletoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RespaldoCompletoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "admin@correo.com", roles = "ADMIN")
class RespaldoCompletoControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RespaldoCompletoService service;

    private ConfiguracionRespaldo configMock() {
        ConfiguracionRespaldo c = new ConfiguracionRespaldo();
        c.setId(1L);
        c.setFrecuenciaHoras(24);
        c.setDiasRetencion(30);
        c.setHabilitado(true);
        return c;
    }

    private RegistroRespaldo registroMock() {
        RegistroRespaldo r = new RegistroRespaldo();
        r.setId(10L);
        r.setTipo("COMPLETO");
        r.setEstado("EXITOSO");
        r.setIniciadoEn(OffsetDateTime.now());
        return r;
    }

    @Test
    void obtenerConfig_devuelve200() throws Exception {
        when(service.obtenerConfiguracion()).thenReturn(configMock());

        mockMvc.perform(get("/api/v1/admin/respaldo-completo/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frecuenciaHoras").value(24));
    }

    @Test
    void actualizarConfig_devuelve200() throws Exception {
        when(service.actualizarConfiguracion(24, 30, true)).thenReturn(configMock());

        mockMvc.perform(put("/api/v1/admin/respaldo-completo/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frecuenciaHoras\":24,\"diasRetencion\":30,\"habilitado\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void listarRegistros_sinTipo_devuelve200() throws Exception {
        when(service.listarTodos()).thenReturn(List.of(registroMock()));

        mockMvc.perform(get("/api/v1/admin/respaldo-completo/registros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void listarRegistros_conTipo_devuelve200() throws Exception {
        when(service.listarPorTipo("COMPLETO")).thenReturn(List.of(registroMock()));

        mockMvc.perform(get("/api/v1/admin/respaldo-completo/registros").param("tipo", "COMPLETO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("COMPLETO"));
    }

    @Test
    void eliminarRegistro_devuelve204() throws Exception {
        doNothing().when(service).eliminar(10L);

        mockMvc.perform(delete("/api/v1/admin/respaldo-completo/registros/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    void registrarInicio_devuelve200() throws Exception {
        when(service.registrarInicio("COMPLETO", null)).thenReturn(registroMock());

        mockMvc.perform(post("/api/v1/admin/respaldo-completo/registros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"COMPLETO\",\"ejecutadoPor\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("COMPLETO"));
    }

    @Test
    void registrarResultado_devuelve200() throws Exception {
        RegistroRespaldo r = registroMock();
        when(service.registrarResultado(eq(10L), any(), any(), any(), any(), any())).thenReturn(r);

        mockMvc.perform(put("/api/v1/admin/respaldo-completo/registros/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EXITOSO\",\"nombreArchivo\":\"bk.zip\",\"tamanoArchivoBytes\":1024,\"rutaR2\":null,\"mensajeError\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXITOSO"));
    }
}
