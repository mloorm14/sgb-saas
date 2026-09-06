package com.uteq.backend.controller;

import com.uteq.backend.entity.Backup;
import com.uteq.backend.entity.BackupProgramacion;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.BackupProgramacionService;
import com.uteq.backend.service.BackupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BackupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "admin@correo.com", roles = "ADMIN")
class BackupControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BackupService backupService;

    @MockitoBean
    private BackupProgramacionService progService;

    private Backup backup() {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        return Backup.builder()
                .id(4L)
                .creadoPor(1L)
                .desde(ahora.minusDays(1))
                .hasta(ahora)
                .tablas(Set.of("prestamos"))
                .formato("sql")
                .ruta("/tmp/backup.zip")
                .tamanoBytes(99L)
                .estado("COMPLETADO")
                .tipo("manual")
                .creadoEn(ahora)
                .build();
    }

    private BackupProgramacion programacionHoras() {
        BackupProgramacion p = new BackupProgramacion();
        p.setId(8L);
        p.setCreadoPor(1L);
        p.setCadaHoras(6);
        p.setFormato("sql");
        p.setActivo(true);
        p.setTablas(Set.of("prestamos"));
        return p;
    }

    @Test
    void generar_datosValidos_devuelve201() throws Exception {
        when(backupService.generarBackup(any(), any(), any(), eq("sql"), eq("manual"))).thenReturn(backup());

        String body = """
                {"desde":"2026-01-01T00:00:00-05:00","hasta":"2026-01-31T23:59:59-05:00",
                "tablas":["prestamos"],"formato":"sql"}
                """;

        mockMvc.perform(post("/api/v1/admin/backups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.formato").value("sql"));
    }

    @Test
    void generar_sinTablas_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/backups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"desde":"2026-01-01T00:00:00-05:00","hasta":"2026-01-31T23:59:59-05:00",
                                "tablas":[],"formato":"sql"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listar_sinFiltro_devuelve200() throws Exception {
        when(backupService.listarTodos()).thenReturn(List.of(backup()));

        mockMvc.perform(get("/api/v1/admin/backups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4));
    }

    @Test
    void listar_conRango_devuelve200() throws Exception {
        when(backupService.listarPorRango(any(), any())).thenReturn(List.of(backup()));

        mockMvc.perform(get("/api/v1/admin/backups")
                        .param("desde", "2026-01-01T00:00:00-05:00")
                        .param("hasta", "2026-01-31T23:59:59-05:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("COMPLETADO"));
    }

    @Test
    void listar_soloDesdeFormatoFlexible_devuelve200() throws Exception {
        when(backupService.listarPorRango(any(), any())).thenReturn(List.of(backup()));

        mockMvc.perform(get("/api/v1/admin/backups").param("desde", "2026-01-01T00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4));
    }

    @Test
    void listar_fechaInvalida_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/backups").param("desde", "no-es-fecha"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listarProgramaciones_devuelve200() throws Exception {
        when(progService.listarActivas()).thenReturn(List.of(programacionHoras()));

        mockMvc.perform(get("/api/v1/admin/backups/programacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(8));
    }

    @Test
    void crearProgramacion_activa_devuelve201() throws Exception {
        BackupProgramacion creada = programacionHoras();
        when(progService.crear(any())).thenReturn(creada);

        mockMvc.perform(post("/api/v1/admin/backups/programacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creada)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cadaHoras").value(6));

        verify(progService).programarEjecucion(8L);
    }

    @Test
    void crearProgramacion_inactiva_noPrograma_devuelve201() throws Exception {
        BackupProgramacion inactiva = programacionHoras();
        inactiva.setActivo(false);
        when(progService.crear(any())).thenReturn(inactiva);

        mockMvc.perform(post("/api/v1/admin/backups/programacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inactiva)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(false));

        verify(progService, never()).programarEjecucion(8L);
    }

    @Test
    void programar_devuelve200() throws Exception {
        when(progService.obtener(8L)).thenReturn(programacionHoras());

        mockMvc.perform(post("/api/v1/admin/backups/8/programar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Programación de respaldo activada exitosamente"));
    }

    @Test
    void borrar_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/backups/4"))
                .andExpect(status().isNoContent());
        verify(backupService).eliminar(4L);
    }

    @Test
    void borrarProgramacion_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/backups/programacion/8"))
                .andExpect(status().isNoContent());
        verify(progService).eliminar(8L);
    }

    @Test
    void descargar_existente_devuelve200() throws Exception {
        when(backupService.descargar(4L)).thenReturn(new byte[]{9, 8});

        mockMvc.perform(get("/api/v1/admin/backups/4/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("backup-4.zip")));
    }

    @Test
    void descargar_inexistente_devuelve404() throws Exception {
        when(backupService.descargar(99L)).thenThrow(new ResponseStatusException(NOT_FOUND, "Backup no encontrado 99"));

        mockMvc.perform(get("/api/v1/admin/backups/99/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ejecutarAhora_porHoras_devuelve200() throws Exception {
        when(progService.obtener(8L)).thenReturn(programacionHoras());
        when(backupService.generarBackup(any(), any(), any(), eq("sql"), eq("automatico"))).thenReturn(backup());

        mockMvc.perform(post("/api/v1/admin/backups/8/ejecutar-ahora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.programacionId").value(8));

        verify(progService).actualizarUltimaEjecucion(eq(8L), any());
    }

    @Test
    void ejecutarAhora_porDias_devuelve200() throws Exception {
        BackupProgramacion p = programacionHoras();
        p.setCadaHoras(null);
        p.setCadaDias(2);
        p.setTablas(Set.of());
        when(progService.obtener(8L)).thenReturn(p);
        when(backupService.generarBackup(any(), any(), any(), eq("sql"), eq("automatico"))).thenReturn(backup());

        mockMvc.perform(post("/api/v1/admin/backups/8/ejecutar-ahora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("El respaldo se ejecutó y guardó correctamente"));
    }
}
