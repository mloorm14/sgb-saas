package com.uteq.backend.service;

import com.uteq.backend.entity.ConfigurationBackup;
import com.uteq.backend.entity.RegistrationBackup;
import com.uteq.backend.repository.ConfigurationBackupRepository;
import com.uteq.backend.repository.RegistrationBackupRepository;
import com.uteq.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BackupFullServiceTest {

    @Mock ConfigurationBackupRepository configRepo;
    @Mock RegistrationBackupRepository registrationRepo;
    @Mock UserRepository userRepository;
    @Mock BackupStorageService storageService;

    @Test
    void getConfiguration_cuandoNotExiste_creaValuesByDefecto() {
        FullBackupService service = service();
        given(configRepo.findAll()).willReturn(List.of());
        given(configRepo.save(any(ConfigurationBackup.class))).willAnswer(inv -> inv.getArgument(0));

        ConfigurationBackup config = service.getConfiguration();

        assertThat(config.getEnabled()).isFalse();
        assertThat(config.getFrequencyTimes()).isEqualTo(6);
        assertThat(config.getDaysRetention()).isEqualTo(14);
    }

    @Test
    void updateConfiguration_validaRangesYProgramaNextExecution() {
        FullBackupService service = service();
        ConfigurationBackup existing = ConfigurationBackup.builder()
                .enabled(false)
                .frequencyTimes(6)
                .daysRetention(14)
                .build();
        given(configRepo.findAll()).willReturn(List.of(existing));
        given(configRepo.save(any(ConfigurationBackup.class))).willAnswer(inv -> inv.getArgument(0));

        ConfigurationBackup updated = service.updateConfiguration(12, 30, true);

        assertThat(updated.getFrequencyTimes()).isEqualTo(12);
        assertThat(updated.getDaysRetention()).isEqualTo(30);
        assertThat(updated.getEnabled()).isTrue();
        assertThat(updated.getNextExecution()).isNotNull();
    }

    @Test
    void updateConfiguration_rechazaRangesInvalids() {
        FullBackupService service = service();

        assertThatThrownBy(() -> service.updateConfiguration(0, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("frecuenciaHoras");
        assertThatThrownBy(() -> service.updateConfiguration(null, 91, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("diasRetencion");
    }

    @Test
    void delete_withPathS3_extraeKeyYBorraRegistration() {
        FullBackupService service = service();
        RegistrationBackup registration = new RegistrationBackup();
        registration.setId(5L);
        registration.setPathR2("s3://bucket/backups/full.zip");
        given(registrationRepo.findById(5L)).willReturn(Optional.of(registration));

        service.delete(5L);

        verify(storageService).delete("backups/full.zip");
        verify(registrationRepo).delete(registration);
    }

    @Test
    void download_withoutFileAsociado_lanzaBadRequest() {
        FullBackupService service = service();
        RegistrationBackup registration = new RegistrationBackup();
        registration.setPathR2(" ");
        given(registrationRepo.findById(6L)).willReturn(Optional.of(registration));

        assertThatThrownBy(() -> service.download(6L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No hay archivo asociado");
    }

    @Test
    void registerStartYResultSuccessful_actualizaConfiguration() {
        FullBackupService service = service();
        RegistrationBackup registration = new RegistrationBackup();
        registration.setId(9L);
        ConfigurationBackup config = ConfigurationBackup.builder()
                .frequencyTimes(4)
                .daysRetention(14)
                .build();
        given(registrationRepo.save(any(RegistrationBackup.class))).willAnswer(inv -> inv.getArgument(0));
        given(registrationRepo.findById(9L)).willReturn(Optional.of(registration));
        given(configRepo.findAll()).willReturn(List.of(config));

        RegistrationBackup start = service.registerStart("completo", 3L);
        RegistrationBackup result = service.registerResult(9L, "exitoso", "full.zip", 100L, "local/full.zip", null);

        assertThat(start.getStatus()).isEqualTo("ejecutando");
        assertThat(result.getStatus()).isEqualTo("exitoso");
        assertThat(result.getFinished()).isNotNull();
        assertThat(config.getLastExecution()).isEqualTo(result.getFinished());
        assertThat(config.getNextExecution()).isEqualTo(result.getFinished().plusHours(4));
        verify(configRepo).save(config);
    }

    @Test
    void listByTypeYListTodos_deleganRepository() {
        FullBackupService service = service();
        RegistrationBackup manual = new RegistrationBackup();
        given(registrationRepo.findByTypeOrderByStartedDesc("manual")).willReturn(List.of(manual));
        given(registrationRepo.findAll()).willReturn(List.of(manual));

        assertThat(service.listByType("manual")).containsExactly(manual);
        assertThat(service.listAll()).containsExactly(manual);
    }

    @Test
    void registerResult_cuandoNotExiste_lanza404() {
        FullBackupService service = service();
        given(registrationRepo.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerResult(404L, "fallido", null, null, null, "error"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Registro no encontrado");
    }

    private FullBackupService service() {
        return new FullBackupService(configRepo, registrationRepo, userRepository, storageService);
    }
}
