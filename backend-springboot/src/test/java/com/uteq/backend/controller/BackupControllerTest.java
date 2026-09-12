package com.uteq.backend.controller;

import com.uteq.backend.entity.Backup;
import com.uteq.backend.entity.BackupSchedule;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.BackupScheduleService;
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
    private BackupScheduleService progService;

    private Backup backup() {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        return Backup.builder()
                .id(4L)
                .createdBy(1L)
                .from(ahora.minusDays(1))
                .until(ahora)
                .tables(Set.of("prestamos"))
                .format("sql")
                .path("/tmp/backup.zip")
                .sizeBytes(99L)
                .status("COMPLETADO")
                .type("manual")
                .created(ahora)
                .build();
    }

    private BackupSchedule scheduleTimes() {
        BackupSchedule p = new BackupSchedule();
        p.setId(8L);
        p.setCreatedBy(1L);
        p.setEveryTimes(6);
        p.setFormat("sql");
        p.setActive(true);
        p.setTables(Set.of("prestamos"));
        return p;
    }

    @Test
    void generate_dataValids_devuelve201() throws Exception {
        when(backupService.generateBackup(any(), any(), any(), eq("sql"), eq("manual"))).thenReturn(backup());

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
    void generate_withoutTables_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/backups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"desde":"2026-01-01T00:00:00-05:00","hasta":"2026-01-31T23:59:59-05:00",
                                "tablas":[],"formato":"sql"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_withoutFilter_devuelve200() throws Exception {
        when(backupService.listAll()).thenReturn(List.of(backup()));

        mockMvc.perform(get("/api/v1/admin/backups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4));
    }

    @Test
    void list_withRange_devuelve200() throws Exception {
        when(backupService.listByRange(any(), any())).thenReturn(List.of(backup()));

        mockMvc.perform(get("/api/v1/admin/backups")
                        .param("desde", "2026-01-01T00:00:00-05:00")
                        .param("hasta", "2026-01-31T23:59:59-05:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("COMPLETADO"));
    }

    @Test
    void list_soloFromFormatFlexible_devuelve200() throws Exception {
        when(backupService.listByRange(any(), any())).thenReturn(List.of(backup()));

        mockMvc.perform(get("/api/v1/admin/backups").param("desde", "2026-01-01T00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4));
    }

    @Test
    void list_dateInvalida_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/backups").param("desde", "no-es-fecha"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSchedules_devuelve200() throws Exception {
        when(progService.listActives()).thenReturn(List.of(scheduleTimes()));

        mockMvc.perform(get("/api/v1/admin/backups/programacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(8));
    }

    @Test
    void createSchedule_active_devuelve201() throws Exception {
        BackupSchedule created = scheduleTimes();
        when(progService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/admin/backups/programacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(created)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cadaHoras").value(6));

        verify(progService).scheduleExecution(8L);
    }

    @Test
    void createSchedule_inactiva_notPrograma_devuelve201() throws Exception {
        BackupSchedule inactiva = scheduleTimes();
        inactiva.setActive(false);
        when(progService.create(any())).thenReturn(inactiva);

        mockMvc.perform(post("/api/v1/admin/backups/programacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inactiva)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(false));

        verify(progService, never()).scheduleExecution(8L);
    }

    @Test
    void schedule_devuelve200() throws Exception {
        when(progService.get(8L)).thenReturn(scheduleTimes());

        mockMvc.perform(post("/api/v1/admin/backups/8/programar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Programación de respaldo activada exitosamente"));
    }

    @Test
    void borrar_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/backups/4"))
                .andExpect(status().isNoContent());
        verify(backupService).delete(4L);
    }

    @Test
    void borrarSchedule_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/backups/programacion/8"))
                .andExpect(status().isNoContent());
        verify(progService).delete(8L);
    }

    @Test
    void download_existing_devuelve200() throws Exception {
        when(backupService.download(4L)).thenReturn(new byte[]{9, 8});

        mockMvc.perform(get("/api/v1/admin/backups/4/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("backup-4.zip")));
    }

    @Test
    void download_inexistente_devuelve404() throws Exception {
        when(backupService.download(99L)).thenThrow(new ResponseStatusException(NOT_FOUND, "Backup no encontrado 99"));

        mockMvc.perform(get("/api/v1/admin/backups/99/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    void executeAhora_byTimes_devuelve200() throws Exception {
        when(progService.get(8L)).thenReturn(scheduleTimes());
        when(backupService.generateBackup(any(), any(), any(), eq("sql"), eq("automatico"))).thenReturn(backup());

        mockMvc.perform(post("/api/v1/admin/backups/8/ejecutar-ahora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.programacionId").value(8));

        verify(progService).updateLastExecution(eq(8L), any());
    }

    @Test
    void executeAhora_byDays_devuelve200() throws Exception {
        BackupSchedule p = scheduleTimes();
        p.setEveryTimes(null);
        p.setEveryDays(2);
        p.setTables(Set.of());
        when(progService.get(8L)).thenReturn(p);
        when(backupService.generateBackup(any(), any(), any(), eq("sql"), eq("automatico"))).thenReturn(backup());

        mockMvc.perform(post("/api/v1/admin/backups/8/ejecutar-ahora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("El respaldo se ejecutó y guardó correctamente"));
    }
}
