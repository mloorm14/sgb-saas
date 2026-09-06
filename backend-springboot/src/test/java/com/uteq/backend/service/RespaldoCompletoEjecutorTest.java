package com.uteq.backend.service;

import com.uteq.backend.entity.ConfiguracionRespaldo;
import com.uteq.backend.entity.RegistroRespaldo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RespaldoCompletoEjecutorTest {

    @Mock
    private RespaldoCompletoService respaldoService;

    @Mock
    private BackupStorageService storageService;

    @Mock
    private PgDumpRunner pgDumpRunner;

    @Mock
    private TaskScheduler taskScheduler;

    private RespaldoCompletoEjecutor ejecutor;

    @BeforeEach
    void setUp() {
        ejecutor = new RespaldoCompletoEjecutor(respaldoService, storageService, pgDumpRunner, taskScheduler);
        ReflectionTestUtils.setField(ejecutor, "dbUrl", "jdbc:postgresql://localhost:5432/sgb_db");
        ReflectionTestUtils.setField(ejecutor, "dbUser", "sgb_user");
        ReflectionTestUtils.setField(ejecutor, "dbPassword", "changeme");
        ReflectionTestUtils.setField(ejecutor, "maxMinutos", 20L);
    }

    private RegistroRespaldo registroConId(Long id) {
        return RegistroRespaldo.builder().id(id).tipo("manual").estado("ejecutando").build();
    }

    private Runnable dispararYCapturar() {
        ejecutor.dispararManual();
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(captor.capture(), any(java.time.Instant.class));
        return captor.getValue();
    }

    @Test
    void dispararManual_exito_registraExitosoYSubeArchivo() throws Exception {
        when(respaldoService.registrarInicio(eq("manual"), any())).thenReturn(registroConId(7L));
        doAnswer(inv -> {
            Files.write((Path) inv.getArgument(0), "dump".getBytes());
            return null;
        }).when(pgDumpRunner).dump(any(Path.class), anyString(), anyString(), anyString());
        when(storageService.rutaPara(anyString())).thenAnswer(inv -> "s3://bucket/" + inv.getArgument(0));

        dispararYCapturar().run();

        verify(storageService).upload(anyString(), org.mockito.ArgumentMatchers.argThat(
                (byte[] b) -> java.util.Arrays.equals(b, "dump".getBytes())));
        verify(respaldoService).registrarResultado(eq(7L), eq("exitoso"), anyString(), eq(4L),
                org.mockito.ArgumentMatchers.startsWith("s3://bucket/backup_completo_"), any());
    }

    @Test
    void dispararManual_falloPgDump_registraFallido() throws Exception {
        when(respaldoService.registrarInicio(eq("manual"), any())).thenReturn(registroConId(8L));
        doThrow(new IllegalStateException("pg_dump salió con código 1")).when(pgDumpRunner)
                .dump(any(Path.class), anyString(), anyString(), anyString());

        dispararYCapturar().run();

        verify(storageService, never()).upload(anyString(), any());
        verify(respaldoService).registrarResultado(eq(8L), eq("fallido"), any(), any(), any(),
                org.mockito.ArgumentMatchers.contains("pg_dump salió con código 1"));
    }

    @Test
    void dispararManual_conRespaldoEnCurso_lanzaExcepcion() {
        ejecutor.dispararManual();

        assertThrows(RespaldoEnCursoException.class, () -> ejecutor.dispararManual());
    }

    @Test
    void ejecutarAutomatico_deshabilitado_noHaceNada() {
        ConfiguracionRespaldo config = ConfiguracionRespaldo.builder().habilitado(false).frecuenciaHoras(6).build();
        when(respaldoService.obtenerConfiguracion()).thenReturn(config);

        ejecutor.ejecutarAutomatico();

        verify(respaldoService, never()).registrarInicio(anyString(), any());
    }

    @Test
    void ejecutarAutomatico_proximaRancia_recalculaYSalta() {
        ConfiguracionRespaldo config = ConfiguracionRespaldo.builder()
                .habilitado(true).frecuenciaHoras(6)
                .proximaEjecucion(OffsetDateTime.now().minusDays(3))
                .build();
        when(respaldoService.obtenerConfiguracion()).thenReturn(config);

        ejecutor.ejecutarAutomatico();

        verify(respaldoService).actualizarConfiguracion(eq(6), any(), eq(true));
        verify(respaldoService, never()).registrarInicio(anyString(), any());
    }

    @Test
    void ejecutarAutomatico_proximaVencidaReciente_ejecuta() {
        ConfiguracionRespaldo config = ConfiguracionRespaldo.builder()
                .habilitado(true).frecuenciaHoras(6).actualizadoPor(9L)
                .proximaEjecucion(OffsetDateTime.now().minusMinutes(5))
                .build();
        when(respaldoService.obtenerConfiguracion()).thenReturn(config);
        when(respaldoService.registrarInicio(eq("automatico"), eq(9L))).thenReturn(registroConId(10L));

        ejecutor.ejecutarAutomatico();

        verify(respaldoService).registrarInicio(eq("automatico"), eq(9L));
        verify(respaldoService).registrarResultado(eq(10L), anyString(), any(), any(), any(), any());
    }
}
