package com.uteq.backend.service;

import com.uteq.backend.entity.ConfiguracionRespaldo;
import com.uteq.backend.entity.RegistroRespaldo;
import com.uteq.backend.repository.ConfiguracionRespaldoRepository;
import com.uteq.backend.repository.RegistroRespaldoRepository;
import com.uteq.backend.repository.UsuarioRepository;
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
class RespaldoCompletoServiceTest {

    @Mock ConfiguracionRespaldoRepository configRepo;
    @Mock RegistroRespaldoRepository registroRepo;
    @Mock UsuarioRepository usuarioRepository;
    @Mock BackupStorageService storageService;

    @Test
    void obtenerConfiguracion_cuandoNoExiste_creaValoresPorDefecto() {
        RespaldoCompletoService service = service();
        given(configRepo.findAll()).willReturn(List.of());
        given(configRepo.save(any(ConfiguracionRespaldo.class))).willAnswer(inv -> inv.getArgument(0));

        ConfiguracionRespaldo config = service.obtenerConfiguracion();

        assertThat(config.getHabilitado()).isFalse();
        assertThat(config.getFrecuenciaHoras()).isEqualTo(6);
        assertThat(config.getDiasRetencion()).isEqualTo(14);
    }

    @Test
    void actualizarConfiguracion_validaRangosYProgramaProximaEjecucion() {
        RespaldoCompletoService service = service();
        ConfiguracionRespaldo existente = ConfiguracionRespaldo.builder()
                .habilitado(false)
                .frecuenciaHoras(6)
                .diasRetencion(14)
                .build();
        given(configRepo.findAll()).willReturn(List.of(existente));
        given(configRepo.save(any(ConfiguracionRespaldo.class))).willAnswer(inv -> inv.getArgument(0));

        ConfiguracionRespaldo actualizado = service.actualizarConfiguracion(12, 30, true);

        assertThat(actualizado.getFrecuenciaHoras()).isEqualTo(12);
        assertThat(actualizado.getDiasRetencion()).isEqualTo(30);
        assertThat(actualizado.getHabilitado()).isTrue();
        assertThat(actualizado.getProximaEjecucion()).isNotNull();
    }

    @Test
    void actualizarConfiguracion_rechazaRangosInvalidos() {
        RespaldoCompletoService service = service();

        assertThatThrownBy(() -> service.actualizarConfiguracion(0, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("frecuenciaHoras");
        assertThatThrownBy(() -> service.actualizarConfiguracion(null, 91, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("diasRetencion");
    }

    @Test
    void eliminar_conRutaS3_extraeKeyYBorraRegistro() {
        RespaldoCompletoService service = service();
        RegistroRespaldo registro = new RegistroRespaldo();
        registro.setId(5L);
        registro.setRutaR2("s3://bucket/backups/full.zip");
        given(registroRepo.findById(5L)).willReturn(Optional.of(registro));

        service.eliminar(5L);

        verify(storageService).delete("backups/full.zip");
        verify(registroRepo).delete(registro);
    }

    @Test
    void descargar_sinArchivoAsociado_lanzaBadRequest() {
        RespaldoCompletoService service = service();
        RegistroRespaldo registro = new RegistroRespaldo();
        registro.setRutaR2(" ");
        given(registroRepo.findById(6L)).willReturn(Optional.of(registro));

        assertThatThrownBy(() -> service.descargar(6L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No hay archivo asociado");
    }

    @Test
    void registrarInicioYResultadoExitoso_actualizaConfiguracion() {
        RespaldoCompletoService service = service();
        RegistroRespaldo registro = new RegistroRespaldo();
        registro.setId(9L);
        ConfiguracionRespaldo config = ConfiguracionRespaldo.builder()
                .frecuenciaHoras(4)
                .diasRetencion(14)
                .build();
        given(registroRepo.save(any(RegistroRespaldo.class))).willAnswer(inv -> inv.getArgument(0));
        given(registroRepo.findById(9L)).willReturn(Optional.of(registro));
        given(configRepo.findAll()).willReturn(List.of(config));

        RegistroRespaldo inicio = service.registrarInicio("completo", 3L);
        RegistroRespaldo resultado = service.registrarResultado(9L, "exitoso", "full.zip", 100L, "local/full.zip", null);

        assertThat(inicio.getEstado()).isEqualTo("ejecutando");
        assertThat(resultado.getEstado()).isEqualTo("exitoso");
        assertThat(resultado.getFinalizadoEn()).isNotNull();
        assertThat(config.getUltimaEjecucion()).isEqualTo(resultado.getFinalizadoEn());
        assertThat(config.getProximaEjecucion()).isEqualTo(resultado.getFinalizadoEn().plusHours(4));
        verify(configRepo).save(config);
    }

    @Test
    void listarPorTipoYListarTodos_deleganEnRepositorio() {
        RespaldoCompletoService service = service();
        RegistroRespaldo manual = new RegistroRespaldo();
        given(registroRepo.findByTipoOrderByIniciadoEnDesc("manual")).willReturn(List.of(manual));
        given(registroRepo.findAll()).willReturn(List.of(manual));

        assertThat(service.listarPorTipo("manual")).containsExactly(manual);
        assertThat(service.listarTodos()).containsExactly(manual);
    }

    @Test
    void registrarResultado_cuandoNoExiste_lanza404() {
        RespaldoCompletoService service = service();
        given(registroRepo.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarResultado(404L, "fallido", null, null, null, "error"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Registro no encontrado");
    }

    private RespaldoCompletoService service() {
        return new RespaldoCompletoService(configRepo, registroRepo, usuarioRepository, storageService);
    }
}
