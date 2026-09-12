package com.uteq.backend.controller;

import com.uteq.backend.entity.ConfigurationBackup;
import com.uteq.backend.entity.RegistrationBackup;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.FullBackupService;
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

@WebMvcTest(FullBackupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "admin@correo.com", roles = "ADMIN")
class BackupFullControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FullBackupService service;

    private ConfigurationBackup configMock() {
        ConfigurationBackup c = new ConfigurationBackup();
        c.setId(1L);
        c.setFrequencyTimes(24);
        c.setDaysRetention(30);
        c.setEnabled(true);
        return c;
    }

    private RegistrationBackup registrationMock() {
        RegistrationBackup r = new RegistrationBackup();
        r.setId(10L);
        r.setType("COMPLETO");
        r.setStatus("EXITOSO");
        r.setStarted(OffsetDateTime.now());
        return r;
    }

    @Test
    void getConfig_devuelve200() throws Exception {
        when(service.getConfiguration()).thenReturn(configMock());

        mockMvc.perform(get("/api/v1/admin/respaldo-completo/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frecuenciaHoras").value(24));
    }

    @Test
    void updateConfig_devuelve200() throws Exception {
        when(service.updateConfiguration(24, 30, true)).thenReturn(configMock());

        mockMvc.perform(put("/api/v1/admin/respaldo-completo/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frecuenciaHoras\":24,\"diasRetencion\":30,\"habilitado\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void listRegistrations_withoutType_devuelve200() throws Exception {
        when(service.listAll()).thenReturn(List.of(registrationMock()));

        mockMvc.perform(get("/api/v1/admin/respaldo-completo/registros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void listRegistrations_withType_devuelve200() throws Exception {
        when(service.listByType("COMPLETO")).thenReturn(List.of(registrationMock()));

        mockMvc.perform(get("/api/v1/admin/respaldo-completo/registros").param("tipo", "COMPLETO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("COMPLETO"));
    }

    @Test
    void deleteRegistration_devuelve204() throws Exception {
        doNothing().when(service).delete(10L);

        mockMvc.perform(delete("/api/v1/admin/respaldo-completo/registros/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    void registerStart_devuelve200() throws Exception {
        when(service.registerStart("COMPLETO", null)).thenReturn(registrationMock());

        mockMvc.perform(post("/api/v1/admin/respaldo-completo/registros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"COMPLETO\",\"ejecutadoPor\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("COMPLETO"));
    }

    @Test
    void registerResult_devuelve200() throws Exception {
        RegistrationBackup r = registrationMock();
        when(service.registerResult(eq(10L), any(), any(), any(), any(), any())).thenReturn(r);

        mockMvc.perform(put("/api/v1/admin/respaldo-completo/registros/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EXITOSO\",\"nombreArchivo\":\"bk.zip\",\"tamanoArchivoBytes\":1024,\"rutaR2\":null,\"mensajeError\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXITOSO"));
    }

    @Test
    void triggerBackupFull_devuelve503SiNodeNotResponde() throws Exception {
        mockMvc.perform(post("/api/v1/admin/respaldo-completo/trigger")
                        .principal(new org.springframework.security.authentication.TestingAuthenticationToken("admin@correo.com", null, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable());
    }
}
