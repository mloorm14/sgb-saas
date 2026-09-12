package com.uteq.backend.service;

import com.uteq.backend.entity.Backup;
import com.uteq.backend.entity.BackupSchedule;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.BackupScheduleRepository;
import com.uteq.backend.repository.UserRepository;
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
class BackupScheduleServiceTest {

    @Mock BackupScheduleRepository progRepo;
    @Mock UserRepository userRepo;
    @Mock TaskScheduler taskScheduler;
    @Mock BackupService backupService;

    @Test
    void create_withEveryTimesValid_asignaAuditYActive() {
        BackupScheduleService service = service();
        Authentication auth = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(auth);
        given(auth.getName()).willReturn("admin@correo.com");
        given(userRepo.findByEmail("admin@correo.com")).willReturn(Optional.of(user(8L)));
        given(progRepo.save(any(BackupSchedule.class))).willAnswer(inv -> inv.getArgument(0));

        BackupSchedule result = service.create(scheduleTimes(2));

        assertThat(result.getCreatedBy()).isEqualTo(8L);
        assertThat(result.getActive()).isTrue();
        assertThat(result.getCreated()).isNotNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_rechazaXorYRangesInvalids() {
        BackupScheduleService service = service();
        BackupSchedule withoutPeriod = new BackupSchedule();
        BackupSchedule ambos = BackupSchedule.builder().everyTimes(2).everyDays(1).build();
        BackupSchedule timesFuera = BackupSchedule.builder().everyTimes(24).build();
        BackupSchedule daysFuera = BackupSchedule.builder().everyDays(31).build();

        assertThatThrownBy(() -> service.create(withoutPeriod))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exactamente uno");
        assertThatThrownBy(() -> service.create(ambos))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exactamente uno");
        assertThatThrownBy(() -> service.create(timesFuera))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cada_horas");
        assertThatThrownBy(() -> service.create(daysFuera))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cada_dias");
    }

    @Test
    void getYUpdateLastExecution_requierenScheduleActive() {
        BackupScheduleService service = service();
        BackupSchedule active = scheduleTimes(3);
        active.setId(10L);
        active.setActive(true);
        given(progRepo.findById(10L)).willReturn(Optional.of(active));

        OffsetDateTime date = OffsetDateTime.now();
        assertThat(service.get(10L)).isSameAs(active);
        service.updateLastExecution(10L, date);

        assertThat(active.getLastExecution()).isEqualTo(date);
        verify(progRepo).save(active);
    }

    @Test
    void delete_markInactivaYCancelaSchedulerActive() {
        BackupScheduleService service = service();
        BackupSchedule active = scheduleTimes(1);
        active.setId(20L);
        active.setActive(true);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        given(progRepo.findById(20L)).willReturn(Optional.of(active));
        doReturn(future).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));

        service.scheduleExecution(20L);
        service.delete(20L);

        assertThat(active.getActive()).isFalse();
        verify(future).cancel(false);
        verify(progRepo, org.mockito.Mockito.times(2)).save(active);
    }

    @Test
    void scheduleExecution_withEveryDaysProgramaPeriodDiarioYGuardaLastExecution() {
        BackupScheduleService service = service();
        BackupSchedule active = BackupSchedule.builder()
                .id(30L)
                .everyDays(2)
                .format("sql")
                .active(true)
                .build();
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        given(progRepo.findById(30L)).willReturn(Optional.of(active));
        doReturn(future).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), eq(Duration.ofSeconds(172800)));

        ScheduledFuture<?> result = service.scheduleExecution(30L);

        assertThat(result == future).isTrue();
        assertThat(active.getLastExecution()).isNotNull();
        verify(progRepo).save(active);
    }

    @Test
    void initializeTasksProgramadas_reprogramaActivesYContinuaSiUnaFalla() {
        BackupScheduleService service = service();
        BackupSchedule invalida = scheduleTimes(1);
        invalida.setId(41L);
        invalida.setActive(true);
        BackupSchedule active = scheduleTimes(2);
        active.setId(42L);
        active.setActive(true);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        given(progRepo.findByActiveTrueOrderByLastExecutionDesc()).willReturn(List.of(invalida, active));
        given(progRepo.findById(41L)).willReturn(Optional.empty());
        given(progRepo.findById(42L)).willReturn(Optional.of(active));
        doReturn(future).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));

        service.initializeTasksProgramadas();

        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), any(Instant.class), eq(Duration.ofSeconds(7200)));
    }

    @Test
    void runnableProgramado_ejecutaBackupYActualizaLastExecution() {
        BackupScheduleService service = service();
        BackupSchedule active = scheduleTimes(4);
        active.setId(50L);
        active.setActive(true);
        active.setTables(Set.of("prestamos"));
        Backup backup = Backup.builder().created(OffsetDateTime.now()).build();
        given(progRepo.findById(50L)).willReturn(Optional.of(active));
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));
        given(backupService.generateBackup(any(OffsetDateTime.class), any(OffsetDateTime.class),
                eq(Set.of("prestamos")), eq("csv"), eq("automatico"))).willReturn(backup);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        service.scheduleExecution(50L);
        verify(taskScheduler).scheduleAtFixedRate(runnableCaptor.capture(), any(Instant.class), eq(Duration.ofSeconds(14400)));
        runnableCaptor.getValue().run();

        assertThat(active.getLastExecution()).isEqualTo(backup.getCreated());
        verify(progRepo, org.mockito.Mockito.atLeastOnce()).save(active);
    }

    @Test
    void scheduleExecution_cuandoNotExiste_lanza404() {
        BackupScheduleService service = service();
        given(progRepo.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.scheduleExecution(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Programación no encontrada");
    }

    private BackupScheduleService service() {
        return new BackupScheduleService(progRepo, userRepo, taskScheduler, backupService);
    }

    private BackupSchedule scheduleTimes(int times) {
        return BackupSchedule.builder()
                .everyTimes(times)
                .format("csv")
                .active(true)
                .build();
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
