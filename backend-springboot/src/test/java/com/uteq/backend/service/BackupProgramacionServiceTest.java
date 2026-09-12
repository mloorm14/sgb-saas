package com.uteq.backend.service;

import com.uteq.backend.entity.Backup;
import com.uteq.backend.entity.BackupProgramacion;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BackupProgramacionRepository;
import com.uteq.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BackupProgramacionServiceTest {

    @Mock BackupProgramacionRepository progRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock TaskScheduler taskScheduler;
    @Mock BackupService backupService;

    @Test
    void crear_conCadaHorasValido_asignaAuditoriaYActivo() {
        BackupProgramacionService service = service();
        Authentication auth = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(auth);
        given(auth.getName()).willReturn("admin@correo.com");
        given(usuarioRepo.findByCorreo("admin@correo.com")).willReturn(Optional.of(usuario(8L)));
        given(progRepo.save(any(BackupProgramacion.class))).willAnswer(inv -> inv.getArgument(0));

        BackupProgramacion resultado = service.crear(programacionHoras(2));

        assertThat(resultado.getCreadoPor()).isEqualTo(8L);
        assertThat(resultado.getActivo()).isTrue();
        assertThat(resultado.getCreadoEn()).isNotNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void crear_rechazaXorYRangosInvalidos() {
        BackupProgramacionService service = service();
        BackupProgramacion sinPeriodo = new BackupProgramacion();
        BackupProgramacion ambos = BackupProgramacion.builder().cadaHoras(2).cadaDias(1).build();
        BackupProgramacion horasFuera = BackupProgramacion.builder().cadaHoras(24).build();
        BackupProgramacion diasFuera = BackupProgramacion.builder().cadaDias(31).build();

        assertThatThrownBy(() -> service.crear(sinPeriodo))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exactamente uno");
        assertThatThrownBy(() -> service.crear(ambos))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exactamente uno");
        assertThatThrownBy(() -> service.crear(horasFuera))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cada_horas");
        assertThatThrownBy(() -> service.crear(diasFuera))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cada_dias");
    }

    @Test
    void obtenerYActualizarUltimaEjecucion_requierenProgramacionActiva() {
        BackupProgramacionService service = service();
        BackupProgramacion activa = programacionHoras(3);
        activa.setId(10L);
        activa.setActivo(true);
        given(progRepo.findById(10L)).willReturn(Optional.of(activa));

        OffsetDateTime fecha = OffsetDateTime.now();
        assertThat(service.obtener(10L)).isSameAs(activa);
        service.actualizarUltimaEjecucion(10L, fecha);

        assertThat(activa.getUltimaEjecucion()).isEqualTo(fecha);
        verify(progRepo).save(activa);
    }

    @Test
    void eliminar_marcaInactivaYCancelaSchedulerActivo() {
        BackupProgramacionService service = service();
        BackupProgramacion activa = programacionHoras(1);
        activa.setId(20L);
        activa.setActivo(true);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        given(progRepo.findById(20L)).willReturn(Optional.of(activa));
        doReturn(future).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));

        service.programarEjecucion(20L);
        service.eliminar(20L);

        assertThat(activa.getActivo()).isFalse();
        verify(future).cancel(false);
        verify(progRepo, org.mockito.Mockito.times(2)).save(activa);
    }

    @Test
    void programarEjecucion_conCadaDiasProgramaPeriodoDiarioYGuardaUltimaEjecucion() {
        BackupProgramacionService service = service();
        BackupProgramacion activa = BackupProgramacion.builder()
                .id(30L)
                .cadaDias(2)
                .formato("sql")
                .activo(true)
                .build();
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        given(progRepo.findById(30L)).willReturn(Optional.of(activa));
        doReturn(future).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), eq(Duration.ofSeconds(172800)));

        ScheduledFuture<?> resultado = service.programarEjecucion(30L);

        assertThat(resultado == future).isTrue();
        assertThat(activa.getUltimaEjecucion()).isNotNull();
        verify(progRepo).save(activa);
    }

    @Test
    void inicializarTareasProgramadas_reprogramaActivasYContinuaSiUnaFalla() {
        BackupProgramacionService service = service();
        BackupProgramacion invalida = programacionHoras(1);
        invalida.setId(41L);
        invalida.setActivo(true);
        BackupProgramacion activa = programacionHoras(2);
        activa.setId(42L);
        activa.setActivo(true);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        given(progRepo.findByActivoTrueOrderByUltimaEjecucionDesc()).willReturn(List.of(invalida, activa));
        given(progRepo.findById(41L)).willReturn(Optional.empty());
        given(progRepo.findById(42L)).willReturn(Optional.of(activa));
        doReturn(future).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));

        service.inicializarTareasProgramadas();

        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), any(Instant.class), eq(Duration.ofSeconds(7200)));
    }

    @Test
    void runnableProgramado_ejecutaBackupYActualizaUltimaEjecucion() {
        BackupProgramacionService service = service();
        BackupProgramacion activa = programacionHoras(4);
        activa.setId(50L);
        activa.setActivo(true);
        activa.setTablas(Set.of("prestamos"));
        Backup backup = Backup.builder().creadoEn(OffsetDateTime.now()).build();
        given(progRepo.findById(50L)).willReturn(Optional.of(activa));
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));
        given(backupService.generarBackup(any(OffsetDateTime.class), any(OffsetDateTime.class),
                eq(Set.of("prestamos")), eq("csv"), eq("automatico"))).willReturn(backup);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        service.programarEjecucion(50L);
        verify(taskScheduler).scheduleAtFixedRate(runnableCaptor.capture(), any(Instant.class), eq(Duration.ofSeconds(14400)));
        runnableCaptor.getValue().run();

        assertThat(activa.getUltimaEjecucion()).isEqualTo(backup.getCreadoEn());
        verify(progRepo, org.mockito.Mockito.atLeastOnce()).save(activa);
    }

    @Test
    void programarEjecucion_cuandoNoExiste_lanza404() {
        BackupProgramacionService service = service();
        given(progRepo.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.programarEjecucion(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Programación no encontrada");
    }

    private BackupProgramacionService service() {
        return new BackupProgramacionService(progRepo, usuarioRepo, taskScheduler, backupService);
    }

    private BackupProgramacion programacionHoras(int horas) {
        return BackupProgramacion.builder()
                .cadaHoras(horas)
                .formato("csv")
                .activo(true)
                .build();
    }

    private Usuario usuario(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }
}
