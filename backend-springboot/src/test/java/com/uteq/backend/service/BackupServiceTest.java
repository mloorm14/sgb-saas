package com.uteq.backend.service;

import com.uteq.backend.entity.Backup;
import com.uteq.backend.repository.BackupRepository;
import com.uteq.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock BackupRepository backupRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock BackupStorageService storageService;

    @Test
    void generarBackup_csvFiltraTablaConFechaYGuardaMetadata() throws Exception {
        BackupService service = service();
        OffsetDateTime desde = OffsetDateTime.now().minusDays(1);
        OffsetDateTime hasta = OffsetDateTime.now();
        given(storageService.isEncryptionEnabled()).willReturn(false);
        java.util.Map<String, Object> fila = new java.util.LinkedHashMap<>();
        fila.put("id", 1L);
        fila.put("detalle", "texto, con coma");
        doReturn(List.of(fila))
                .when(jdbcTemplate).queryForList(anyString(), any(OffsetDateTime.class), any(OffsetDateTime.class));
        given(backupRepository.save(any(Backup.class))).willAnswer(inv -> {
            Backup backup = inv.getArgument(0);
            backup.setId(10L);
            return backup;
        });

        Backup resultado = service.generarBackup(desde, hasta, Set.of("prestamos"), "csv", "manual");

        ArgumentCaptor<byte[]> zipCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(storageService).upload(org.mockito.ArgumentMatchers.startsWith("backups/backup_"), zipCaptor.capture());
        assertThat(resultado.getFormato()).isEqualTo("csv");
        assertThat(resultado.getEstado()).isEqualTo("COMPLETADO");
        assertThat(resultado.getTamanoBytes()).isEqualTo((long) zipCaptor.getValue().length);
        assertThat(contenidoZip(zipCaptor.getValue(), "prestamos.csv"))
                .contains("id,detalle")
                .contains("\"texto, con coma\"");
    }

    @Test
    void generarBackup_sqlTablaSinFechaHaceVolcadoCompleto() throws Exception {
        BackupService service = service();
        given(storageService.isEncryptionEnabled()).willReturn(true);
        given(jdbcTemplate.queryForList("SELECT * FROM categorias"))
                .willReturn(List.of(Map.of("id", 2L, "nombre", "Infantil")));
        given(backupRepository.save(any(Backup.class))).willAnswer(inv -> inv.getArgument(0));

        Backup resultado = service.generarBackup(
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now(),
                Set.of("categorias"),
                null,
                "manual");

        ArgumentCaptor<byte[]> zipCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(storageService).upload(org.mockito.ArgumentMatchers.endsWith(".zip.enc"), zipCaptor.capture());
        assertThat(resultado.getFormato()).isEqualTo("sql");
        assertThat(contenidoZip(zipCaptor.getValue(), "categorias.sql"))
                .contains("INSERT INTO categorias")
                .contains("'Infantil'");
    }

    @Test
    void generarBackup_rechazaRangoTablaYFormatoInvalidos() {
        BackupService service = service();
        OffsetDateTime ahora = OffsetDateTime.now();

        assertThatThrownBy(() -> service.generarBackup(null, ahora, Set.of("prestamos"), "csv", "manual"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.generarBackup(ahora, ahora.minusDays(1), Set.of("prestamos"), "csv", "manual"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.generarBackup(ahora.minusDays(31), ahora, Set.of("prestamos"), "csv", "manual"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Rango max 30 dias");
        assertThatThrownBy(() -> service.generarBackup(ahora.minusDays(1), ahora, Set.of("tabla_rara"), "csv", "manual"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Tabla no permitida");
        assertThatThrownBy(() -> service.generarBackup(ahora.minusDays(1), ahora, Set.of("prestamos"), "json", "manual"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("formato debe ser sql o csv");
    }

    @Test
    void generarBackup_errorDeJdbcDevuelveBadRequestLegible() {
        BackupService service = service();
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("columna faltante"))
                .when(jdbcTemplate).queryForList(anyString(), any(OffsetDateTime.class), any(OffsetDateTime.class));

        assertThatThrownBy(() -> service.generarBackup(
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now(),
                Set.of("prestamos"),
                "csv",
                "manual"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void listarObtenerDescargarYEliminar_deleganEnRepositorioYStorage() {
        BackupService service = service();
        Backup backup = Backup.builder().id(7L).ruta("backups/x.zip").build();
        given(backupRepository.findById(7L)).willReturn(Optional.of(backup));
        given(storageService.download("backups/x.zip")).willReturn("zip".getBytes());

        assertThat(service.descargar(7L)).isEqualTo("zip".getBytes());
        service.eliminar(7L);

        verify(storageService).delete("backups/x.zip");
        verify(backupRepository).delete(backup);
    }

    @Test
    void obtener_cuandoNoExiste_lanza404() {
        BackupService service = service();
        given(backupRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerPorId(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Backup no encontrado");
    }

    private BackupService service() {
        return new BackupService(backupRepository, usuarioRepository, jdbcTemplate, storageService);
    }

    private String contenidoZip(byte[] zipBytes, String nombreEntrada) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(nombreEntrada)) {
                    return new String(zip.readAllBytes());
                }
            }
        }
        throw new AssertionError("Entrada no encontrada: " + nombreEntrada);
    }
}
